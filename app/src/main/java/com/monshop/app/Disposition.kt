package com.monshop.app

import android.content.Context

/**
 * Les façons d'agencer la bibliothèque.
 *
 * Aucune n'est meilleure dans l'absolu : une ludothèque de trente jeux se regarde, une
 * de trois cents se parcourt. Le choix reste donc à l'utilisateur, et chaque disposition
 * assume un parti pris plutôt que d'être une variante tiède de la précédente.
 */
enum class Disposition(val etiquette: String, val explication: String) {
    /** Tuiles inégales et alternées : l'image d'abord, le rythme irrégulier. */
    MOSAIQUE("Mosaic", "Uneven tiles, artwork first. Best for browsing."),

    /** Une rangée qui défile par console, façon rayon de magasin. */
    ETAGERES("Shelves", "One scrolling row per console, box art upright."),

    /** Grille régulière de jaquettes : la vue la plus dense. */
    GRILLE("Grid", "Even rows of covers. Fits the most games on screen."),

    /** La liste d'origine : vignette, titre, description. */
    LISTE("List", "One row per game, with its description.");

    companion object {
        val PAR_DEFAUT = MOSAIQUE
    }
}

private const val PREFS_DISPOSITION = "mon_shop_prefs"
private const val CLE_DISPOSITION = "disposition_bibliotheque"

fun lireDisposition(context: Context): Disposition {
    val nom = context.getSharedPreferences(PREFS_DISPOSITION, Context.MODE_PRIVATE)
        .getString(CLE_DISPOSITION, null) ?: return Disposition.PAR_DEFAUT
    // valueOf lèverait sur un nom inconnu — cas réel si une disposition disparaît
    // d'une version à l'autre.
    return Disposition.values().firstOrNull { it.name == nom } ?: Disposition.PAR_DEFAUT
}

fun ecrireDisposition(context: Context, disposition: Disposition) {
    context.getSharedPreferences(PREFS_DISPOSITION, Context.MODE_PRIVATE).edit()
        .putString(CLE_DISPOSITION, disposition.name)
        .apply()
}
