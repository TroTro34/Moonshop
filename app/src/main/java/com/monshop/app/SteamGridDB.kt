package com.monshop.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Illustrations des jeux, fournies par SteamGridDB.
 *
 * Cette base ne connaît aucune information textuelle — ni année, ni genre, ni
 * description : elle ne fait que des images, et c'est précisément ce pour quoi elle est
 * meilleure qu'IGDB. Elle sert les lanceurs de ROMs, sa couverture des consoles est donc
 * bien plus fournie, et elle distingue trois usages que l'appli exploite séparément :
 *
 *   la *jaquette* pour la liste et la carte du jeu,
 *   la *bannière* (format large) pour le fond de la fiche, au lieu d'étirer une jaquette,
 *   le *logo* détouré, qui remplace le titre écrit par-dessus l'image.
 *
 * IGDB reste la source du texte, et son image le repli quand celle-ci ne trouve rien.
 */
object SteamGridDB {

    private const val BASE = "https://www.steamgriddb.com/api/v2"

    // Aucune clé n'est livrée : écrite ici, elle serait lisible dans l'APK, donc
    // consommable et révocable par n'importe quel porteur. Chacun apporte la sienne.
    private var cleEffective: String = ""

    /** Vrai quand l'utilisateur a fourni sa clé. */
    val configure: Boolean get() = cleEffective.isNotBlank()

    fun appliquerCle(cle: String) {
        cleEffective = cle.trim()
        // Les illustrations déjà trouvées restent valables, mais les échecs mis en
        // cache venaient peut-être d'une clé refusée : ils méritent un second essai.
        cache.clear()
    }

    /**
     * Le site est derrière Cloudflare, qui refuse les clients sans agent reconnaissable
     * — le refus prend alors la forme d'une erreur 1010, sans rapport apparent avec
     * l'authentification. L'appli s'annonce donc explicitement.
     */
    private const val AGENT = "Mozilla/5.0 (Android) MoonshopApp/1.0"

    // Les formats que la base publie sous « grids ». Les demander explicitement évite de
    // recevoir une verticale là où il faut une horizontale, et inversement.
    private const val FORMATS_VERTICAUX = "600x900,342x482,660x930"
    private const val FORMATS_LARGES = "920x430,460x215"

    /** Les images d'un jeu ; chacune peut manquer indépendamment des autres. */
    data class Illustrations(
        /** Format vertical, celui d'une boîte de jeu : pour les vignettes hautes. */
        val jaquette: String?,
        /**
         * Même dessin, composé en largeur. Recadrer une jaquette verticale dans une
         * tuile large coupe le titre et la moitié du personnage ; cette variante-là est
         * dessinée pour ce format, donc rien n'est perdu.
         */
        val jaquetteLarge: String?,
        val banniere: String?,
        val logo: String?
    ) {
        val vide: Boolean
            get() = jaquette == null && jaquetteLarge == null && banniere == null && logo == null
    }

    private val cache = mutableMapOf<String, Illustrations?>()

    private fun normaliser(texte: String): String {
        val sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sansAccents.lowercase()
            .replace(Regex("[.,:;!?'’\"-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun interroger(chemin: String): JSONObject? {
        var connexion: HttpURLConnection? = null
        return try {
            connexion = (URL(BASE + chemin).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $cleEffective")
                setRequestProperty("User-Agent", AGENT)
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            if (connexion.responseCode != 200) return null
            JSONObject(connexion.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            null
        } finally {
            connexion?.disconnect()
        }
    }

    /**
     * Adresse de la première image d'un type donné, ou null si ce type manque.
     *
     * `dimensions` restreint aux formats voulus : la base mélange verticales et
     * horizontales sous le même type « grids », et prendre la première venue donne une
     * image au mauvais format une fois sur deux.
     */
    private fun premiereImage(type: String, identifiant: Int, dimensions: String? = null): String? {
        // types=static : une image animée s'afficherait mal en fond de fiche, et pèse
        // inutilement lourd sur une console.
        val filtre = dimensions?.let { "&dimensions=$it" } ?: ""
        val reponse = interroger("/$type/game/$identifiant?limit=1&types=static$filtre") ?: return null
        if (!reponse.optBoolean("success", false)) return null
        val images = reponse.optJSONArray("data") ?: return null
        val premiere = images.optJSONObject(0) ?: return null
        return premiere.optString("url", "").ifBlank { null }
    }

    /**
     * Cherche le jeu par son nom, puis renvoie ses illustrations.
     *
     * Comme pour IGDB, seule une correspondance exacte est retenue : la recherche est
     * floue et renvoie volontiers un autre épisode de la même série, dont l'illustration
     * aurait toutes les apparences de la bonne.
     */
    suspend fun recuperer(nomFichier: String): Illustrations? = withContext(Dispatchers.IO) {
        if (!configure) return@withContext null
        val titre = IGDBMetadataService.nettoyerNomJeu(nomFichier)
        if (titre.isBlank()) return@withContext null
        if (cache.containsKey(titre)) return@withContext cache[titre]

        val resultat = try {
            val encode = URLEncoder.encode(titre, "UTF-8").replace("+", "%20")
            val reponse = interroger("/search/autocomplete/$encode")
            val jeux = reponse?.takeIf { it.optBoolean("success", false) }?.optJSONArray("data")

            var identifiant = -1
            if (jeux != null) {
                val cible = normaliser(titre)
                for (i in 0 until jeux.length()) {
                    val jeu = jeux.optJSONObject(i) ?: continue
                    if (normaliser(jeu.optString("name", "")) == cible) {
                        identifiant = jeu.optInt("id", -1)
                        break
                    }
                }
            }

            if (identifiant <= 0) {
                null
            } else {
                Illustrations(
                    jaquette = premiereImage("grids", identifiant, FORMATS_VERTICAUX),
                    jaquetteLarge = premiereImage("grids", identifiant, FORMATS_LARGES),
                    banniere = premiereImage("heroes", identifiant),
                    logo = premiereImage("logos", identifiant)
                ).takeIf { !it.vide }
            }
        } catch (e: Exception) {
            null
        }

        cache[titre] = resultat
        resultat
    }
}
