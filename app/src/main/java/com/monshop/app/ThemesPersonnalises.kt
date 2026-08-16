package com.monshop.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Thèmes créés par l'utilisateur : enregistrement, chargement, et conversion depuis
 * une teinte choisie au curseur.
 *
 * Les thèmes livrés avec l'appli sont figés dans le code ; ceux-ci vivent dans les
 * préférences, sous forme de liste JSON. Ils se mêlent aux autres dans la liste des
 * apparences, avec un identifiant préfixé pour ne jamais entrer en collision.
 */
private const val PREFS_THEMES = "mon_shop_prefs"
private const val CLE_THEMES_PERSO = "themes_personnalises"
const val PREFIXE_THEME_PERSO = "perso:"

fun estThemePersonnalise(theme: ThemeOption): Boolean = theme.id.startsWith(PREFIXE_THEME_PERSO)

private fun Color.enHexa(): String = String.format("#%08X", toArgb())

private fun couleurDepuisHexa(texte: String?, defaut: Color): Color = try {
    if (texte.isNullOrBlank()) defaut else Color(android.graphics.Color.parseColor(texte))
} catch (e: Exception) {
    defaut
}

fun lireThemesPersonnalises(context: Context): List<ThemeOption> {
    val prefs = context.getSharedPreferences(PREFS_THEMES, Context.MODE_PRIVATE)
    val brut = prefs.getString(CLE_THEMES_PERSO, null) ?: return emptyList()
    return try {
        val tableau = JSONArray(brut)
        (0 until tableau.length()).map { indice ->
            val objet = tableau.getJSONObject(indice)
            ThemeOption(
                id = objet.getString("id"),
                nom = objet.optString("nom", "Custom"),
                primaire = couleurDepuisHexa(objet.optString("primaire"), Color(0xFFD6432A)),
                accent = couleurDepuisHexa(objet.optString("accent"), Color(0xFFFFC107)),
                fond = couleurDepuisHexa(objet.optString("fond"), Color.White),
                surface = couleurDepuisHexa(objet.optString("surface"), Color(0xFFFFF7EA)),
                texte = couleurDepuisHexa(objet.optString("texte"), Color(0xFF2A2320)),
                texteBandeau = couleurDepuisHexa(objet.optString("texteBandeau"), Color.White)
            )
        }
    } catch (e: Exception) {
        // Préférences corrompues : mieux vaut repartir de zéro que d'empêcher
        // l'écran de réglages de s'ouvrir.
        emptyList()
    }
}

fun ecrireThemesPersonnalises(context: Context, themes: List<ThemeOption>) {
    val tableau = JSONArray()
    themes.forEach { theme ->
        tableau.put(
            JSONObject().apply {
                put("id", theme.id)
                put("nom", theme.nom)
                put("primaire", theme.primaire.enHexa())
                put("accent", theme.accent.enHexa())
                put("fond", theme.fond.enHexa())
                put("surface", theme.surface.enHexa())
                put("texte", theme.texte.enHexa())
                put("texteBandeau", theme.texteBandeau.enHexa())
            }
        )
    }
    context.getSharedPreferences(PREFS_THEMES, Context.MODE_PRIVATE)
        .edit()
        .putString(CLE_THEMES_PERSO, tableau.toString())
        .apply()
}

/** Conversion teinte/saturation/luminosité vers RVB, pour les curseurs de l'éditeur. */
fun couleurDepuisTSL(teinte: Float, saturation: Float, luminosite: Float): Color {
    val h = ((teinte % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = luminosite.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, v, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, v + m, b + m, 1f)
}

/**
 * Fabrique un thème à partir d'une couleur choisie au carré chromatique.
 *
 * La couleur dominante et l'accent sont choisis par l'utilisateur ; les neutres (fond,
 * surface, texte) restent dérivés de la teinte, ce qui garantit qu'un texte reste
 * toujours lisible sur son fond, quelle que soit la couleur retenue.
 */
fun themeDepuisChoix(
    identifiant: String,
    nom: String,
    teinte: Float,
    primaire: Color,
    accent: Color,
    sombre: Boolean
): ThemeOption = if (sombre) {
    ThemeOption(
        id = identifiant,
        nom = nom,
        primaire = primaire,
        accent = accent,
        fond = couleurDepuisTSL(teinte, 0.30f, 0.07f),
        surface = couleurDepuisTSL(teinte, 0.26f, 0.13f),
        texte = couleurDepuisTSL(teinte, 0.12f, 0.94f),
        texteBandeau = texteLisibleSur(primaire)
    )
} else {
    ThemeOption(
        id = identifiant,
        nom = nom,
        primaire = primaire,
        accent = accent,
        fond = Color.White,
        surface = couleurDepuisTSL(teinte, 0.42f, 0.955f),
        texte = couleurDepuisTSL(teinte, 0.22f, 0.14f),
        texteBandeau = texteLisibleSur(primaire)
    )
}

/**
 * Noir ou blanc selon le fond, d'après la luminance perçue.
 *
 * Indispensable depuis que la couleur dominante est libre : sur un jaune vif, le blanc
 * du bandeau devenait illisible.
 */
fun texteLisibleSur(fond: Color): Color {
    val luminance = 0.2126f * fond.red + 0.7152f * fond.green + 0.0722f * fond.blue
    return if (luminance > 0.62f) Color(0xFF1A1614) else Color.White
}

/**
 * Fabrique un thème complet à partir d'une seule teinte.
 *
 * L'utilisateur ne choisit qu'une couleur dominante et un mode clair/sombre : dériver
 * le reste évite de lui faire régler six couleurs dont trois qu'il ne verra jamais
 * séparément, et garantit que le texte reste lisible sur chaque fond.
 */
fun themeDepuisTeinte(
    identifiant: String,
    nom: String,
    teinte: Float,
    saturation: Float,
    sombre: Boolean
): ThemeOption {
    val primaire = couleurDepuisTSL(teinte, saturation, if (sombre) 0.52f else 0.44f)
    val accent = couleurDepuisTSL(teinte + 165f, (saturation + 0.25f).coerceAtMost(1f), 0.58f)
    return if (sombre) {
        ThemeOption(
            id = identifiant,
            nom = nom,
            primaire = primaire,
            accent = accent,
            fond = couleurDepuisTSL(teinte, 0.30f, 0.07f),
            surface = couleurDepuisTSL(teinte, 0.26f, 0.13f),
            texte = couleurDepuisTSL(teinte, 0.12f, 0.94f),
            texteBandeau = Color.White
        )
    } else {
        ThemeOption(
            id = identifiant,
            nom = nom,
            primaire = primaire,
            accent = accent,
            fond = Color.White,
            surface = couleurDepuisTSL(teinte, 0.42f, 0.955f),
            texte = couleurDepuisTSL(teinte, 0.22f, 0.14f),
            texteBandeau = Color.White
        )
    }
}

/** Identifiant unique et stable pour un nouveau thème. */
fun nouvelIdentifiantTheme(): String = PREFIXE_THEME_PERSO + System.currentTimeMillis().toString(36)
