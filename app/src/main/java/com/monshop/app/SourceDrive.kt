package com.monshop.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Catalogue construit depuis un dossier Google Drive partagé par lien.
 *
 * Aucune connexion au compte Google : une simple clé d'API suffit tant que le dossier
 * est partagé « accessible à toute personne disposant du lien ». C'est le seul chemin
 * praticable — l'accès à un Drive privé passerait par une autorisation OAuth, dont la
 * vérification par Google est facturée plusieurs milliers d'euros par an.
 *
 * La convention de rangement est la même que côté PC : un sous-dossier = une console,
 * une image de même nom qu'un jeu = sa jaquette.
 */
object SourceDrive {

    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val TYPE_DOSSIER = "application/vnd.google-apps.folder"
    private val EXTENSIONS_IMAGE = listOf(".jpg", ".jpeg", ".png", ".webp")

    /** Page où l'on crée la clé, ouverte depuis les réglages. */
    const val URL_CLE_API = "https://console.cloud.google.com/apis/credentials"

    /** Page où l'on active l'API Drive, sans quoi la clé renverra une erreur. */
    const val URL_ACTIVER_API = "https://console.cloud.google.com/apis/library/drive.googleapis.com"

    class ErreurDrive(message: String) : Exception(message)

    /**
     * Extrait l'identifiant du dossier depuis ce que l'utilisateur colle : lien complet,
     * lien avec paramètres, ou identifiant seul.
     */
    fun identifiantDossier(saisie: String): String? {
        val texte = saisie.trim()
        if (texte.isEmpty()) return null
        Regex("/folders/([A-Za-z0-9_-]+)").find(texte)?.let { return it.groupValues[1] }
        Regex("[?&]id=([A-Za-z0-9_-]+)").find(texte)?.let { return it.groupValues[1] }
        // Identifiant collé seul : les identifiants Drive n'ont ni espace ni barre.
        if (Regex("^[A-Za-z0-9_-]{15,}$").matches(texte)) return texte
        return null
    }

    /** Adresse de téléchargement direct d'un fichier public. */
    fun lienTelechargement(identifiant: String, cle: String): String =
        "$API/$identifiant?alt=media&key=${URLEncoder.encode(cle, "UTF-8")}"

    private fun encoder(valeur: String) = URLEncoder.encode(valeur, "UTF-8")

    private fun lire(url: String): JSONObject {
        var connexion: HttpURLConnection? = null
        try {
            connexion = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", AnnuaireConfig.AGENT)
            }
            val code = connexion.responseCode
            if (code != 200) {
                val detail = connexion.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw ErreurDrive(messageLisible(code, detail))
            }
            return JSONObject(connexion.inputStream.bufferedReader().use { it.readText() })
        } catch (e: ErreurDrive) {
            throw e
        } catch (e: Exception) {
            throw ErreurDrive(e.message ?: "No connection.")
        } finally {
            connexion?.disconnect()
        }
    }

    /**
     * Traduit les refus de Google en phrases actionnables : le message brut de l'API
     * ("API key not valid. Please pass a valid API key.") n'indique jamais laquelle des
     * trois causes possibles est la bonne.
     */
    private fun messageLisible(code: Int, detail: String): String = when {
        code == 400 && detail.contains("API key not valid") ->
            "This API key is not valid. Check that you copied it whole."
        code == 403 && detail.contains("has not been used") ->
            "The Drive API is not enabled for this key yet — enable it, then try again."
        code == 403 && detail.contains("blocked") ->
            "This API key is restricted and does not allow the Drive API."
        code == 403 -> "Access refused (403). Is the folder shared with anyone who has the link?"
        code == 404 -> "Folder not found. Check the link you pasted."
        else -> "Google Drive error ($code)."
    }

    private data class Entree(val id: String, val nom: String, val dossier: Boolean)

    private fun enfants(identifiantDossier: String, cle: String): List<Entree> {
        val entrees = mutableListOf<Entree>()
        var jeton: String? = null
        do {
            val requete = buildString {
                append(API)
                append("?q=").append(encoder("'$identifiantDossier' in parents and trashed = false"))
                append("&key=").append(encoder(cle))
                append("&fields=").append(encoder("nextPageToken,files(id,name,mimeType)"))
                append("&pageSize=200")
                // Nécessaire pour lire un dossier partagé par lien qui n'appartient pas
                // au propriétaire de la clé.
                append("&supportsAllDrives=true&includeItemsFromAllDrives=true")
                if (jeton != null) append("&pageToken=").append(encoder(jeton!!))
            }
            val reponse = lire(requete)
            val fichiers = reponse.optJSONArray("files")
            if (fichiers != null) {
                for (i in 0 until fichiers.length()) {
                    val objet = fichiers.getJSONObject(i)
                    entrees.add(
                        Entree(
                            id = objet.optString("id"),
                            nom = objet.optString("name"),
                            dossier = objet.optString("mimeType") == TYPE_DOSSIER
                        )
                    )
                }
            }
            jeton = reponse.optString("nextPageToken").ifBlank { null }
        } while (jeton != null)
        return entrees
    }

    private fun estImage(nom: String) = EXTENSIONS_IMAGE.any { nom.lowercase().endsWith(it) }

    private fun sansExtension(nom: String) = nom.substringBeforeLast('.', nom)

    /**
     * Parcourt le dossier partagé et en fait un catalogue.
     *
     * Un seul niveau de sous-dossiers est descendu : c'est la convention (un dossier =
     * une console), et cela borne le nombre d'appels à l'API, facturé en quota.
     */
    suspend fun catalogue(lien: String, cle: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val racine = identifiantDossier(lien)
            ?: throw ErreurDrive("This does not look like a Drive folder link.")
        if (cle.isBlank()) throw ErreurDrive("Enter your Google API key first.")

        val resultat = mutableListOf<CatalogItem>()

        fun ajouter(entrees: List<Entree>, categorie: String) {
            val images = entrees.filter { !it.dossier && estImage(it.nom) }
            entrees.filter { !it.dossier && !estImage(it.nom) }.forEach { fichier ->
                val jaquette = images.firstOrNull { sansExtension(it.nom) == sansExtension(fichier.nom) }
                resultat.add(
                    CatalogItem(
                        nom = fichier.nom,
                        cheminServeur = lienTelechargement(fichier.id, cle),
                        image = jaquette?.let { lienTelechargement(it.id, cle) },
                        description = "",
                        categorie = categorie
                    )
                )
            }
        }

        val premierNiveau = enfants(racine, cle)
        ajouter(premierNiveau, "Misc")
        premierNiveau.filter { it.dossier }.forEach { sousDossier ->
            ajouter(enfants(sousDossier.id, cle), sousDossier.nom)
        }
        resultat.sortedWith(compareBy({ it.categorie.lowercase() }, { it.nom.lowercase() }))
    }
}
