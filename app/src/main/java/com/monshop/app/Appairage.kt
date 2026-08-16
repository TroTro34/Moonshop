package com.monshop.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Autorisation de cette console auprès d'un PC.
 *
 * Le code à six caractères ne donne plus l'accès : il mène jusqu'à la porte, et c'est
 * l'utilisateur, devant son PC, qui ouvre. La console reçoit alors un jeton qui n'est
 * qu'à elle — un code aperçu par-dessus une épaule ne suffit donc plus à télécharger.
 *
 * Le jeton est retenu par code : changer de PC redemande une autorisation, revenir au
 * précédent n'en redemande pas.
 */
object Appairage {

    private const val PREFS = "mon_shop_prefs"
    private const val CLE_ID = "appairage_id"
    private const val CLE_CODE = "appairage_code"
    private const val CLE_JETON = "appairage_jeton"

    private const val ENTETE = "X-Moonshop-Appareil"

    // Le PC oublie une demande restée sans réponse au bout de cinq minutes ; la console
    // cesse d'attendre un peu avant, pour annoncer l'abandon plutôt que de le subir.
    private const val ATTENTE_MAX_MS = 240_000L
    private const val PERIODE_SONDAGE_MS = 2_000L

    /**
     * Jeton en mémoire, lu à chaque requête sortante.
     *
     * Tenu ici plutôt que relu des préférences à chaque téléchargement : le
     * téléchargement s'exécute hors du fil principal, où l'accès aux préférences est
     * possible mais inutilement coûteux.
     */
    @Volatile
    var jeton: String = ""
        private set

    /** Levée quand le PC refuse la requête faute d'autorisation. */
    class NonAppaire : IOException("This console is not approved on that PC yet")

    sealed class Resultat {
        object Accepte : Resultat()
        object Refuse : Resultat()
        data class Echec(val message: String) : Resultat()
    }

    fun identifiant(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(CLE_ID, null)?.let { return it }
        val nouveau = UUID.randomUUID().toString()
        prefs.edit().putString(CLE_ID, nouveau).apply()
        return nouveau
    }

    /** Nom lisible affiché sur le PC au moment d'accepter. */
    fun nomAppareil(): String {
        val marque = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val modele = Build.MODEL.orEmpty()
        return if (modele.startsWith(marque, ignoreCase = true)) modele else "$marque $modele"
    }

    /** Recharge le jeton mémorisé pour ce code, ou le vide si le code a changé. */
    fun charger(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        jeton = if (prefs.getString(CLE_CODE, "") == code) {
            prefs.getString(CLE_JETON, "").orEmpty()
        } else {
            ""
        }
    }

    private fun retenir(context: Context, code: String, nouveauJeton: String) {
        jeton = nouveauJeton
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(CLE_CODE, code)
            .putString(CLE_JETON, nouveauJeton)
            .apply()
    }

    /** Oublie l'autorisation : le PC l'a révoquée, ou le jeton n'est plus reconnu. */
    fun oublier(context: Context) {
        jeton = ""
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(CLE_JETON)
            .remove(CLE_CODE)
            .apply()
    }

    /** Pose l'en-tête d'autorisation, quand cette console en détient une. */
    fun signer(connexion: HttpURLConnection) {
        if (jeton.isNotBlank()) connexion.setRequestProperty(ENTETE, jeton)
    }

    /**
     * S'assure que cette console est autorisée sur le PC joignable à `base`.
     *
     * Renvoie immédiatement si un jeton est déjà connu. Sinon dépose une demande et
     * interroge le PC jusqu'à ce que quelqu'un accepte, refuse, ou que l'attente soit
     * trop longue. `onAttente` est appelé une fois, quand l'attente commence : c'est ce
     * qui permet à l'écran de dire quoi faire au lieu de rester figé.
     */
    suspend fun assurer(
        context: Context,
        base: String,
        code: String,
        onAttente: () -> Unit
    ): Resultat = withContext(Dispatchers.IO) {
        if (jeton.isNotBlank()) return@withContext Resultat.Accepte

        val identifiant = identifiant(context)
        val premiere = try {
            demander(base, identifiant, nomAppareil())
        } catch (e: Exception) {
            return@withContext Resultat.Echec("Could not reach your PC to ask for access.")
        }

        premiere.optString("jeton_appareil", "").takeIf { it.isNotBlank() }?.let {
            retenir(context, code, it)
            return@withContext Resultat.Accepte
        }
        if (premiere.optString("statut") == "refuse") return@withContext Resultat.Refuse

        onAttente()

        val limite = System.currentTimeMillis() + ATTENTE_MAX_MS
        while (System.currentTimeMillis() < limite) {
            delay(PERIODE_SONDAGE_MS)
            val etat = try {
                interroger(base, identifiant)
            } catch (e: Exception) {
                // Coupure passagère : on retente jusqu'à la limite plutôt que
                // d'abandonner une demande que l'utilisateur va peut-être accepter.
                continue
            }
            when (etat.optString("statut")) {
                "accepte" -> {
                    val recu = etat.optString("jeton_appareil", "")
                    if (recu.isNotBlank()) {
                        retenir(context, code, recu)
                        return@withContext Resultat.Accepte
                    }
                }
                "refuse" -> return@withContext Resultat.Refuse
                // « inconnu » : le PC a oublié la demande (redémarrage, expiration).
                // La redéposer vaut mieux qu'attendre une réponse qui ne viendra pas.
                "inconnu" -> runCatching { demander(base, identifiant, nomAppareil()) }
            }
        }
        Resultat.Echec("No answer from your PC. Approve the request there, then try again.")
    }

    private fun demander(base: String, identifiant: String, nom: String): JSONObject {
        val corps = JSONObject()
            .put("id", identifiant)
            .put("nom", nom)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val connexion = (URL("$base/appairage").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", AnnuaireConfig.AGENT)
        }
        return try {
            connexion.outputStream.use { it.write(corps) }
            // 202 « en attente » est une réponse normale, pas une erreur : elle arrive
            // par errorStream, qu'il faut donc lire aussi.
            val flux = if (connexion.responseCode in 200..299) connexion.inputStream else connexion.errorStream
            JSONObject(flux.bufferedReader().use { it.readText() })
        } finally {
            connexion.disconnect()
        }
    }

    private fun interroger(base: String, identifiant: String): JSONObject {
        val adresse = "$base/appairage/etat?id=" + URLEncoder.encode(identifiant, "UTF-8")
        val connexion = (URL(adresse).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", AnnuaireConfig.AGENT)
        }
        return try {
            JSONObject(connexion.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connexion.disconnect()
        }
    }
}
