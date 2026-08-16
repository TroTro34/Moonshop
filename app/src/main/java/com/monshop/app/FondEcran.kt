package com.monshop.app

import android.content.Context
import android.net.Uri

/**
 * Image de fond de l'appli : une photo choisie par l'utilisateur, et son voile.
 *
 * Le voile n'est pas un ornement — une image claire derrière la liste rend les titres
 * illisibles, et sans moyen de la ternir l'option ne servirait qu'avec des images sombres.
 *
 * Le fond d'écran du système a été envisagé puis retiré : Android en interdit la lecture
 * aux applications ordinaires, l'option ne pouvait qu'échouer sur les appareils récents.
 */
private const val PREFS_FOND = "mon_shop_prefs"
private const val CLE_FOND_URI = "fond_ecran_uri"
private const val CLE_FOND_VOILE = "fond_ecran_voile"

data class ReglagesFondEcran(val uri: Uri?, val voile: Float)

fun lireFondEcran(context: Context): ReglagesFondEcran {
    val prefs = context.getSharedPreferences(PREFS_FOND, Context.MODE_PRIVATE)
    val texte = prefs.getString(CLE_FOND_URI, null)
    val uri = texte?.let { try { Uri.parse(it) } catch (e: Exception) { null } }
    return ReglagesFondEcran(uri, prefs.getFloat(CLE_FOND_VOILE, 0.45f))
}

fun ecrireFondEcran(context: Context, reglages: ReglagesFondEcran) {
    context.getSharedPreferences(PREFS_FOND, Context.MODE_PRIVATE).edit()
        .putString(CLE_FOND_URI, reglages.uri?.toString())
        .putFloat(CLE_FOND_VOILE, reglages.voile)
        .apply()
}

fun effacerFondEcran(context: Context) {
    context.getSharedPreferences(PREFS_FOND, Context.MODE_PRIVATE).edit()
        .remove(CLE_FOND_URI)
        .apply()
}
