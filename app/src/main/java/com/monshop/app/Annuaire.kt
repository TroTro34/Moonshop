package com.monshop.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Résolution du code de la console en adresse du PC.
 *
 * L'utilisateur ne tape son code qu'une fois : l'appli le mémorise et redemande
 * l'adresse à l'annuaire à chaque ouverture. C'est ce qui permet au PC de changer
 * d'adresse (nouveau tunnel à chaque partage) sans que la console s'en aperçoive.
 *
 * L'annuaire est déployé une fois pour toutes côté développeur : personne n'a de
 * compte à créer ni d'adresse à connaître (voir le dossier `worker/`).
 */
object AnnuaireConfig {
    const val URL_BASE = "https://moonshop-annuaire.moonshop-annuaire.workers.dev"

    /** Alphabet du code : ni I, ni O, ni 0, ni 1 (illisibles au clavier virtuel). */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LONGUEUR_CODE = 6

    /**
     * Cloudflare bloque en amont les signatures de clients automatisés (erreur 1010),
     * sans que la requête atteigne le Worker. L'appli s'annonce donc explicitement
     * plutôt que de laisser l'agent par défaut de la pile HTTP d'Android.
     */
    const val AGENT = "Mozilla/5.0 (Android) MoonshopApp/1.0"
}

/** Ce que la console peut apprendre en interrogeant l'annuaire. */
sealed class ResultatAnnuaire {
    /**
     * Le PC publie deux adresses : celle du tunnel, joignable de partout, et celle
     * qu'il a sur son réseau local (vide s'il n'en a pas). Quand les deux appareils
     * sont sur le même wifi, la seconde évite de faire sortir chaque octet jusqu'à
     * Cloudflare pour le faire revenir aussitôt.
     */
    data class Trouve(val adresse: String, val adresseLocale: String) : ResultatAnnuaire()

    /** Code valide mais aucun PC ne partage derrière : éteint, ou partage arrêté. */
    object HorsLigne : ResultatAnnuaire()

    /** L'annuaire lui-même est injoignable (pas de réseau, service en panne). */
    data class Injoignable(val message: String) : ResultatAnnuaire()
}

object Annuaire {

    /**
     * Une adresse en clair n'est acceptée que vers le réseau local.
     *
     * L'appli doit autoriser le HTTP simple, faute de quoi la liaison directe sur le
     * wifi serait impossible : une IP privée ne peut porter aucun certificat. Mais
     * Android ne sait exprimer cette permission que globalement, pour tous les hôtes.
     * Le tri se fait donc ici : hors du réseau local, seul le HTTPS passe, et une
     * réponse d'annuaire détournée vers un serveur en clair ne mène nulle part.
     */
    fun adresseAcceptable(adresse: String): Boolean {
        val propre = adresse.trim()
        if (propre.startsWith("https://")) return true
        if (!propre.startsWith("http://")) return false
        val hote = propre.removePrefix("http://").substringBefore('/').substringBefore(':')
        return estIpPrivee(hote)
    }

    /** Plages réservées aux réseaux privés (RFC 1918), plus la boucle locale. */
    private fun estIpPrivee(hote: String): Boolean {
        val octets = hote.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 10 ||
            a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254)
    }

    /** Ne garde que les caractères de l'alphabet, en majuscules, longueur plafonnée. */
    fun nettoyerCode(saisie: String): String =
        saisie.uppercase()
            .filter { it in AnnuaireConfig.ALPHABET }
            .take(AnnuaireConfig.LONGUEUR_CODE)

    fun codeComplet(code: String): Boolean = code.length == AnnuaireConfig.LONGUEUR_CODE

    /**
     * Choisit l'adresse à utiliser : la locale si le PC répond dessus, sinon le tunnel.
     *
     * Aucune détection de réseau n'est nécessaire — et elle serait de toute façon peu
     * fiable sur Android, où lire le nom du wifi exige une permission de localisation.
     * Essayer l'adresse directement est plus simple et répond à la seule question qui
     * compte : est-elle joignable, ici et maintenant ?
     */
    suspend fun choisirAdresse(trouve: ResultatAnnuaire.Trouve): Pair<String, Boolean> {
        if (adresseAcceptable(trouve.adresseLocale) && repond(trouve.adresseLocale)) {
            return trouve.adresseLocale to true
        }
        // Peut être vide : le PC a coupé son tunnel et n'est joignable que chez lui.
        return trouve.adresse to false
    }

    /** Délai volontairement court : sur un réseau local, un PC joignable répond en
     *  quelques dizaines de millisecondes. Au-delà, il est ailleurs. */
    private suspend fun repond(adresse: String): Boolean = withContext(Dispatchers.IO) {
        var connexion: HttpURLConnection? = null
        try {
            connexion = (URL("$adresse/catalogue.json").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1_500
                readTimeout = 1_500
                setRequestProperty("User-Agent", AnnuaireConfig.AGENT)
                Appairage.signer(this)
            }
            // 401 compte comme une réponse : la question posée ici est « ce PC est-il
            // joignable en direct ? », pas « ai-je le droit d'y télécharger ». Les
            // confondre ferait passer par Cloudflare un PC pourtant sur le même wifi.
            connexion.responseCode == 200 || connexion.responseCode == 401
        } catch (e: Exception) {
            false
        } finally {
            connexion?.disconnect()
        }
    }

    suspend fun resoudre(code: String): ResultatAnnuaire = withContext(Dispatchers.IO) {
        val propre = nettoyerCode(code)
        if (!codeComplet(propre)) {
            return@withContext ResultatAnnuaire.Injoignable("Enter the 6 characters of your code.")
        }

        val url = URL(
            "${AnnuaireConfig.URL_BASE}/resolve?code=" + URLEncoder.encode(propre, "UTF-8")
        )
        var connexion: HttpURLConnection? = null
        try {
            connexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", AnnuaireConfig.AGENT)
            }
            when (val statut = connexion.responseCode) {
                200 -> {
                    val corps = connexion.inputStream.bufferedReader().use { it.readText() }
                    val objet = JSONObject(corps)
                    val adresse = objet.optString("url", "")
                    val locale = objet.optString("url_locale", "")
                    val localeSure = adresseAcceptable(locale)
                    when {
                        adresse.isBlank() && localeSure -> ResultatAnnuaire.Trouve("", locale.trimEnd('/'))
                        adresse.isBlank() ->
                            ResultatAnnuaire.Injoignable("Unexpected answer from the directory.")
                        // Le contrôle existe déjà côté annuaire ; le refaire ici protège
                        // même si la réponse venait d'ailleurs que du vrai service.
                        !adresseAcceptable(adresse) ->
                            ResultatAnnuaire.Injoignable("The directory returned an unsafe address.")
                        else -> ResultatAnnuaire.Trouve(
                            adresse.trimEnd('/'),
                            if (localeSure) locale.trimEnd('/') else ""
                        )
                    }
                }
                // 404 : le code est bien formé, mais rien n'est publié derrière.
                404 -> ResultatAnnuaire.HorsLigne
                // Limitation de débit de l'annuaire : passagère, contrairement aux autres.
                429 -> ResultatAnnuaire.Injoignable("Too many attempts. Wait a minute and retry.")
                // 403 : requête refusée en amont du Worker (filtrage Cloudflare).
                // Nommé à part, sans quoi on cherche un problème de réseau pendant
                // des heures alors que la requête arrive très bien.
                403 -> ResultatAnnuaire.Injoignable("Blocked by the directory (403).")
                else -> ResultatAnnuaire.Injoignable("Directory error ($statut).")
            }
        } catch (e: Exception) {
            ResultatAnnuaire.Injoignable(e.message ?: "No connection.")
        } finally {
            connexion?.disconnect()
        }
    }
}
