package com.monshop.app

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/**
 * Ce que l'utilisateur décide lui-même de l'apparence d'un jeu.
 *
 * Les deux bases d'illustrations se trompent parfois, et aucun recadrage automatique ne
 * devine où se trouve l'essentiel d'une image. Plutôt que d'essayer d'être plus malin,
 * l'appli laisse corriger : une image prise dans la galerie, et un cadrage réglé à la
 * main. Ce choix-là prime sur tout le reste.
 */
data class CadrageJeu(
    /** Image choisie dans la galerie ; null = celle trouvée automatiquement. */
    val uriImage: String? = null,
    /**
     * La zone retenue, en fractions de l'image entière : coin haut-gauche puis taille.
     *
     * Exprimée ainsi plutôt qu'en pixels, elle reste juste quelle que soit la taille
     * de l'image ou celle de la tuile qui l'affiche. La zone par défaut couvre tout.
     */
    val x: Float = 0f,
    val y: Float = 0f,
    val largeur: Float = 1f,
    val hauteur: Float = 1f
) {
    val zoneEntiere: Boolean
        get() = x == 0f && y == 0f && largeur == 1f && hauteur == 1f

    val parDefaut: Boolean
        get() = uriImage == null && zoneEntiere
}

private const val PREFS_PERSO = "mon_shop_prefs"
private const val CLE_CADRAGES = "cadrages_jeux"

/**
 * Les cadrages en mémoire, indexés par nom de fichier.
 *
 * Table d'état observable : une tuile qui lit un cadrage se redessine d'elle-même dès
 * qu'il change, sans que l'écran ait à faire redescendre l'information.
 */
object Personnalisations {

    private val parJeu = mutableStateMapOf<String, CadrageJeu>()
    private var chargees = false

    fun charger(context: Context) {
        if (chargees) return
        chargees = true
        val brut = context.getSharedPreferences(PREFS_PERSO, Context.MODE_PRIVATE)
            .getString(CLE_CADRAGES, null) ?: return
        try {
            val racine = JSONObject(brut)
            for (nom in racine.keys()) {
                val o = racine.getJSONObject(nom)
                parJeu[nom] = CadrageJeu(
                    uriImage = o.optString("uri", "").ifBlank { null },
                    x = o.optDouble("x", 0.0).toFloat(),
                    y = o.optDouble("y", 0.0).toFloat(),
                    largeur = o.optDouble("largeur", 1.0).toFloat().coerceIn(0.05f, 1f),
                    hauteur = o.optDouble("hauteur", 1.0).toFloat().coerceIn(0.05f, 1f)
                )
            }
        } catch (e: Exception) {
            // Réglages illisibles : on repart d'une table vide plutôt que de refuser
            // d'afficher la bibliothèque.
            parJeu.clear()
        }
    }

    fun de(nomJeu: String): CadrageJeu? = parJeu[nomJeu]

    fun definir(context: Context, nomJeu: String, cadrage: CadrageJeu) {
        if (cadrage.parDefaut) {
            effacer(context, nomJeu)
            return
        }
        parJeu[nomJeu] = cadrage
        enregistrer(context)
    }

    fun effacer(context: Context, nomJeu: String) {
        parJeu.remove(nomJeu)
        enregistrer(context)
    }

    private fun enregistrer(context: Context) {
        val racine = JSONObject()
        parJeu.forEach { (nom, c) ->
            racine.put(
                nom,
                JSONObject().apply {
                    c.uriImage?.let { put("uri", it) }
                    put("x", c.x.toDouble())
                    put("y", c.y.toDouble())
                    put("largeur", c.largeur.toDouble())
                    put("hauteur", c.hauteur.toDouble())
                }
            )
        }
        context.getSharedPreferences(PREFS_PERSO, Context.MODE_PRIVATE).edit()
            .putString(CLE_CADRAGES, racine.toString())
            .apply()
    }
}
