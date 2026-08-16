package com.monshop.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class FileInstaller(private val baseUrl: String, private val context: Context) {

    private fun adresseComplete(chemin: String): String =
        if (chemin.startsWith("http://") || chemin.startsWith("https://")) chemin else "$baseUrl$chemin"

    /** Récupère et parse le catalogue JSON depuis <baseUrl>/catalogue.json */
    suspend fun recupererCatalogue(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/catalogue.json")
        val connexion = url.openConnection() as HttpURLConnection
        connexion.connectTimeout = 10_000
        connexion.readTimeout = 10_000
        Appairage.signer(connexion)
        try {
            // 401 : le PC ne reconnaît pas cette console. Distingué des autres erreurs
            // pour que l'écran propose l'appairage au lieu d'annoncer une panne.
            if (connexion.responseCode == 401) throw Appairage.NonAppaire()
            val json = connexion.inputStream.bufferedReader().use { it.readText() }
            parseCatalogue(json)
        } finally {
            connexion.disconnect()
        }
    }

    /**
     * Télécharge un fichier et l'écrit dans le dossier choisi par l'utilisateur.
     * onProgress renvoie : pourcentage (0-100), octets déjà téléchargés, octets total (0 si inconnu).
     */
    suspend fun installer(
        item: CatalogItem,
        dossierDestinationUri: Uri,
        onProgress: (pourcentage: Int, octetsTelecharges: Long, octetsTotal: Long) -> Unit = { _, _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val dossier = DocumentFile.fromTreeUri(context, dossierDestinationUri)
            ?: return@withContext Result.failure(Exception("Invalid destination folder"))

        try {
            dossier.findFile(item.nom)?.delete()

            val nouveauFichier = dossier.createFile("application/octet-stream", item.nom)
                ?: return@withContext Result.failure(Exception("Could not create the file in this folder"))

            // Google Drive fournit une adresse complète par fichier, là où le PC
            // ne donne qu'un chemin relatif à son serveur : on distingue les deux
            // plutôt que d'imposer une source unique au reste du code.
            val url = URL(adresseComplete(item.cheminServeur))
            val connexion = url.openConnection() as HttpURLConnection
            connexion.connectTimeout = 10_000
            connexion.readTimeout = 30_000
            // Demande le fichier "brut" : certains serveurs/tunnels (compression à la
            // volée, chunked transfer-encoding) suppriment sinon l'en-tête Content-Length,
            // ce qui empêchait la barre de progression de savoir où elle en est.
            connexion.setRequestProperty("Accept-Encoding", "identity")
            Appairage.signer(connexion)

            if (connexion.responseCode == 401) throw Appairage.NonAppaire()

            val tailleTotale = connexion.contentLengthLong

            connexion.inputStream.use { entree ->
                context.contentResolver.openOutputStream(nouveauFichier.uri)?.use { sortie ->
                    val buffer = ByteArray(64 * 1024)
                    var lu: Int
                    var totalLu = 0L
                    while (true) {
                        // Vérifie à chaque tour si le téléchargement a été annulé (bouton
                        // "Annuler" côté utilisateur) : sans ce contrôle explicite, la lecture
                        // bloquante du flux réseau ignorerait l'annulation jusqu'à la fin.
                        ensureActive()
                        lu = entree.read(buffer)
                        if (lu == -1) break
                        sortie.write(buffer, 0, lu)
                        totalLu += lu
                        val pct = if (tailleTotale > 0) ((totalLu * 100) / tailleTotale).toInt() else 0
                        onProgress(pct, totalLu, tailleTotale)
                    }
                } ?: return@withContext Result.failure(Exception("Could not write the file"))
            }
            connexion.disconnect()
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Annulé par l'utilisateur : on supprime le fichier partiellement téléchargé
            // pour ne pas laisser un fichier tronqué/inutilisable à cet emplacement.
            try { dossier.findFile(item.nom)?.delete() } catch (ignore: Exception) {}
            throw e
        } catch (e: Exception) {
            try { dossier.findFile(item.nom)?.delete() } catch (ignore: Exception) {}
            Result.failure(e)
        }
    }
}
