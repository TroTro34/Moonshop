package com.monshop.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Récupère automatiquement une jaquette (image) et une description pour un jeu
 * quand le catalogue ne les fournit pas déjà, via l'API IGDB (propriété Twitch/Amazon).
 *
 * Mise en route (rapide, 100% self-service, pas d'attente humaine) :
 * 1. Crée/utilise un compte Twitch, puis va sur https://dev.twitch.tv/console/apps
 * 2. Clique "Register Your Application" : nom quelconque, OAuth Redirect URL =
 *    http://localhost (pas utilisée en pratique), catégorie = "Application Integration"
 * 3. Une fois créée, récupère le "Client ID", puis clique "New Secret" pour
 *    générer le "Client Secret"
 * 4. Colle les deux valeurs ci-dessous
 *
 * Sans ces identifiants, les requêtes échouent silencieusement et l'appli
 * retombe sur l'icône par défaut — aucun crash.
 *
 * IGDB s'authentifie via un jeton OAuth Twitch de courte durée : ce service
 * l'obtient et le renouvelle automatiquement, aucune action manuelle après
 * la configuration initiale.
 */
object IGDBMetadataService {

    // Aucun identifiant n'est livré avec l'appli : une constante écrite ici serait
    // lisible par quiconque ouvre l'APK, donc consommable et révocable par un tiers.
    // Chacun apporte les siens, demandés par l'assistant de première ouverture.
    private var identifiant: String = ""
    private var secret: String = ""

    /** Vrai quand l'utilisateur a fourni ses identifiants. */
    val configure: Boolean get() = identifiant.isNotBlank() && secret.isNotBlank()

    /**
     * Remplace les identifiants livrés par ceux de l'utilisateur.
     *
     * Le jeton en cours a été délivré à l'ancien couple : le garder ferait échouer la
     * requête suivante avec une erreur d'authentification incompréhensible.
     */
    fun appliquerIdentifiants(id: String, secretApi: String) {
        identifiant = id.trim()
        secret = secretApi.trim()
        jeton = null
        jetonExpireA = 0L
        cache.clear()
    }

    private const val URL_JETON = "https://id.twitch.tv/oauth2/token"
    private const val URL_JEUX = "https://api.igdb.com/v4/games"
    private const val LONGUEUR_MAX_DESCRIPTION = 400

    /**
     * Ce qu'IGDB sait d'un jeu et qui mérite d'être montré.
     *
     * Le nombre de ventes ne s'y trouve pas : IGDB est une base éditoriale, pas
     * commerciale. La note et le nombre d'avis en sont l'équivalent le plus proche.
     */
    data class Metadonnees(
        val image: String?,
        val description: String?,
        val annee: Int? = null,
        val genres: List<String> = emptyList(),
        val note: Int? = null,
        val nombreAvis: Int? = null,
        val studio: String? = null,
        val editeur: String? = null,
        val plateformes: List<String> = emptyList()
    )

    // Cache mémoire (process de l'appli) : une recherche par titre nettoyé, jamais deux fois.
    private val cache = mutableMapOf<String, Metadonnees?>()

    // Jeton OAuth mis en cache jusqu'à expiration (avec marge de sécurité)
    private var jeton: String? = null
    private var jetonExpireA: Long = 0L

    /** Nettoie un nom de fichier pour en tirer un titre de jeu exploitable en recherche. */
    fun nettoyerNomJeu(nomFichier: String): String {
        return nomFichier
            .substringBeforeLast(".")                       // enlève l'extension
            .replace(Regex("\\[.*?\\]"), " ")                // enlève tags ROM entre crochets [!] [T-Fr] [b]...
            .replace(Regex("\\(.*?\\)"), " ")                // enlève (Europe), (USA), (Rev 1), (Beta)...
            .replace(Regex("[_\\-]+"), " ")                  // underscores/tirets -> espaces
            .replace(Regex("\\s+0\\d{1,2}$"), "")           // enlève un numéro de dump ROM zero-paddé ("... 01"), jamais un vrai "2"/"3" de titre
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalise un titre pour comparaison : sans accents, sans ponctuation, en
     * minuscules, espaces réduits.
     *
     * La ponctuation doit disparaître, sans quoi « New Super Mario Bros » ne
     * correspondrait pas à « New Super Mario Bros. » et l'on retomberait sur un
     * homonyme approchant — typiquement l'épisode Wii U.
     */
    private fun normaliser(texte: String): String {
        val sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sansAccents.lowercase()
            .replace(Regex("[.,:;!?'’\"]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Tous les noms connus d'un jeu : son titre, et ses titres alternatifs régionaux. */
    private fun nomsConnus(jeu: JSONObject): List<String> {
        val noms = mutableListOf(jeu.optString("name", ""))
        jeu.optJSONArray("alternative_names")?.let { autres ->
            for (i in 0 until autres.length()) {
                autres.optJSONObject(i)?.optString("name")?.let { noms.add(it) }
            }
        }
        return noms.filter { it.isNotBlank() }
    }

    /**
     * Le titre demandé est-il exactement l'un des noms de ce jeu ?
     *
     * Exiger l'égalité, et non une ressemblance, est le seul moyen d'éviter la
     * jaquette d'un autre jeu. « Inazuma Eleven » ramène ses trois suites, « New
     * Super Mario Bros » ramène l'épisode Wii U : dans les deux cas, une note de
     * ressemblance élevée désignait le mauvais jeu avec l'assurance du bon.
     */
    private fun correspondExactement(jeu: JSONObject, titreNormalise: String): Boolean =
        nomsConnus(jeu).any { normaliser(it) == titreNormalise }

    /**
     * Départage plusieurs jeux portant exactement le même nom, par leur console.
     *
     * Le nom seul ne suffit pas : le même titre désigne souvent deux jeux distincts
     * selon la machine. La catégorie, c'est-à-dire le dossier, tranche.
     */
    private fun affiniteConsole(jeu: JSONObject, categorie: String?): Int {
        if (categorie.isNullOrBlank()) return 0
        val console = normaliser(categorie)
        val plateformes = mutableListOf<String>()
        jeu.optJSONArray("platforms")?.let { liste ->
            for (i in 0 until liste.length()) {
                val objet = liste.optJSONObject(i) ?: continue
                plateformes.add(normaliser(objet.optString("name", "")))
                plateformes.add(normaliser(objet.optString("abbreviation", "")))
            }
        }
        return when {
            plateformes.any { it.isNotBlank() && it == console } -> 2
            plateformes.any { it.isNotBlank() && (it.contains(console) || console.contains(it)) } -> 1
            else -> 0
        }
    }

    private const val CHAMPS_DEMANDES =
        "name,summary,cover.url,first_release_date,genres.name,rating,rating_count," +
            "involved_companies.company.name,involved_companies.developer," +
            "involved_companies.publisher,platforms.abbreviation,platforms.name," +
            "alternative_names.name"

    /** Neutralise les guillemets, qui délimitent les valeurs dans le langage d'IGDB. */
    private fun echapper(titre: String): String = titre.replace("\"", "\\\"")

    /** Recherche par pertinence : ce que ferait un moteur de recherche. */
    private fun requeteRecherche(titre: String): String {
        val valeur = echapper(titre)
        return "search \"$valeur\"; fields $CHAMPS_DEMANDES; limit 10;"
    }

    /** Recherche sur les autres noms connus d'un jeu, y compris ses titres régionaux. */
    private fun requeteNomAlternatif(titre: String): String {
        val valeur = echapper(titre)
        return "fields $CHAMPS_DEMANDES; where alternative_names.name ~ *\"$valeur\"*; limit 15;"
    }

    /** Envoie une requête Apicalypse et renvoie le tableau de résultats. */
    private fun interroger(token: String, corps: String): JSONArray {
        var connexion: HttpURLConnection? = null
        return try {
            connexion = (URL(URL_JEUX).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Client-ID", identifiant)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "text/plain")
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            connexion.outputStream.use { it.write(corps.toByteArray(Charsets.UTF_8)) }
            JSONArray(connexion.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            // Une interrogation ratée ne doit pas empêcher la suivante d'être tentée.
            JSONArray()
        } finally {
            connexion?.disconnect()
        }
    }

    /** Meilleur candidat exact, ou null si aucun ne correspond vraiment. */
    private fun choisir(resultats: JSONArray, titreNormalise: String, categorie: String?): JSONObject? {
        var meilleur: JSONObject? = null
        var meilleureAffinite = -1
        for (i in 0 until resultats.length()) {
            val candidat = resultats.optJSONObject(i) ?: continue
            if (!correspondExactement(candidat, titreNormalise)) continue
            val affinite = affiniteConsole(candidat, categorie)
            if (affinite > meilleureAffinite) {
                meilleureAffinite = affinite
                meilleur = candidat
            }
        }
        return meilleur
    }

    /**
     * Renvoie les métadonnées auto-détectées pour ce nom de fichier, ou null si les
     * identifiants ne sont pas configurés, si la requête échoue, ou si rien n'est trouvé.
     * Résultat mis en cache par titre nettoyé.
     */
    suspend fun recuperer(nomFichier: String, categorie: String? = null): Metadonnees? =
        withContext(Dispatchers.IO) {
        // Sans identifiants, pas de fiche : l'appli s'en passe et n'affiche que ce que
        // le PC fournit déjà. C'est un manque, jamais une erreur.
        if (!configure) return@withContext null

        val titre = nettoyerNomJeu(nomFichier)
        if (titre.isBlank()) return@withContext null

        // La console fait partie de la clé : le même titre sur deux consoles peut
        // désigner deux jeux differents.
        val cle = "$titre|${categorie ?: ""}"
        if (cache.containsKey(cle)) return@withContext cache[cle]

        val resultat = try {
            val token = obtenirJeton()
            if (token == null) {
                null
            } else {
                val titreNormalise = normaliser(titre)

                // Deux interrogations au plus, dans cet ordre :
                //  1. la recherche par pertinence, qui trouve la plupart des titres ;
                //  2. une requête sur les noms alternatifs, quand la première ne rend
                //     aucune correspondance exacte — c'est elle qui relie un titre
                //     européen à sa fiche d'origine (« Mario Slam Basketball » n'existe
                //     dans la base que comme autre nom de « Mario Hoops 3-on-3 »).
                val jeu = choisir(interroger(token, requeteRecherche(titre)), titreNormalise, categorie)
                    ?: choisir(interroger(token, requeteNomAlternatif(titre)), titreNormalise, categorie)

                if (jeu == null) {
                    // Aucun nom ne correspond exactement : on préfère ne rien afficher
                    // plutôt qu'une jaquette d'un jeu voisin, qui passerait pour vraie.
                    null
                } else {
                    val imageBrute = jeu.optJSONObject("cover")?.optString("url", null)
                    val image = imageBrute?.let { corrigerUrlImage(it) }
                    val descriptionBrute = jeu.optString("summary", "").ifBlank { null }
                    val description = descriptionBrute?.let { couper(it) }

                    Metadonnees(
                        image = image,
                        description = description,
                        annee = anneeDepuis(jeu.optLong("first_release_date", 0L)),
                        genres = nomsDe(jeu.optJSONArray("genres"), "name"),
                        // IGDB note sur 100 avec des décimales : un entier suffit à l'écran.
                        note = jeu.optDouble("rating", Double.NaN)
                            .takeIf { !it.isNaN() }?.roundToInt(),
                        nombreAvis = jeu.optInt("rating_count", 0).takeIf { it > 0 },
                        studio = societe(jeu, "developer"),
                        editeur = societe(jeu, "publisher"),
                        plateformes = nomsDe(jeu.optJSONArray("platforms"), "abbreviation", "name")
                    )
                }
            }
        } catch (e: Exception) {
            null
        }

        cache[cle] = resultat
        resultat
    }

    /** IGDB date les sorties en secondes depuis 1970 ; seule l'année nous intéresse. */
    private fun anneeDepuis(secondes: Long): Int? {
        if (secondes <= 0L) return null
        val calendrier = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendrier.timeInMillis = secondes * 1000L
        return calendrier.get(java.util.Calendar.YEAR)
    }

    /** Noms d'une liste d'objets liés, en essayant plusieurs champs par ordre de préférence. */
    private fun nomsDe(tableau: JSONArray?, vararg champs: String): List<String> {
        if (tableau == null) return emptyList()
        return (0 until tableau.length()).mapNotNull { indice ->
            val objet = tableau.optJSONObject(indice) ?: return@mapNotNull null
            champs.firstNotNullOfOrNull { champ -> objet.optString(champ, "").ifBlank { null } }
        }.distinct().take(4)
    }

    /**
     * Studio ou éditeur : IGDB les mêle dans « involved_companies », distingués par
     * deux drapeaux. Une même société peut porter les deux.
     */
    private fun societe(jeu: JSONObject, drapeau: String): String? {
        val societes = jeu.optJSONArray("involved_companies") ?: return null
        for (indice in 0 until societes.length()) {
            val entree = societes.optJSONObject(indice) ?: continue
            if (entree.optBoolean(drapeau, false)) {
                val nom = entree.optJSONObject("company")?.optString("name", "")
                if (!nom.isNullOrBlank()) return nom
            }
        }
        return null
    }

    /** IGDB renvoie une URL sans protocole et en basse résolution (t_thumb) : on corrige les deux. */
    private fun corrigerUrlImage(urlBrute: String): String {
        val avecProtocole = if (urlBrute.startsWith("//")) "https:$urlBrute" else urlBrute
        return avecProtocole.replace("t_thumb", "t_cover_big")
    }

    private fun couper(texte: String): String {
        return if (texte.length > LONGUEUR_MAX_DESCRIPTION) {
            texte.take(LONGUEUR_MAX_DESCRIPTION).substringBeforeLast(" ") + "…"
        } else texte
    }

    /** Récupère un jeton d'accès Twitch valide, en le renouvelant si besoin. */
    private fun obtenirJeton(): String? {
        val maintenant = System.currentTimeMillis()
        val jetonActuel = jeton
        if (jetonActuel != null && maintenant < jetonExpireA) return jetonActuel

        return try {
            val url = URL("$URL_JETON?client_id=$identifiant&client_secret=$secret&grant_type=client_credentials")
            val connexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val corps = connexion.inputStream.bufferedReader().use { it.readText() }
            connexion.disconnect()

            val json = JSONObject(corps)
            val token = json.getString("access_token")
            val expireDansSecondes = json.optLong("expires_in", 3600L)

            jeton = token
            jetonExpireA = maintenant + (expireDansSecondes * 1000L) - 60_000L // marge de sécurité d'1 min
            token
        } catch (e: Exception) {
            null
        }
    }
}
