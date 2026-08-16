package com.monshop.app

import android.content.Context

/**
 * Les identifiants que l'utilisateur fournit lui-même.
 *
 * Le code de la console et la clé Drive vivent ailleurs — le premier dans les réglages de
 * connexion, la seconde dans ceux de la source — parce qu'ils y servaient déjà avant. Ce
 * fichier ne garde donc que les deux services d'illustration et de fiches, et l'assistant
 * de première ouverture va chercher les autres là où ils sont.
 */
data class ClesApi(
    val igdbId: String = "",
    val igdbSecret: String = "",
    val steamgriddb: String = ""
)

private const val PREFS_CLES = "mon_shop_prefs"
private const val CLE_IGDB_ID = "igdb_client_id"
private const val CLE_IGDB_SECRET = "igdb_client_secret"
private const val CLE_SGDB = "steamgriddb_cle"
private const val CLE_ASSISTANT = "assistant_termine"

fun lireClesApi(context: Context): ClesApi {
    val prefs = context.getSharedPreferences(PREFS_CLES, Context.MODE_PRIVATE)
    return ClesApi(
        igdbId = prefs.getString(CLE_IGDB_ID, "").orEmpty(),
        igdbSecret = prefs.getString(CLE_IGDB_SECRET, "").orEmpty(),
        steamgriddb = prefs.getString(CLE_SGDB, "").orEmpty()
    )
}

fun ecrireClesApi(context: Context, cles: ClesApi) {
    context.getSharedPreferences(PREFS_CLES, Context.MODE_PRIVATE).edit()
        .putString(CLE_IGDB_ID, cles.igdbId.trim())
        .putString(CLE_IGDB_SECRET, cles.igdbSecret.trim())
        .putString(CLE_SGDB, cles.steamgriddb.trim())
        .apply()
}

/**
 * Fait suivre les clés aux services concernés.
 *
 * Appelé au démarrage et après chaque modification : les deux objets gardent leurs
 * identifiants en mémoire vive, ils ne relisent jamais les préférences eux-mêmes.
 */
fun appliquerClesApi(cles: ClesApi) {
    // Un identifiant sans son secret ne permet pas d'obtenir de jeton : le couple
    // incomplet est ignoré plutôt que d'échouer à chaque requête.
    if (cles.igdbId.isNotBlank() && cles.igdbSecret.isNotBlank()) {
        IGDBMetadataService.appliquerIdentifiants(cles.igdbId, cles.igdbSecret)
    }
    if (cles.steamgriddb.isNotBlank()) {
        SteamGridDB.appliquerCle(cles.steamgriddb)
    }
}

/** Vrai une fois l'assistant de première ouverture parcouru, sauté ou non. */
fun lireAssistantTermine(context: Context): Boolean =
    context.getSharedPreferences(PREFS_CLES, Context.MODE_PRIVATE)
        .getBoolean(CLE_ASSISTANT, false)

fun ecrireAssistantTermine(context: Context, termine: Boolean) {
    context.getSharedPreferences(PREFS_CLES, Context.MODE_PRIVATE).edit()
        .putBoolean(CLE_ASSISTANT, termine)
        .apply()
}
