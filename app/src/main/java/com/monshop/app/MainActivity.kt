package com.monshop.app

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Adresse du serveur de fichiers, modifiable depuis les réglages sans recompiler l'appli.
 *
 * C'est ce qui permet de ne plus dépendre du réseau local : IP locale à la maison
 * (`http://192.168.x.x:8080`), adresse Tailscale (`http://100.x.x.x:8080`) ou URL
 * Cloudflare Tunnel (`https://...`) en déplacement — il suffit de changer la valeur ici.
 *
 * `mutableStateOf` et non une simple variable : les écrans qui lisent l'adresse
 * (liste, jaquettes) se recomposent tout seuls dès qu'elle change.
 */
object ServeurConfig {
    const val URL_PAR_DEFAUT = "http://192.168.31.13:8080"
    var url by mutableStateOf(URL_PAR_DEFAUT)

    /** Enlève les espaces et le "/" final : les chemins du catalogue commencent déjà par "/". */
    fun nettoyer(saisie: String): String = saisie.trim().trimEnd('/')
}

private const val PREFS = "mon_shop_prefs"
private const val CLE_DOSSIER_PAR_DEFAUT = "dossier_par_defaut"
private const val CLE_DOSSIERS_PAR_ITEM = "dossiers_par_item"
private const val CLE_THEME = "theme_choisi"
private const val CLE_MUSIQUE_URI = "musique_uri"
private const val CLE_MUSIQUE_NOM = "musique_nom"
private const val CLE_MUSIQUE_ACTIVE = "musique_active"
private const val CLE_MUSIQUE_VOLUME = "musique_volume"
private const val CLE_SONS_ACTIFS = "sons_actifs"
private const val CLE_SONS_VOLUME = "sons_volume"
private const val CLE_MOTIF_FOND = "motif_fond"
private const val CLE_URL_SERVEUR = "url_serveur"
private const val CLE_CODE_CONSOLE = "code_console"
private const val CLE_SOURCE = "source_catalogue"
private const val CLE_DRIVE_CLE_API = "drive_cle_api"
private const val CLE_DRIVE_LIEN = "drive_lien"

/** Catégorie virtuelle (pas une vraie catégorie du catalogue) pour filtrer les jeux installés. */
const val CATEGORIE_INSTALLES = "__INSTALLES__"

// ---------- Thèmes de couleurs (Réglages > Apparence) ----------
// Chaque thème définit toute la palette utilisée dans l'appli (bandeau, fond, cartes,
// accent, texte) : changer de thème recolore donc l'intégralité de l'interface.
data class ThemeOption(
    val id: String,
    val nom: String,
    val primaire: Color,   // bandeau, boutons principaux
    val accent: Color,     // badges, sélection, statut "installé"
    val fond: Color,       // fond général de l'appli
    val surface: Color,    // cartes, dialogues
    val texte: Color,      // texte sur fond/surface
    val texteBandeau: Color = Color.White // texte/icônes sur le bandeau (couleur "primaire")
)

val THEMES = listOf(
    ThemeOption("rouge", "Fire Flower", Color(0xFFD6432A), Color(0xFFFFC107), Color(0xFFFFFFFF), Color(0xFFFFF7EA), Color(0xFF2A2320)),
    ThemeOption("bleu", "Ocean Blue", Color(0xFF1E6FD9), Color(0xFF4FD1C5), Color(0xFFFFFFFF), Color(0xFFEAF4FF), Color(0xFF152230)),
    ThemeOption("vert", "Forest Green", Color(0xFF2E8B57), Color(0xFFFFD166), Color(0xFFFFFFFF), Color(0xFFEEFAF1), Color(0xFF1F2E22)),
    ThemeOption("orange", "Sunset", Color(0xFFE8712C), Color(0xFFFFD23F), Color(0xFFFFFFFF), Color(0xFFFFF3E6), Color(0xFF2E2015)),
    ThemeOption("rose", "Sakura", Color(0xFFE85D8A), Color(0xFF7FD8C8), Color(0xFFFFFFFF), Color(0xFFFFF0F5), Color(0xFF33202A)),
    ThemeOption("violet", "Galaxy Purple", Color(0xFF7C5CBF), Color(0xFFFF6FB5), Color(0xFF14101C), Color(0xFF1F1930), Color(0xFFF3EEFA)),
    ThemeOption("noir", "Neon Black", Color(0xFF1AC9E6), Color(0xFFFF2D95), Color(0xFF0A0A0F), Color(0xFF16161F), Color(0xFFECECF5))
)
val THEME_PAR_DEFAUT = THEMES.first()

fun lireThemeChoisi(context: Context): ThemeOption {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val id = prefs.getString(CLE_THEME, null) ?: return THEME_PAR_DEFAUT
    // Les thèmes créés par l'utilisateur sont cherchés aussi : sans cela, le sien
    // serait oublié à chaque redémarrage et l'appli reviendrait au thème d'origine.
    return THEMES.find { it.id == id }
        ?: lireThemesPersonnalises(context).find { it.id == id }
        ?: THEME_PAR_DEFAUT
}

fun ecrireThemeChoisi(context: Context, theme: ThemeOption) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(CLE_THEME, theme.id).apply()
}

fun construireColorScheme(theme: ThemeOption) = lightColorScheme(
    primary = theme.primaire,
    onPrimary = theme.texteBandeau,
    secondary = theme.accent,
    onSecondary = theme.texte,
    background = theme.fond,
    onBackground = theme.texte,
    surface = theme.surface,
    onSurface = theme.texte
)

// Police personnalisée (ronde) pour le titre "MOONSHOP"
val PoliceMoonshop = FontFamily(Font(R.font.tbj_buffy))

// ---------- Musique de fond (Réglages > Musique) ----------
// Réglages persistés : fichier choisi (avec accès permanent via prise de permission
// persistable), activation, et volume. La lecture elle-même est pilotée par MusicManager.
data class ReglagesMusique(val uri: Uri?, val nom: String?, val active: Boolean, val volume: Float)

fun lireReglagesMusique(context: Context): ReglagesMusique {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val uriTexte = prefs.getString(CLE_MUSIQUE_URI, null)
    val uri = uriTexte?.let { try { Uri.parse(it) } catch (e: Exception) { null } }
    val nom = prefs.getString(CLE_MUSIQUE_NOM, null)
    val active = prefs.getBoolean(CLE_MUSIQUE_ACTIVE, false)
    val volume = prefs.getFloat(CLE_MUSIQUE_VOLUME, 0.6f)
    return ReglagesMusique(uri, nom, active, volume)
}

fun ecrireMusiqueChoisie(context: Context, uri: Uri, nom: String) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(CLE_MUSIQUE_URI, uri.toString()).putString(CLE_MUSIQUE_NOM, nom).apply()
}

fun effacerMusiqueChoisie(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().remove(CLE_MUSIQUE_URI).remove(CLE_MUSIQUE_NOM).putBoolean(CLE_MUSIQUE_ACTIVE, false).apply()
}

fun ecrireMusiqueActive(context: Context, active: Boolean) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(CLE_MUSIQUE_ACTIVE, active).apply()
}

fun ecrireVolumeMusique(context: Context, volume: Float) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putFloat(CLE_MUSIQUE_VOLUME, volume).apply()
}

// ---------- Adresse du serveur en cours d'utilisation ----------
// Renseignée par la résolution du code : plus aucune saisie manuelle d'adresse.
// Conservée entre deux lancements pour pouvoir tenter un chargement immédiat au
// démarrage, avant même que l'annuaire ait répondu.
fun lireUrlServeur(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val valeur = prefs.getString(CLE_URL_SERVEUR, null)
    return if (valeur.isNullOrBlank()) ServeurConfig.URL_PAR_DEFAUT else valeur
}

fun ecrireUrlServeur(context: Context, url: String) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(CLE_URL_SERVEUR, url).apply()
}

// ---------- Code de la console (Réglages > Console code) ----------
// Tapé une seule fois par l'utilisateur, puis conservé : c'est lui qui permet de
// retrouver le PC à chaque ouverture, quelle que soit son adresse du moment.
fun lireCodeConsole(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return prefs.getString(CLE_CODE_CONSOLE, "") ?: ""
}

fun ecrireCodeConsole(context: Context, code: String) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(CLE_CODE_CONSOLE, code).apply()
}

/** D'où l'appli tire son catalogue. */
enum class SourceCatalogue { CONSOLE, DRIVE }

data class ReglagesDrive(val cleApi: String, val lien: String)

fun lireSource(context: Context): SourceCatalogue {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return if (prefs.getString(CLE_SOURCE, "") == "drive") SourceCatalogue.DRIVE
    else SourceCatalogue.CONSOLE
}

fun ecrireSource(context: Context, source: SourceCatalogue) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(CLE_SOURCE, if (source == SourceCatalogue.DRIVE) "drive" else "console").apply()
}

fun lireReglagesDrive(context: Context): ReglagesDrive {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return ReglagesDrive(
        cleApi = prefs.getString(CLE_DRIVE_CLE_API, "") ?: "",
        lien = prefs.getString(CLE_DRIVE_LIEN, "") ?: ""
    )
}

fun ecrireReglagesDrive(context: Context, reglages: ReglagesDrive) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(CLE_DRIVE_CLE_API, reglages.cleApi.trim())
        .putString(CLE_DRIVE_LIEN, reglages.lien.trim())
        .apply()
}

/** Ouvre une page dans le navigateur du système. */
fun ouvrirLien(context: Context, url: String) {
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // Aucun navigateur installé : rien à faire, mais surtout ne pas planter.
    }
}

/** Ce que l'écran de réglages affiche à propos du code : où en est la connexion. */
enum class EtatConnexion { AUCUN, RECHERCHE, APPAIRAGE, REFUSE, CONNECTE, HORS_LIGNE, ERREUR }

// ---------- Effets sonores (Réglages > Sound effects) ----------
fun lireSonsActifs(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return prefs.getBoolean(CLE_SONS_ACTIFS, true)
}

fun ecrireSonsActifs(context: Context, actifs: Boolean) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(CLE_SONS_ACTIFS, actifs).apply()
}

fun lireMotifFond(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return prefs.getBoolean(CLE_MOTIF_FOND, true)
}

fun ecrireMotifFond(context: Context, actif: Boolean) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(CLE_MOTIF_FOND, actif).apply()
}

fun lireVolumeSons(context: Context): Float {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return prefs.getFloat(CLE_SONS_VOLUME, SoundEffects.VOLUME_PAR_DEFAUT)
}

fun ecrireVolumeSons(context: Context, volume: Float) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putFloat(CLE_SONS_VOLUME, volume).apply()
}

/** Essaie de retrouver le nom "affichable" d'un fichier choisi via le sélecteur système. */
fun nomAffichableFichier(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { curseur ->
            val index = curseur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && curseur.moveToFirst()) curseur.getString(index) else uri.lastPathSegment ?: "Selected file"
        } ?: (uri.lastPathSegment ?: "Selected file")
    } catch (e: Exception) {
        uri.lastPathSegment ?: "Selected file"
    }
}

/**
 * Lecteur de musique de fond, unique pour toute l'appli (survit à la navigation entre
 * écrans, contrairement à un MediaPlayer logé dans un composable qui serait recréé à
 * chaque changement d'écran). Boucle en continu tant qu'elle est active.
 */
object MusicManager {
    private var lecteur: MediaPlayer? = null
    private var uriEnCours: Uri? = null
    // Vrai quand la musique a été suspendue parce que l'appli est passée en arrière-plan :
    // seule une reprise (retour au premier plan) doit alors la relancer.
    private var suspendueEnArrierePlan = false

    fun demarrer(context: Context, uri: Uri, volume: Float) {
        suspendueEnArrierePlan = false
        if (uriEnCours == uri && lecteur != null) {
            definirVolume(volume)
            if (lecteur?.isPlaying == false) {
                try { lecteur?.start() } catch (e: Exception) {}
            }
            return
        }
        arreter()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener { it.start() }
            mp.setOnErrorListener { _, _, _ -> arreter(); true }
            mp.prepareAsync()
            lecteur = mp
            uriEnCours = uri
        } catch (e: Exception) {
            lecteur = null
            uriEnCours = null
        }
    }

    fun definirVolume(volume: Float) {
        try { lecteur?.setVolume(volume, volume) } catch (e: Exception) {}
    }

    fun arreter() {
        try { lecteur?.stop() } catch (e: Exception) {}
        try { lecteur?.release() } catch (e: Exception) {}
        lecteur = null
        uriEnCours = null
        suspendueEnArrierePlan = false
    }

    /** Appli mise en arrière-plan : la musique se met en pause (elle ne doit jamais
     *  continuer à jouer par-dessus une autre appli), sans perdre sa position. */
    fun suspendre() {
        try {
            if (lecteur?.isPlaying == true) {
                lecteur?.pause()
                suspendueEnArrierePlan = true
            }
        } catch (e: Exception) {}
    }

    /** Retour au premier plan : ne relance que ce qui a été suspendu par [suspendre]. */
    fun reprendre() {
        if (!suspendueEnArrierePlan) return
        suspendueEnArrierePlan = false
        try { lecteur?.start() } catch (e: Exception) {}
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundEffects.actifs = lireSonsActifs(this)
        SoundEffects.volume = lireVolumeSons(this)
        ServeurConfig.url = lireUrlServeur(this)
        setContent {
            val context = LocalContext.current
            var themeActuel by remember { mutableStateOf(lireThemeChoisi(context)) }

            MaterialTheme(colorScheme = construireColorScheme(themeActuel)) {
                ShopScreen(
                    themeActuel = themeActuel,
                    onChangerTheme = { nouveau ->
                        themeActuel = nouveau
                        ecrireThemeChoisi(context, nouveau)
                    }
                )
            }
        }
    }

    // La musique ne joue que pendant que l'appli est réellement à l'écran : dès qu'elle
    // passe en arrière-plan (autre appli, écran verrouillé), elle se met en pause et
    // reprend là où elle en était au retour.
    override fun onStart() {
        super.onStart()
        MusicManager.reprendre()
    }

    override fun onStop() {
        super.onStop()
        MusicManager.suspendre()
    }

    override fun onDestroy() {
        super.onDestroy()
        // La musique de fond ne doit pas continuer à jouer une fois l'appli quittée.
        MusicManager.arreter()
    }
}

fun lireDossierParDefaut(context: Context): Uri? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val valeur = prefs.getString(CLE_DOSSIER_PAR_DEFAUT, null) ?: return null
    return Uri.parse(valeur)
}

fun ecrireDossierParDefaut(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(CLE_DOSSIER_PAR_DEFAUT, uri.toString()).apply()
}

/** Mémorise, pour un item donné, dans quel dossier il a été installé (pour pouvoir le
 *  retrouver plus tard : vérifier s'il est toujours là, le mettre à jour, le désinstaller). */
private fun lireDossiersParItem(context: Context): MutableMap<String, String> {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val json = prefs.getString(CLE_DOSSIERS_PAR_ITEM, null) ?: return mutableMapOf()
    val obj = JSONObject(json)
    val carte = mutableMapOf<String, String>()
    obj.keys().forEach { cle -> carte[cle] = obj.getString(cle) }
    return carte
}

private fun ecrireDossiersParItem(context: Context, carte: Map<String, String>) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val obj = JSONObject()
    carte.forEach { (cle, valeur) -> obj.put(cle, valeur) }
    prefs.edit().putString(CLE_DOSSIERS_PAR_ITEM, obj.toString()).apply()
}

fun dossierEnregistrePour(context: Context, nomItem: String): Uri? {
    val valeur = lireDossiersParItem(context)[nomItem] ?: return null
    return try { Uri.parse(valeur) } catch (e: Exception) { null }
}

fun enregistrerDossierPourItem(context: Context, nomItem: String, dossier: Uri) {
    val carte = lireDossiersParItem(context)
    carte[nomItem] = dossier.toString()
    ecrireDossiersParItem(context, carte)
}

fun oublierDossierPourItem(context: Context, nomItem: String) {
    val carte = lireDossiersParItem(context)
    carte.remove(nomItem)
    ecrireDossiersParItem(context, carte)
}

/** Vérifie réellement sur le disque si le fichier de cet item existe encore, dans le
 *  dossier où il a été installé la dernière fois (et non un simple statut mémorisé
 *  en RAM, qui reste faussement "installé" si le fichier a été supprimé ailleurs). */
fun verifierInstalle(context: Context, item: CatalogItem): Boolean {
    val dossierUri = dossierEnregistrePour(context, item.nom) ?: return false
    return try {
        DocumentFile.fromTreeUri(context, dossierUri)?.findFile(item.nom)?.exists() == true
    } catch (e: Exception) {
        false
    }
}

fun formaterTaille(octets: Long): String {
    if (octets <= 0) return "0 B"
    val unites = listOf("B", "KB", "MB", "GB")
    var valeur = octets.toDouble()
    var i = 0
    while (valeur >= 1024 && i < unites.size - 1) {
        valeur /= 1024
        i++
    }
    return "%.1f %s".format(valeur, unites[i])
}

fun formaterDuree(secondes: Long): String {
    if (secondes < 60) return "${secondes}s"
    val min = secondes / 60
    val sec = secondes % 60
    return "${min}m ${sec}s"
}

data class EtatTelechargement(
    val pourcentage: Int,
    val octetsTelecharges: Long,
    val octetsTotal: Long,
    val debutMillis: Long
)

// ---------- Images/descriptions automatiques ----------
/**
 * Extensions qui ne désignent jamais un jeu : documents, sauvegardes, réglages, images.
 *
 * Filtre de secours pour les catalogues où rien n'a été marqué à la main — la plupart
 * des cas gênants tiennent dans cette liste, et l'utilisateur n'a alors rien à régler.
 */
private val EXTENSIONS_ACCESSOIRES = setOf(
    "txt", "pdf", "md", "nfo", "doc", "docx", "rtf",
    "sav", "srm", "state", "ss0", "ss1", "cfg", "ini", "json", "xml", "log",
    "jpg", "jpeg", "png", "webp", "gif", "bmp",
    "mp3", "wav", "ogg", "mp4", "mkv", "avi"
)

fun estFichierAccessoire(nom: String): Boolean =
    nom.substringAfterLast('.', "").lowercase() in EXTENSIONS_ACCESSOIRES

data class MetadonneesAffichees(
    val image: String?,
    val description: String,
    val fiche: IGDBMetadataService.Metadonnees? = null,
    /** Même jaquette, composée en largeur : ce que réclament les tuiles larges. */
    val jaquetteLarge: String? = null,
    /** Bannière large, pour le fond de la fiche : une jaquette verticale étirée sur
     *  toute la largeur donne un résultat déformé et flou. */
    val banniere: String? = null,
    /** Titre détouré du jeu, qui remplace le titre écrit quand il existe. */
    val logo: String? = null
)

/**
 * Renvoie l'image et la description à afficher pour un item : celles du catalogue
 * si présentes, sinon celles auto-récupérées via IGDBMetadataService (une seule
 * fois par item grâce au cache interne du service).
 */
@Composable
fun rememberMetadonneesAffichees(item: CatalogItem): MetadonneesAffichees {
    var auto by remember(item.nom) { mutableStateOf<IGDBMetadataService.Metadonnees?>(null) }

    // Interrogé même quand le catalogue fournit image et description : l'année, le
    // genre, le studio et la note n'en viennent jamais, et c'est justement ce que la
    // fiche a de plus intéressant à montrer. Le service met en cache par titre.
    //
    // Sauf si l'entrée est marquée sans illustration côté PC, ou si son extension
    // annonce un document : chercher « notice.pdf » dans une base de jeux ne peut
    // produire qu'un résultat trompeur, jamais un bon.
    val chercherFiche = item.avecJaquette && !estFichierAccessoire(item.nom)

    var illustrations by remember(item.nom) { mutableStateOf<SteamGridDB.Illustrations?>(null) }

    LaunchedEffect(item.nom, item.categorie, chercherFiche) {
        if (chercherFiche) {
            // La catégorie, c'est le nom du dossier, donc la console : c'est elle qui
            // départage deux jeux au titre voisin sortis sur des machines différentes.
            auto = IGDBMetadataService.recuperer(item.nom, item.categorie)
            // Les deux bases sont interrogées : l'une sait dessiner, l'autre raconter.
            illustrations = SteamGridDB.recuperer(item.nom)
        }
    }

    val image = item.image?.let {
        // Adresse déjà complète (Google Drive) ou chemin relatif au serveur du PC.
        if (it.startsWith("http://") || it.startsWith("https://")) it else "${ServeurConfig.url}$it"
    } ?: illustrations?.jaquette ?: auto?.image
    val description = item.description.ifBlank { auto?.description ?: "" }
    return MetadonneesAffichees(
        image = image,
        description = description,
        fiche = auto,
        jaquetteLarge = illustrations?.jaquetteLarge,
        banniere = illustrations?.banniere,
        logo = illustrations?.logo
    )
}

// ---------- Bannière festonnée (motif décoratif générique, pas un logo précis) ----------
// Hauteur réduite (64dp au lieu de 90dp) + festons calculés selon la largeur réelle de
// l'écran pour ne jamais être coupés ni disproportionnés sur un écran large.
// Partagée avec les autres écrans (réglages, style) pour que tous les bandeaux
// fassent exactement la même hauteur.
val HauteurBanniere = 64.dp
private val TailleFestonCible = 26.dp

// ---------- Couleurs "courantes" utilisées dans toute l'appli ----------
// Ce sont des propriétés composables (pas des constantes) qui lisent la palette du thème
// actif via MaterialTheme.colorScheme : changer de thème dans les Réglages recolore donc
// automatiquement tout ce qui utilise ces noms, sans avoir à toucher chaque écran.
val RougeJeu: Color @Composable get() = MaterialTheme.colorScheme.primary
val BlancJeu: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
val BlancCreme: Color @Composable get() = MaterialTheme.colorScheme.surface
val FondClair: Color @Composable get() = MaterialTheme.colorScheme.background
val AccentJaune: Color @Composable get() = MaterialTheme.colorScheme.secondary
val TexteFonce: Color @Composable get() = MaterialTheme.colorScheme.onBackground

@Composable
fun BanniereFestonnee(titre: String, onOuvrirMenu: () -> Unit, onOuvrirReglages: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HauteurBanniere)
                    .background(RougeJeu)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOuvrirMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Category menu", tint = BlancJeu)
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.logo_moonshop_white),
                                contentDescription = null,
                                modifier = Modifier.height(40.dp)
                            )
                            Text(
                                titre,
                                color = BlancJeu,
                                fontSize = 24.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = PoliceMoonshop
                            )
                        }
                    }
                    IconButton(onClick = onOuvrirReglages) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = BlancJeu)
                    }
                }
            }
            BordureFestonnee()
        }
    }
}

/**
 * Bordure en demi-cercles (l'auvent de magasin), à poser sous n'importe quel bandeau.
 *
 * Extraite du bandeau principal pour être la même partout : c'est la signature visuelle
 * de Moonshop, elle n'a pas de raison de s'arrêter à l'écran d'accueil. Le nombre
 * d'arrondis est calculé d'après la largeur réelle, pour qu'aucun ne soit coupé.
 */
@Composable
fun BordureFestonnee(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val nombreArrondis = maxOf(4, (maxWidth / TailleFestonCible).roundToInt())
        // RougeJeu est une propriété @Composable : on la lit ici, pas dans le
        // lambda de dessin de Canvas qui, lui, n'est pas composable.
        val couleurFestons = RougeJeu
        Canvas(modifier = Modifier.fillMaxWidth().height(TailleFestonCible / 2)) {
            val largeurArrondi = size.width / nombreArrondis
            val rayon = largeurArrondi / 2f
            for (i in 0 until nombreArrondis) {
                val centreX = i * largeurArrondi + rayon
                drawArc(
                    color = couleurFestons,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(centreX - rayon, -rayon),
                    size = androidx.compose.ui.geometry.Size(rayon * 2f, rayon * 2f)
                )
            }
        }
    }
}

// ---------- Silhouettes gaming génériques dispersées en fond (fond blanc à motif) ----------
fun dessinerManette(offset: Offset, taille: Float, path: Path) {
    path.addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            offset.x, offset.y + taille * 0.25f,
            offset.x + taille, offset.y + taille * 0.75f,
            androidx.compose.ui.geometry.CornerRadius(taille * 0.3f)
        )
    )
}

fun dessinerEtoile(centre: Offset, rayon: Float): Path {
    val chemin = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) rayon else rayon * 0.45f
        val angle = Math.PI * i / points - Math.PI / 2
        val x = centre.x + (r * Math.cos(angle)).toFloat()
        val y = centre.y + (r * Math.sin(angle)).toFloat()
        if (i == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
    }
    chemin.close()
    return chemin
}

@Composable
fun FondSilhouettesGaming(modifier: Modifier = Modifier) {
    // Résolues ici (contexte de composition) car le lambda de Canvas est un DrawScope,
    // pas un contexte @Composable : impossible d'y lire directement MaterialTheme.colorScheme.
    val rouge = RougeJeu
    val accent = AccentJaune
    val blancCreme = BlancCreme
    Canvas(modifier = modifier.fillMaxSize()) {
        val aleatoire = Random(42) // graine fixe pour un motif stable
        val pas = 130f
        var y = 0f
        var ligne = 0
        while (y < size.height) {
            var x = if (ligne % 2 == 0) 0f else pas / 2
            while (x < size.width) {
                val forme = aleatoire.nextInt(4)
                val opacite = 0.07f
                when (forme) {
                    0 -> drawPath(dessinerEtoile(Offset(x, y), 16f), color = accent, alpha = opacite)
                    1 -> drawCircle(color = rouge, radius = 14f, center = Offset(x, y), alpha = opacite)
                    2 -> {
                        // pièce (cercle avec anneau)
                        drawCircle(color = accent, radius = 15f, center = Offset(x, y), alpha = opacite)
                        drawCircle(color = blancCreme, radius = 9f, center = Offset(x, y), alpha = opacite)
                    }
                    else -> {
                        // petit cœur pixel (deux cercles + triangle)
                        drawCircle(color = rouge, radius = 8f, center = Offset(x - 6f, y - 4f), alpha = opacite)
                        drawCircle(color = rouge, radius = 8f, center = Offset(x + 6f, y - 4f), alpha = opacite)
                        val cœur = Path().apply {
                            moveTo(x - 13f, y - 2f)
                            lineTo(x, y + 14f)
                            lineTo(x + 13f, y - 2f)
                            close()
                        }
                        drawPath(cœur, color = rouge, alpha = opacite)
                    }
                }
                x += pas
            }
            y += pas
            ligne++
        }
    }
}

// ---------- Voile + panneau de menu superposé sur TOUT l'écran (au-dessus du bandeau) ----------
@Composable
fun MenuOverlay(
    ouvert: Boolean,
    categories: List<String>,
    categorieSelectionnee: String?,
    comptesParCategorie: Map<String, Int>,
    totalItems: Int,
    totalInstalles: Int,
    onSelection: (String?) -> Unit,
    onFermer: () -> Unit
) {
    // Voile semi-transparent cliquable pour fermer, couvre tout l'écran
    AnimatedVisibility(
        visible = ouvert,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onFermer)
        )
    }

    // Panneau qui va du tout haut au tout bas de l'écran, par-dessus le bandeau rouge
    AnimatedVisibility(
        visible = ouvert,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .background(RougeJeu)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Consoles",
                    color = BlancJeu,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFermer, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close menu", tint = BlancJeu)
                }
            }
            Spacer(Modifier.height(8.dp))
            Divider(color = BlancJeu.copy(alpha = 0.25f))
            Spacer(Modifier.height(8.dp))

            ElementMenuCategorie(
                libelle = "All",
                compte = totalItems,
                selectionnee = categorieSelectionnee == null,
                onClic = { onSelection(null) }
            )
            ElementMenuCategorie(
                libelle = "Installed",
                compte = totalInstalles,
                selectionnee = categorieSelectionnee == CATEGORIE_INSTALLES,
                onClic = { onSelection(CATEGORIE_INSTALLES) }
            )
            Spacer(Modifier.height(4.dp))
            Divider(color = BlancJeu.copy(alpha = 0.15f))
            Spacer(Modifier.height(4.dp))
            categories.forEach { cat ->
                ElementMenuCategorie(
                    libelle = cat,
                    compte = comptesParCategorie[cat] ?: 0,
                    selectionnee = categorieSelectionnee == cat,
                    onClic = { onSelection(cat) }
                )
            }
        }
    }
}

@Composable
private fun ElementMenuCategorie(libelle: String, compte: Int, selectionnee: Boolean, onClic: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selectionnee) BlancJeu.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClic)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            libelle,
            color = if (selectionnee) AccentJaune else BlancJeu,
            fontWeight = if (selectionnee) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "$compte",
            color = if (selectionnee) AccentJaune else BlancJeu.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

// ---------- Écran de chargement initial (logo animé) ----------
// Étapes : 1) les deux pétales du logo (sans le mot "MOONSHOP"), blanches sur fond rouge
// (même rouge que la banderole du haut), se rapprochent et se rejoignent au centre —
// 2) le logo refermé tourne sur lui-même, en continu et sans à-coup, tant que le
// catalogue n'est pas chargé — 3) il s'ouvre : les deux pétales s'écartent verticalement
// et laissent apparaître "MOONSHOP" lettre par lettre de gauche à droite, dans l'espace
// ainsi ouvert. L'ouverture n'est déclenchée qu'une fois le catalogue réellement chargé :
// l'animation ne coupe jamais avant que les données soient prêtes, même si elle continue
// de tourner en attendant.
private const val NomMoonshop = "MOONSHOP"
private val LargeurLogoDemarrage = 96.dp
// Ratios calculés à partir des deux pétales détourées de logo_moonshop_white.png
// (195×126 pour le haut, 195×106 pour le bas) pour garder les bonnes proportions.
private val HauteurPetaleHaut = LargeurLogoDemarrage * (126f / 195f)
private val HauteurPetaleBas = LargeurLogoDemarrage * (106f / 195f)

@Composable
fun EcranChargementInitial(pret: Boolean, onTermine: () -> Unit) {
    val progressionArcs = remember { Animatable(0f) }   // 0 = pétales écartées, 1 = logo refermé
    val rotation = remember { Animatable(0f) }          // valeur cumulative (jamais remise à 0 : pas de saut visuel)
    val ouverture = remember { Animatable(0f) }         // 0 = logo fermé, 1 = totalement ouvert + nom révélé
    val pretActuel = rememberUpdatedState(pret)
    val densite = LocalDensity.current

    LaunchedEffect(Unit) {
        // Arpège d'ouverture, calé sur le rapprochement des deux pétales.
        SoundEffects.chargementDebut()
        // 1) les deux pétales se rapprochent avec un léger rebond (ressort plutôt que
        // courbe figée : ça casse l'effet "mécanique" d'une interpolation linéaire).
        progressionArcs.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        )
        // 2) le logo refermé tourne en continu, sans jamais revenir brutalement à 0°
        // (chaque tour vise "valeur actuelle + 360°" : la rotation reste donc fluide
        // même si elle boucle plusieurs fois en attendant le chargement). Rythme plus
        // rapide et légèrement accéléré-décéléré à chaque tour pour éviter l'effet
        // "aiguille d'horloge" d'une vitesse parfaitement constante.
        var cible = 360f
        rotation.animateTo(cible, tween(800, easing = FastOutSlowInEasing))
        while (!pretActuel.value) {
            cible += 360f
            rotation.animateTo(cible, tween(950, easing = FastOutSlowInEasing))
        }
        // 3) le logo s'ouvre avec un petit rebond et révèle le nom
        SoundEffects.chargementFin()
        ouverture.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        delay(350)
        onTermine()
    }

    Box(modifier = Modifier.fillMaxSize().background(RougeJeu), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(190.dp), contentAlignment = Alignment.Center) {

            // Écart des pétales par rapport au centre : grand au tout début (pétales
            // séparées), nul une fois le logo refermé, puis à nouveau grand pendant
            // l'ouverture finale. Les deux phases ne se chevauchant jamais dans le temps,
            // une seule variable suffit à piloter les deux mouvements symétriques.
            val ecart = if (ouverture.value > 0f) ouverture.value else (1f - progressionArcs.value)
            val decalagePx = with(densite) { 100.dp.toPx() } * ecart

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { rotationZ = rotation.value }
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.logo_arc_haut),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(BlancJeu),
                    modifier = Modifier
                        .width(LargeurLogoDemarrage)
                        .height(HauteurPetaleHaut)
                        .graphicsLayer { translationY = -decalagePx }
                )
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.logo_arc_bas),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(BlancJeu),
                    modifier = Modifier
                        .width(LargeurLogoDemarrage)
                        .height(HauteurPetaleBas)
                        .graphicsLayer { translationY = decalagePx }
                )
            }

            // Le nom apparaît en blanc (lisible sur le fond rouge), lettre par lettre de
            // gauche à droite, dans l'espace que les deux pétales laissent en s'écartant.
            Row {
                NomMoonshop.forEachIndexed { indice, lettre ->
                    val seuil = indice / NomMoonshop.length.toFloat()
                    AnimatedVisibility(
                        visible = ouverture.value > seuil,
                        enter = fadeIn(tween(120)) + slideInHorizontally(
                            initialOffsetX = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh
                            )
                        )
                    ) {
                        Text(
                            lettre.toString(),
                            color = BlancJeu,
                            fontSize = 26.sp,
                            fontFamily = PoliceMoonshop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShopScreen(themeActuel: ThemeOption, onChangerTheme: (ThemeOption) -> Unit) {
    val context = LocalContext.current
    // Recréé quand l'adresse du serveur change (réglages) : le téléchargement suivant
    // part alors de la nouvelle adresse sans avoir à redémarrer l'appli.
    val installer = remember(ServeurConfig.url) { FileInstaller(ServeurConfig.url, context) }
    val scope = rememberCoroutineScope()

    var dossierParDefaut by remember { mutableStateOf(lireDossierParDefaut(context)) }
    var reglagesMusique by remember { mutableStateOf(lireReglagesMusique(context)) }
    var catalogue by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    // Démarre à true : le chargement du catalogue est lancé dès l'ouverture de l'appli
    // (voir LaunchedEffect plus bas), l'écran de démarrage doit donc le savoir tout de suite.
    var chargement by remember { mutableStateOf(true) }
    var erreur by remember { mutableStateOf<String?>(null) }
    // Tant que false, l'écran de chargement animé (logo) reste affiché par-dessus tout le reste.
    var demarrageTermine by remember { mutableStateOf(false) }

    val etats = remember { mutableStateMapOf<String, EtatTelechargement?>() }
    val installes = remember { mutableStateMapOf<String, Boolean>() }
    val jobsTelechargement = remember { mutableStateMapOf<String, Job>() }
    var itemEnAttenteDeDossier by remember { mutableStateOf<CatalogItem?>(null) }

    var menuOuvert by remember { mutableStateOf(false) }
    var reglagesOuverts by remember { mutableStateOf(false) }
    var sonsActifs by remember { mutableStateOf(lireSonsActifs(context)) }
    var themesPersonnalises by remember { mutableStateOf(lireThemesPersonnalises(context)) }
    var styleOuvert by remember { mutableStateOf(false) }
    var motifFond by remember { mutableStateOf(lireMotifFond(context)) }
    var cadeauEnRelief by remember { mutableStateOf(lireCadeau3D(context)) }
    var fondEcran by remember { mutableStateOf(lireFondEcran(context)) }
    var messageFond by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(lireSource(context)) }
    var reglagesDrive by remember { mutableStateOf(lireReglagesDrive(context)) }
    var volumeSons by remember { mutableStateOf(lireVolumeSons(context)) }
    var codeConsole by remember { mutableStateOf(lireCodeConsole(context)) }
    var etatConnexion by remember {
        mutableStateOf(if (lireCodeConsole(context).isBlank()) EtatConnexion.AUCUN else EtatConnexion.RECHERCHE)
    }
    var messageConnexion by remember { mutableStateOf("") }
    var connexionDirecte by remember { mutableStateOf(false) }
    // Incrémenté par le bouton « Connect » : permet de relancer la résolution même
    // quand le code n'a pas changé (typiquement, le PC vient d'être allumé).
    var tentativeConnexion by remember { mutableStateOf(0) }
    var volumeAffiche by remember(reglagesMusique.volume) { mutableStateOf(reglagesMusique.volume) }
    // Jeu dont l'installation vient de se terminer : déclenche l'animation de cadeau
    // plein écran, remis à null quand elle est finie (ou touchée par l'utilisateur).
    var jeuFraichementInstalle by remember { mutableStateOf<CatalogItem?>(null) }
    var categorieSelectionnee by remember { mutableStateOf<String?>(null) } // null = toutes
    var itemDetail by remember { mutableStateOf<CatalogItem?>(null) }
    var disposition by remember { mutableStateOf(lireDisposition(context)) }
    var cles by remember { mutableStateOf(lireClesApi(context)) }
    var assistantTermine by remember { mutableStateOf(lireAssistantTermine(context)) }

    // Les services gardent leurs identifiants en mémoire vive : ils les reçoivent une
    // fois au lancement, puis à chaque modification.
    remember { appliquerClesApi(lireClesApi(context)) }
    var dispositionOuverte by remember { mutableStateOf(false) }
    var clesOuvertes by remember { mutableStateOf(false) }
    // Jeu visé par le sélecteur d'image de la galerie : le résultat revient de façon
    // asynchrone, longtemps après que le menu ait été refermé.
    var jeuPourImage by remember { mutableStateOf<CatalogItem?>(null) }
    // Jeu en cours de recadrage, avec la proportion de la tuile d'où il a été ouvert.
    var recadrage by remember { mutableStateOf<Pair<CatalogItem, Float>?>(null) }

    // Lus pendant la composition, et non dans un effet lancé après : une tuile dessinée
    // avant le chargement afficherait un instant l'illustration automatique à la place
    // de celle choisie par l'utilisateur.
    remember { Personnalisations.charger(context) }

    fun rafraichirEtatInstalle(item: CatalogItem) {
        installes[item.nom] = verifierInstalle(context, item)
    }

    fun rafraichirTousLesEtatsInstalles(liste: List<CatalogItem>) {
        liste.forEach { rafraichirEtatInstalle(it) }
    }

    fun installerVers(item: CatalogItem, dossier: Uri) {
        val debut = System.currentTimeMillis()
        etats[item.nom] = EtatTelechargement(0, 0, 0, debut)
        val job = scope.launch {
            try {
                val resultat = installer.installer(item, dossier) { pct, telecharges, total ->
                    etats[item.nom] = EtatTelechargement(pct, telecharges, total, debut)
                }
                if (resultat.isSuccess) {
                    enregistrerDossierPourItem(context, item.nom, dossier)
                    installes[item.nom] = true
                    jeuFraichementInstalle = item
                } else {
                    erreur = "${item.nom} failed: ${resultat.exceptionOrNull()?.message}"
                }
            } catch (e: CancellationException) {
                // Téléchargement annulé par l'utilisateur : rien à signaler comme erreur.
            } finally {
                etats[item.nom] = null
                jobsTelechargement.remove(item.nom)
            }
        }
        jobsTelechargement[item.nom] = job
    }

    fun annulerTelechargement(item: CatalogItem) {
        jobsTelechargement[item.nom]?.cancel()
    }

    fun desinstaller(item: CatalogItem) {
        val dossierUri = dossierEnregistrePour(context, item.nom)
        if (dossierUri != null) {
            try {
                DocumentFile.fromTreeUri(context, dossierUri)?.findFile(item.nom)?.delete()
            } catch (e: Exception) {
                erreur = "Could not uninstall ${item.nom}: ${e.message}"
            }
        }
        oublierDossierPourItem(context, item.nom)
        installes[item.nom] = false
    }

    val lanceurDossierParDefaut = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            ecrireDossierParDefaut(context, uri)
            dossierParDefaut = uri
        }
    }

    val lanceurDossierPonctuel = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val item = itemEnAttenteDeDossier
        if (uri != null && item != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            installerVers(item, uri)
        }
        itemEnAttenteDeDossier = null
    }

    val lanceurImageFond = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Permission persistante : sans elle, l'image choisie deviendrait
            // illisible au prochain démarrage de l'appli.
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
            }
            fondEcran = fondEcran.copy(uri = uri)
            ecrireFondEcran(context, fondEcran)
            messageFond = ""
        }
    }

    val lanceurImageJeu = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val jeu = jeuPourImage
        jeuPourImage = null
        if (uri != null && jeu != null) {
            // Sans permission persistante, l'image choisie serait illisible au
            // prochain démarrage : la tuile retomberait sur l'illustration automatique.
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
            }
            // Le cadrage précédent portait sur l'ancienne image : le garder décalerait
            // la nouvelle sans raison.
            Personnalisations.definir(context, jeu.nom, CadrageJeu(uriImage = uri.toString()))
        }
    }

    val lanceurFichierMusique = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val nom = nomAffichableFichier(context, uri)
            ecrireMusiqueChoisie(context, uri, nom)
            ecrireMusiqueActive(context, true)
            reglagesMusique = ReglagesMusique(uri, nom, true, reglagesMusique.volume)
        }
    }

    // Démarre/arrête/ajuste la musique de fond dès que le réglage change, et à chaque
    // fois que l'écran principal recompose (ex: retour depuis un autre écran) tant
    // qu'elle est censée être active — pour qu'elle continue en continu sans coupure.
    // Elle n'est lancée qu'une fois l'écran de démarrage terminé : pendant l'animation
    // du logo, seuls les bruitages de chargement se font entendre.
    LaunchedEffect(demarrageTermine, reglagesMusique.active, reglagesMusique.uri, reglagesMusique.volume) {
        val uri = reglagesMusique.uri
        if (demarrageTermine && reglagesMusique.active && uri != null) {
            MusicManager.demarrer(context, uri, reglagesMusique.volume)
        } else {
            MusicManager.arreter()
        }
    }

    fun actualiser() {
        chargement = true
        erreur = null
        scope.launch {
            try {
                catalogue = if (source == SourceCatalogue.DRIVE) {
                    SourceDrive.catalogue(reglagesDrive.lien, reglagesDrive.cleApi)
                } else {
                    installer.recupererCatalogue()
                }
                rafraichirTousLesEtatsInstalles(catalogue)
            } catch (e: Appairage.NonAppaire) {
                // Autorisation révoquée depuis le PC : on oublie le jeton devenu
                // inutile et on relance la demande, plutôt que de laisser l'écran
                // sur un catalogue vide.
                //
                // Uniquement si un jeton existait : sans cette condition, un PC qui
                // refuserait un jeton qu'il vient pourtant d'émettre ferait tourner
                // en boucle demande et rechargement.
                if (Appairage.jeton.isNotBlank()) {
                    Appairage.oublier(context)
                    erreur = null
                    tentativeConnexion++
                } else {
                    erreur = "This console is not approved on that PC yet."
                }
            } catch (e: SourceDrive.ErreurDrive) {
                // Message déjà écrit pour être lu par un humain : le répéter derrière
                // « Could not load the catalogue » ne ferait que le noyer.
                erreur = e.message
            } catch (e: Exception) {
                erreur = "Could not load the catalogue: ${e.message}"
            } finally {
                chargement = false
            }
        }
    }

    // Relancé aussi quand l'adresse du serveur change : le catalogue est alors rechargé
    // depuis le nouveau serveur, avec l'installer correspondant.
    LaunchedEffect(ServeurConfig.url, source, reglagesDrive) { actualiser() }

    // Un code mémorisé est re-résolu à chaque ouverture : l'adresse du PC a pu changer
    // entre-temps (nouveau tunnel à chaque partage), c'est tout l'intérêt du code.
    LaunchedEffect(codeConsole, tentativeConnexion, source) {
        // En source Drive, interroger l'annuaire n'aurait aucun sens : aucun PC ne
        // partage, et l'écran afficherait « PC offline » sans rapport avec la situation.
        if (source == SourceCatalogue.DRIVE || codeConsole.isBlank()) {
            etatConnexion = EtatConnexion.AUCUN
            return@LaunchedEffect
        }
        etatConnexion = EtatConnexion.RECHERCHE
        messageConnexion = ""
        when (val resultat = Annuaire.resoudre(codeConsole)) {
            is ResultatAnnuaire.Trouve -> {
                // Le PC est peut-être sur le même wifi : on privilégie alors la
                // liaison directe, bien plus rapide que le détour par Cloudflare.
                val (adresse, directe) = Annuaire.choisirAdresse(resultat)
                connexionDirecte = directe

                // Adresse vide : le PC partage, mais sans tunnel, donc uniquement chez
                // lui — et cette console n'est pas sur son réseau.
                if (adresse.isBlank()) {
                    etatConnexion = EtatConnexion.HORS_LIGNE
                    messageConnexion = "This PC only shares on its own network. " +
                        "Join its wifi, or turn its Cloudflare tunnel back on."
                    return@LaunchedEffect
                }

                // Trouver le PC ne suffit plus : il faut qu'il reconnaisse cette
                // console. Sans autorisation, le catalogue reviendrait vide avec un
                // message d'erreur incompréhensible.
                Appairage.charger(context, codeConsole)
                val accord = Appairage.assurer(context, adresse, codeConsole) {
                    etatConnexion = EtatConnexion.APPAIRAGE
                    messageConnexion = "Approve this console on your PC to continue."
                }
                when (accord) {
                    is Appairage.Resultat.Accepte -> {
                        etatConnexion = EtatConnexion.CONNECTE
                        messageConnexion = ""
                        if (adresse != ServeurConfig.url) {
                            ServeurConfig.url = adresse
                            ecrireUrlServeur(context, adresse)
                        } else {
                            // Adresse inchangée : l'effet de rechargement ne se
                            // déclenchera pas tout seul, or le catalogue attendait
                            // justement cette autorisation.
                            actualiser()
                        }
                    }
                    is Appairage.Resultat.Refuse -> {
                        etatConnexion = EtatConnexion.REFUSE
                        messageConnexion = "Access denied on the PC."
                    }
                    is Appairage.Resultat.Echec -> {
                        etatConnexion = EtatConnexion.ERREUR
                        messageConnexion = accord.message
                    }
                }
            }
            is ResultatAnnuaire.HorsLigne -> {
                etatConnexion = EtatConnexion.HORS_LIGNE
                messageConnexion = "No PC is sharing with this code right now."
            }
            is ResultatAnnuaire.Injoignable -> {
                etatConnexion = EtatConnexion.ERREUR
                messageConnexion = resultat.message
            }
        }
    }

    // Ferme le menu avec le bouton retour du téléphone plutôt que de quitter l'appli
    BackHandler(enabled = menuOuvert) { SoundEffects.menuFermer(); menuOuvert = false }
    BackHandler(enabled = reglagesOuverts) { SoundEffects.menuFermer(); reglagesOuverts = false }

    val categories = remember(catalogue) { catalogue.map { it.categorie }.distinct().sorted() }
    val comptesParCategorie = remember(catalogue) { catalogue.groupingBy { it.categorie }.eachCount() }
    val totalInstalles = installes.values.count { it }
    val itemsAffiches = when (categorieSelectionnee) {
        null -> catalogue
        CATEGORIE_INSTALLES -> catalogue.filter { installes[it.nom] == true }
        else -> catalogue.filter { it.categorie == categorieSelectionnee }
    }

    LaunchedEffect(itemDetail) {
        itemDetail?.let { rafraichirEtatInstalle(it) }
    }

    // Sur une manette, B est le bouton « revenir » : il est renvoyé au répartiteur du
    // système, ce qui lui fait suivre exactement les mêmes règles que le retour Android
    // — fermer le menu, puis les réglages, puis la fiche — sans les redire ici.
    val repartiteurRetour = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FondClair)
            .onPreviewKeyEvent { evenement ->
                if (evenement.type == KeyEventType.KeyDown && evenement.key == Key.ButtonB) {
                    SoundEffects.menuFermer()
                    repartiteurRetour?.onBackPressed()
                    true
                } else {
                    false
                }
            }
    ) {
        // Image de fond en premier, voilée par un aplat du fond du thème : sans ce
        // voile, une photo claire rendrait les titres de jeux illisibles.
        fondEcran.uri?.let { image ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(image).crossfade(200).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FondClair.copy(alpha = fondEcran.voile))
            )
        }
        if (motifFond) FondSilhouettesGaming()

        // Crossfade plutôt qu'un simple if/else : le passage liste <-> détail se fait
        // en fondu au lieu d'un changement brutal d'un écran à l'autre.
        Crossfade(targetState = itemDetail, animationSpec = tween(320), label = "ecranPrincipal") { detail ->
            if (detail != null) {
                EcranDetail(
                    item = detail,
                    etat = etats[detail.nom],
                    estInstalle = installes[detail.nom] == true,
                    dossierParDefaut = dossierParDefaut,
                    dossierInstalle = dossierEnregistrePour(context, detail.nom),
                    onRetour = {
                        rafraichirEtatInstalle(detail)
                        itemDetail = null
                    },
                    onInstaller = { d -> installerVers(detail, d) },
                    onChoisirDossier = {
                        itemEnAttenteDeDossier = detail
                        lanceurDossierPonctuel.launch(null)
                    },
                    onAnnuler = { annulerTelechargement(detail) },
                    onDesinstaller = { desinstaller(detail) }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    BanniereFestonnee(
                        titre = "MOONSHOP",
                        onOuvrirMenu = {
                            menuOuvert = !menuOuvert
                            if (menuOuvert) SoundEffects.menuOuvrir() else SoundEffects.menuFermer()
                        },
                        onOuvrirReglages = {
                            SoundEffects.menuOuvrir()
                            reglagesOuverts = true
                        }
                    )

                    // La bibliothèque prend toute la place sous le bandeau : mosaïque,
                    // recherche et filtres y sont réunis.
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        EcranBibliotheque(
                            catalogue = itemsAffiches,
                            installes = installes,
                            disposition = disposition,
                            chargement = chargement,
                            erreur = erreur,
                            onActualiser = { actualiser() },
                            onOuvrirJeu = { jeu -> itemDetail = jeu },
                            onChoisirImageLocale = { jeu ->
                                jeuPourImage = jeu
                                lanceurImageJeu.launch(arrayOf("image/*"))
                            },
                            onRecadrer = { jeu, rapport -> recadrage = jeu to rapport }
                        )
                    }
                }
            }
        }

        // Le menu est déclaré en dernier dans ce Box : il se dessine par-dessus
        // le bandeau ET la liste, et couvre toute la hauteur de l'écran.
        MenuOverlay(
            ouvert = menuOuvert,
            categories = categories,
            categorieSelectionnee = categorieSelectionnee,
            comptesParCategorie = comptesParCategorie,
            totalItems = catalogue.size,
            totalInstalles = totalInstalles,
            onSelection = { cat ->
                SoundEffects.clic()
                categorieSelectionnee = cat
                menuOuvert = false
            },
            onFermer = { SoundEffects.menuFermer(); menuOuvert = false }
        )

        // Écran de réglages en plein écran (glisse depuis la droite), dessiné en dernier
        // pour passer au-dessus de tout, y compris le menu des catégories.
        AnimatedVisibility(
            visible = reglagesOuverts,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200))
        ) {
            EcranReglages(
                dossierParDefaut = dossierParDefaut,
                onChoisirDossier = { lanceurDossierParDefaut.launch(null) },
                onOuvrirStyle = { styleOuvert = true },
                onOuvrirDisposition = { dispositionOuverte = true },
                onOuvrirCles = { clesOuvertes = true },
                cles = cles,
                onChangerCles = { nouvelles ->
                    cles = nouvelles
                    ecrireClesApi(context, nouvelles)
                    appliquerClesApi(nouvelles)
                },
                reglagesMusique = reglagesMusique,
                onToggleMusique = { active ->
                    if (reglagesMusique.uri != null) {
                        ecrireMusiqueActive(context, active)
                        reglagesMusique = reglagesMusique.copy(active = active)
                    }
                },
                onChoisirMusique = {
                    lanceurFichierMusique.launch(arrayOf("audio/*", "video/mp4"))
                },
                onSupprimerMusique = {
                    effacerMusiqueChoisie(context)
                    reglagesMusique = ReglagesMusique(null, null, false, reglagesMusique.volume)
                },
                volumeAffiche = volumeAffiche,
                onVolumeChange = { v ->
                    volumeAffiche = v
                    MusicManager.definirVolume(v)
                },
                onVolumeFinalise = { v ->
                    ecrireVolumeMusique(context, v)
                    reglagesMusique = reglagesMusique.copy(volume = v)
                },
                source = source,
                onChangerSource = { choix ->
                    source = choix
                    ecrireSource(context, choix)
                },
                reglagesDrive = reglagesDrive,
                onChangerDrive = { nouveaux ->
                    reglagesDrive = nouveaux
                    ecrireReglagesDrive(context, nouveaux)
                },
                codeConsole = codeConsole,
                etatConnexion = etatConnexion,
                messageConnexion = messageConnexion,
                connexionDirecte = connexionDirecte,
                onConnecter = { saisie ->
                    val propre = Annuaire.nettoyerCode(saisie)
                    ecrireCodeConsole(context, propre)
                    codeConsole = propre
                    // Force une nouvelle résolution même si le code est identique :
                    // le PC vient peut-être tout juste d'être allumé.
                    tentativeConnexion++
                },
                sonsActifs = sonsActifs,
                onToggleSons = { actifs ->
                    sonsActifs = actifs
                    SoundEffects.actifs = actifs
                    ecrireSonsActifs(context, actifs)
                    // Joué après activation : donne tout de suite un aperçu du bruitage.
                    if (actifs) SoundEffects.clic()
                },
                volumeSons = volumeSons,
                onVolumeSonsChange = { v ->
                    volumeSons = v
                    SoundEffects.volume = v
                },
                onVolumeSonsFinalise = { v ->
                    ecrireVolumeSons(context, v)
                    // Aperçu immédiat du niveau choisi, une fois le doigt relevé.
                    SoundEffects.clic()
                },
                onRetour = { SoundEffects.menuFermer(); reglagesOuverts = false }
            )
        }
    }

    // Écran d'apparence, au-dessus des réglages d'où on l'ouvre.
    AnimatedVisibility(
        visible = styleOuvert,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200))
    ) {
        EcranStyle(
            themes = THEMES + themesPersonnalises,
            themeActuel = themeActuel,
            onChoisirTheme = onChangerTheme,
            onCreerTheme = { nouveau ->
                themesPersonnalises = themesPersonnalises + nouveau
                ecrireThemesPersonnalises(context, themesPersonnalises)
                onChangerTheme(nouveau)
            },
            onSupprimerTheme = { vise ->
                themesPersonnalises = themesPersonnalises.filterNot { it.id == vise.id }
                ecrireThemesPersonnalises(context, themesPersonnalises)
                // Le thème supprimé était peut-être celui en cours : on retombe
                // alors sur celui d'origine plutôt que sur une palette fantôme.
                if (themeActuel.id == vise.id) onChangerTheme(THEME_PAR_DEFAUT)
            },
            motifActif = motifFond,
            onToggleMotif = { actif ->
                motifFond = actif
                ecrireMotifFond(context, actif)
            },
            cadeauEnRelief = cadeauEnRelief,
            onToggleCadeau = { enRelief ->
                cadeauEnRelief = enRelief
                ecrireCadeau3D(context, enRelief)
            },
            fondEcran = fondEcran,
            onChoisirImage = { lanceurImageFond.launch(arrayOf("image/*")) },
            onEffacerFond = {
                effacerFondEcran(context)
                fondEcran = fondEcran.copy(uri = null)
                messageFond = ""
            },
            onVoileChange = { valeur -> fondEcran = fondEcran.copy(voile = valeur) },
            onVoileFinalise = { valeur ->
                ecrireFondEcran(context, fondEcran.copy(voile = valeur))
            },
            messageFond = messageFond,
            onRetour = { styleOuvert = false }
        )
    }

    // Services et clés, ouverts depuis les réglages.
    AnimatedVisibility(
        visible = clesOuvertes,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200))
    ) {
        EcranClesApi(
            codeConsole = codeConsole,
            onCodeConsole = { saisie ->
                val propre = Annuaire.nettoyerCode(saisie)
                ecrireCodeConsole(context, propre)
                codeConsole = propre
                tentativeConnexion++
            },
            reglagesDrive = reglagesDrive,
            onDrive = { nouveaux ->
                reglagesDrive = nouveaux
                ecrireReglagesDrive(context, nouveaux)
            },
            cles = cles,
            onCles = { nouvelles ->
                cles = nouvelles
                ecrireClesApi(context, nouvelles)
                appliquerClesApi(nouvelles)
            },
            onRetour = { clesOuvertes = false }
        )
    }

    // Choix de la disposition, ouvert depuis les réglages.
    AnimatedVisibility(
        visible = dispositionOuverte,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200))
    ) {
        EcranDisposition(
            dispositionActuelle = disposition,
            onChoisir = { choix ->
                disposition = choix
                ecrireDisposition(context, choix)
            },
            onRetour = { dispositionOuverte = false }
        )
    }

    // Recadrage manuel, ouvert par un appui long sur un jeu. Déclaré tout en haut de la
    // pile pour passer au-dessus de la bibliothèque comme des réglages.
    recadrage?.let { (jeu, rapport) ->
        EcranRecadrage(
            item = jeu,
            rapport = rapport,
            onValider = { cadre ->
                Personnalisations.definir(context, jeu.nom, cadre)
                recadrage = null
            },
            onAnnuler = { recadrage = null }
        )
    }

    // Assistant de première ouverture : posé après l'écran de chargement pour ne pas
    // apparaître pendant l'animation de démarrage, et avant tout le reste puisque rien
    // ne fonctionne tant que la console ne sait pas où chercher les jeux.
    if (demarrageTermine && !assistantTermine) {
        EcranAssistant(
            codeConsole = codeConsole,
            onCodeConsole = { saisie ->
                val propre = Annuaire.nettoyerCode(saisie)
                ecrireCodeConsole(context, propre)
                codeConsole = propre
                tentativeConnexion++
            },
            reglagesDrive = reglagesDrive,
            onDrive = { nouveaux ->
                reglagesDrive = nouveaux
                ecrireReglagesDrive(context, nouveaux)
            },
            cles = cles,
            onCles = { nouvelles ->
                cles = nouvelles
                ecrireClesApi(context, nouvelles)
                appliquerClesApi(nouvelles)
            },
            onTerminer = {
                assistantTermine = true
                ecrireAssistantTermine(context, true)
            }
        )
    }

    // Écran de chargement animé, par-dessus tout le reste, tant que le catalogue
    // (premier chargement) n'est pas prêt et que l'animation n'est pas terminée.
    // AnimatedVisibility + fadeOut plutôt qu'un if brutal : l'écran de démarrage
    // s'efface en fondu au lieu de disparaître d'un coup, révélant l'appli en dessous.
    AnimatedVisibility(
        visible = !demarrageTermine,
        exit = fadeOut(tween(500))
    ) {
        EcranChargementInitial(
            pret = !chargement,
            onTermine = { demarrageTermine = true }
        )
    }

    // Célébration plein écran quand un jeu vient de finir de s'installer. Déclarée en
    // tout dernier : elle passe au-dessus de l'écran de détail, du menu et des réglages.
    jeuFraichementInstalle?.let { jeu ->
        EcranCadeauInstalle(
            item = jeu,
            onTermine = { jeuFraichementInstalle = null }
        )
    }
}

// ---------- Écran détail : image à gauche (taille plafonnée, propre), description + bouton à droite ----------
private val LargeurMaxImageDetail = 190.dp

@Composable
fun EcranDetail(
    item: CatalogItem,
    etat: EtatTelechargement?,
    estInstalle: Boolean,
    dossierParDefaut: Uri?,
    dossierInstalle: Uri?,
    onRetour: () -> Unit,
    onInstaller: (Uri) -> Unit,
    onChoisirDossier: () -> Unit,
    onAnnuler: () -> Unit,
    onDesinstaller: () -> Unit
) {
    var erreurLocale by remember { mutableStateOf<String?>(null) }
    // Action en attente de confirmation utilisateur : "maj" (mise à jour, avec le dossier
    // cible déjà résolu) ou "suppr" (désinstallation). Null = aucune confirmation en cours.
    var actionAConfirmer by remember { mutableStateOf<Pair<String, Uri?>?>(null) }
    val meta = rememberMetadonneesAffichees(item)
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(FondClair)) {

        // ---------- Jaquette en fond, floutée et voilée ----------
        // La même image sert de décor et d'illustration : c'est elle qui donne son
        // identité à la page, et chaque jeu se reconnaît d'un coup d'œil.
        // La bannière est faite pour ça : format large, composition pensée pour un
        // fond. À défaut, la jaquette verticale, moins flatteuse une fois étirée.
        val fondHeros = meta.banniere ?: meta.image
        if (fondHeros != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(fondHeros).crossfade(300).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(if (meta.banniere != null) 14.dp else 26.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(RougeJeu, AccentJaune)))
            )
        }
        // Voile en dégradé : sombre en haut pour le titre, opaque en bas pour que le
        // texte de la fiche reste lisible quelle que soit la jaquette.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            FondClair.copy(alpha = 0.88f),
                            FondClair.copy(alpha = 0.97f)
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ---------- Entête : retour + nom du fichier ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton posé sur l'image plutôt que sur un bandeau plein : le décor
                // n'est pas coupé en deux par une barre.
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { SoundEffects.menuFermer(); onRetour() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    item.nom,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (estInstalle) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentJaune,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Installed", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.Top
            ) {
                // ---------- Jaquette nette, posée sur le fond flouté ----------
                Card(
                    modifier = Modifier
                        .widthIn(max = LargeurMaxImageDetail)
                        .aspectRatio(0.74f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BlancCreme),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    if (meta.image != null) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context).data(meta.image).crossfade(300).build(),
                            contentDescription = item.nom,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        color = RougeJeu,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            },
                            error = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.SportsEsports,
                                        contentDescription = null,
                                        tint = RougeJeu,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = RougeJeu,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Le logo officiel du jeu remplace le titre écrit quand il existe :
                    // c'est le lettrage que le joueur reconnaît. Sinon, le nom du fichier
                    // débarrassé de son extension — celle-ci reste lisible en entête.
                    if (meta.logo != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(meta.logo).crossfade(300).build(),
                            contentDescription = item.nom,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 76.dp)
                        )
                    } else {
                        Text(
                            item.nom.substringBeforeLast('.'),
                            color = TexteFonce,
                            fontFamily = PoliceMoonshop,
                            fontSize = 30.sp,
                            lineHeight = 32.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    EtiquettesJeu(item.categorie, meta.fiche)

                    if (meta.description.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            meta.description,
                            color = TexteFonce.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }

                    meta.fiche?.let { fiche ->
                        val credits = listOfNotNull(
                            fiche.studio?.let { "Studio  $it" },
                            fiche.editeur?.takeIf { it != fiche.studio }?.let { "Publisher  $it" }
                        )
                        if (credits.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            credits.forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TexteFonce.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ---------- Barre d'action, toujours visible ----------
            // Hors de la zone défilante : le bouton d'installation n'a jamais à être
            // cherché, quelle que soit la longueur de la description.
            Surface(
                color = BlancCreme,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    when {
                        etat != null -> {
                            val tailleConnue = etat.octetsTotal > 0
                            val secondesEcoulees = (System.currentTimeMillis() - etat.debutMillis) / 1000.0
                            val vitesse = if (secondesEcoulees > 0.5) etat.octetsTelecharges / secondesEcoulees else 0.0
                            val texteVitesse = if (vitesse > 0) "${formaterTaille(vitesse.roundToInt().toLong())}/s" else "calculating…"
                            val texteRestant = when {
                                vitesse > 0 && tailleConnue -> {
                                    val restants = etat.octetsTotal - etat.octetsTelecharges
                                    "≈ ${formaterDuree((restants / vitesse).roundToInt().toLong())} left"
                                }
                                tailleConnue -> "estimating…"
                                else -> "unknown total size"
                            }
                            val texteProgression = if (tailleConnue) "${etat.pourcentage} %" else formaterTaille(etat.octetsTelecharges)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$texteProgression · $texteVitesse · $texteRestant",
                                    color = TexteFonce,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { SoundEffects.clic(); onAnnuler() }) {
                                    Text("Cancel", color = RougeJeu)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            if (tailleConnue) {
                                LinearProgressIndicator(
                                    progress = { etat.pourcentage / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = RougeJeu
                                )
                            } else {
                                // Taille totale inconnue (serveur sans Content-Length) : barre
                                // animée plutôt qu'une barre figée à 0 %, qui donnerait
                                // l'impression que rien ne se passe.
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = RougeJeu
                                )
                            }
                        }

                        estInstalle -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        SoundEffects.clic()
                                        val dossier = dossierInstalle ?: dossierParDefaut
                                        if (dossier != null) actionAConfirmer = "maj" to dossier
                                        else erreurLocale = "Pick a default folder in the settings first, or use the folder button"
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RougeJeu),
                                    border = BorderStroke(1.5.dp, RougeJeu)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Update", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                IconButton(
                                    onClick = { SoundEffects.clic(); actionAConfirmer = "suppr" to null },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = RougeJeu)
                                }
                            }
                        }

                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        SoundEffects.clic()
                                        val dossier = dossierParDefaut
                                        if (dossier != null) onInstaller(dossier)
                                        else erreurLocale = "Pick a default folder in the settings first, or use the folder button"
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RougeJeu)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = BlancJeu)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "INSTALL",
                                        color = BlancJeu,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                IconButton(
                                    onClick = { SoundEffects.clic(); onChoisirDossier() },
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = "Choose a folder for this file",
                                        tint = RougeJeu
                                    )
                                }
                            }
                        }
                    }
                    erreurLocale?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    // Confirmation obligatoire avant toute mise à jour (re-téléchargement, remplace le
    // fichier existant) ou désinstallation (suppression définitive du fichier).
    actionAConfirmer?.let { (type, dossierCible) ->
        val estSuppression = type == "suppr"
        AlertDialog(
            onDismissRequest = { actionAConfirmer = null },
            icon = {
                Icon(
                    if (estSuppression) Icons.Default.Delete else Icons.Default.Refresh,
                    contentDescription = null,
                    tint = RougeJeu
                )
            },
            title = { Text(if (estSuppression) "Uninstall this game?" else "Update this game?") },
            text = {
                Text(
                    if (estSuppression)
                        "“${item.nom}” will be deleted from this device. This cannot be undone."
                    else
                        "“${item.nom}” will be downloaded again and replace the version currently installed."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SoundEffects.clic()
                    if (estSuppression) onDesinstaller() else dossierCible?.let(onInstaller)
                    actionAConfirmer = null
                }) {
                    Text(
                        if (estSuppression) "Uninstall" else "Update",
                        color = RougeJeu,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { SoundEffects.menuFermer(); actionAConfirmer = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Étiquettes du jeu : console, année, genre, note, plateformes d'origine.
 *
 * Des pastilles à icône plutôt qu'un tableau de lignes « intitulé : valeur » : sur une
 * console tenue en main, on balaie l'écran, on ne lit pas un formulaire. Chaque étiquette
 * n'apparaît que si l'information existe — IGDB est inégalement rempli.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EtiquettesJeu(categorie: String, fiche: IGDBMetadataService.Metadonnees?) {
    val etiquettes = buildList {
        add(Triple(Icons.Default.SportsEsports, categorie, true))
        fiche?.annee?.let { add(Triple(Icons.Default.CalendarMonth, it.toString(), false)) }
        fiche?.note?.let { add(Triple(Icons.Default.Star, "$it/100", false)) }
        fiche?.genres?.firstOrNull()?.let { add(Triple(Icons.Default.LocalOffer, it, false)) }
        // La plateforme renvoyée par l'API n'est pas affichée : le dossier du
        // fichier dit déjà sur quelle console tourne le jeu, et le dit juste — la
        // base liste toutes les machines où le titre est sorti, rééditions comprises.
    }

    // FlowRow : sur un titre à rallonge ou un écran étroit, les étiquettes passent à la
    // ligne au lieu d'être tronquées.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        etiquettes.forEach { (icone, texte, principale) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (principale) RougeJeu else BlancCreme)
                    .border(
                        width = 1.dp,
                        color = if (principale) RougeJeu else RougeJeu.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icone,
                    contentDescription = null,
                    tint = if (principale) BlancJeu else RougeJeu,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    texte,
                    color = if (principale) BlancJeu else TexteFonce,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------- Écran de réglages (plein écran, glisse depuis la droite) ----------
// Trois sections : dossier d'installation par défaut, thème de couleurs, musique de fond.
// L'écran ne détient aucun état : tout est remonté à ShopScreen via les callbacks, qui
// se charge de persister dans les SharedPreferences.
@Composable
fun EcranReglages(
    dossierParDefaut: Uri?,
    onChoisirDossier: () -> Unit,
    onOuvrirStyle: () -> Unit,
    onOuvrirDisposition: () -> Unit,
    onOuvrirCles: () -> Unit,
    cles: ClesApi,
    onChangerCles: (ClesApi) -> Unit,
    reglagesMusique: ReglagesMusique,
    onToggleMusique: (Boolean) -> Unit,
    onChoisirMusique: () -> Unit,
    onSupprimerMusique: () -> Unit,
    volumeAffiche: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeFinalise: (Float) -> Unit,
    sonsActifs: Boolean,
    onToggleSons: (Boolean) -> Unit,
    volumeSons: Float,
    onVolumeSonsChange: (Float) -> Unit,
    onVolumeSonsFinalise: (Float) -> Unit,
    source: SourceCatalogue,
    onChangerSource: (SourceCatalogue) -> Unit,
    reglagesDrive: ReglagesDrive,
    onChangerDrive: (ReglagesDrive) -> Unit,
    codeConsole: String,
    etatConnexion: EtatConnexion,
    messageConnexion: String,
    connexionDirecte: Boolean,
    onConnecter: (String) -> Unit,
    onRetour: () -> Unit
) {
    val context = LocalContext.current

    // Le bouton "retour" système ferme les réglages au lieu de quitter l'appli.
    BackHandler(onBack = onRetour)

    val nomDossier = remember(dossierParDefaut) {
        dossierParDefaut?.let { uri ->
            try {
                DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: uri.toString()
            } catch (e: Exception) {
                uri.lastPathSegment ?: uri.toString()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondClair)
    ) {
        // Bandeau du haut, aux couleurs du thème, avec la flèche de retour.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RougeJeu)
                .statusBarsPadding()
                .height(HauteurBanniere)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetour) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BlancJeu)
            }
            Text(
                "Settings",
                color = BlancJeu,
                fontSize = 22.sp,
                fontFamily = PoliceMoonshop,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BordureFestonnee()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // En tête des réglages : c'est le seul groupe dont dépend le reste, et
            // l'endroit où revenir quand une étape de l'assistant a été sautée. Les
            // services ont leur propre page — chacun y explique ce qu'il apporte, ce
            // qui ne tiendrait pas entre deux cases à cocher.
            TitreGroupe("API")
            SectionReglages(titre = "Services and keys", icone = Icons.Default.VpnKey) {
                Text(
                    "Your PC code, Google Drive, IGDB and SteamGridDB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TexteFonce
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { SoundEffects.menuOuvrir(); onOuvrirCles() },
                    colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open API")
                }
            }

            TitreGroupe("Connection")

            // --- Choix de la source ---
            // Deux façons d'alimenter le catalogue, exclusives l'une de l'autre : le PC
            // d'un ami, ou son propre dossier Drive. Le choix est en tête parce qu'il
            // détermine lequel des deux réglages suivants sert à quelque chose.
            SectionReglages(titre = "Where your games come from", icone = Icons.Default.Cloud) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoixSource(
                        libelle = "Console code",
                        choisi = source == SourceCatalogue.CONSOLE,
                        modifier = Modifier.weight(1f)
                    ) { SoundEffects.clic(); onChangerSource(SourceCatalogue.CONSOLE) }
                    ChoixSource(
                        libelle = "Google Drive",
                        choisi = source == SourceCatalogue.DRIVE,
                        modifier = Modifier.weight(1f)
                    ) { SoundEffects.clic(); onChangerSource(SourceCatalogue.DRIVE) }
                }
            }

            // --- Google Drive ---
            if (source == SourceCatalogue.DRIVE) {
                SectionReglages(titre = "Google Drive", icone = Icons.Default.Cloud) {
                    var cle by remember(reglagesDrive.cleApi) { mutableStateOf(reglagesDrive.cleApi) }
                    var lien by remember(reglagesDrive.lien) { mutableStateOf(reglagesDrive.lien) }
                    val contexte = LocalContext.current

                    Text(
                        "Paste the link of a Drive folder shared with anyone who has the link, " +
                            "and your own Google API key.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TexteFonce
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = lien,
                        onValueChange = { lien = it },
                        singleLine = true,
                        label = { Text("Folder link") },
                        placeholder = { Text("https://drive.google.com/drive/folders/…") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RougeJeu,
                            focusedLabelColor = RougeJeu,
                            cursorColor = RougeJeu,
                            focusedTextColor = TexteFonce,
                            unfocusedTextColor = TexteFonce
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cle,
                        onValueChange = { cle = it },
                        singleLine = true,
                        label = { Text("API key") },
                        placeholder = { Text("AIza…") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RougeJeu,
                            focusedLabelColor = RougeJeu,
                            cursorColor = RougeJeu,
                            focusedTextColor = TexteFonce,
                            unfocusedTextColor = TexteFonce
                        )
                    )

                    // Les deux pages exactes où trouver la clé, ouvertes d'un toucher :
                    // les chercher soi-même dans la console Google est décourageant.
                    Spacer(Modifier.height(6.dp))
                    LienExterne("Get an API key") { ouvrirLien(contexte, SourceDrive.URL_CLE_API) }
                    LienExterne("Enable the Drive API for that key") {
                        ouvrirLien(contexte, SourceDrive.URL_ACTIVER_API)
                    }

                    Spacer(Modifier.height(12.dp))
                    val modifie = cle.trim() != reglagesDrive.cleApi || lien.trim() != reglagesDrive.lien
                    Button(
                        onClick = {
                            SoundEffects.clic()
                            onChangerDrive(ReglagesDrive(cle.trim(), lien.trim()))
                        },
                        enabled = modifie && cle.isNotBlank() && lien.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                    ) {
                        Text(if (modifie) "Save and load" else "Saved")
                    }
                }
            }

            if (source == SourceCatalogue.CONSOLE) {
                // --- Code de la console ---
                // Le chemin normal : l'utilisateur tape les six caractères affichés par
                // Moonshop srv sur son PC, une seule fois, et n'y revient jamais.
                SectionReglages(titre = "Console code", icone = Icons.Default.Key) {
                    var saisieCode by remember(codeConsole) { mutableStateOf(codeConsole) }
                    Text(
                        "Enter the code shown by Moonshop srv on your PC. " +
                            "You only do this once — the app then finds your PC on its own, " +
                            "on any network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TexteFonce
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = saisieCode,
                        // Le filtrage est fait ici plutôt que d'accepter puis de rejeter :
                        // impossible de saisir un caractère qui n'existe pas dans un code.
                        onValueChange = { saisieCode = Annuaire.nettoyerCode(it) },
                        singleLine = true,
                        label = { Text("Code") },
                        placeholder = { Text("A7X9K2") },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = PoliceMoonshop,
                            letterSpacing = 4.sp
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RougeJeu,
                            focusedLabelColor = RougeJeu,
                            cursorColor = RougeJeu,
                            focusedTextColor = TexteFonce,
                            unfocusedTextColor = TexteFonce
                        )
                    )
                    Spacer(Modifier.height(10.dp))

                    val (libelleEtat, couleurEtat) = when (etatConnexion) {
                        EtatConnexion.AUCUN -> "Not connected yet" to TexteFonce.copy(alpha = 0.6f)
                        EtatConnexion.RECHERCHE -> "Looking for your PC…" to TexteFonce.copy(alpha = 0.8f)
                        EtatConnexion.APPAIRAGE -> "Waiting for approval on your PC" to AccentJaune
                        EtatConnexion.REFUSE -> "Access denied on the PC" to RougeJeu
                        // Vert franc plutôt que l'accent du thème : sur une carte crème,
                        // le jaune passerait pour un avertissement.
                        EtatConnexion.CONNECTE -> {
                            val voie = if (connexionDirecte) "direct" else "via internet"
                            "Connected to $codeConsole · $voie" to Color(0xFF2E8B57)
                        }
                        EtatConnexion.HORS_LIGNE -> "PC offline" to RougeJeu
                        EtatConnexion.ERREUR -> "Connection problem" to RougeJeu
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (etatConnexion == EtatConnexion.RECHERCHE ||
                            etatConnexion == EtatConnexion.APPAIRAGE
                        ) {
                            CircularProgressIndicator(
                                color = RougeJeu,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(libelleEtat, color = couleurEtat, fontWeight = FontWeight.Bold)
                    }
                    if (messageConnexion.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            messageConnexion,
                            style = MaterialTheme.typography.bodySmall,
                            color = TexteFonce.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            SoundEffects.clic()
                            onConnecter(saisieCode)
                        },
                        enabled = Annuaire.codeComplet(saisieCode) &&
                            etatConnexion != EtatConnexion.RECHERCHE &&
                                etatConnexion != EtatConnexion.APPAIRAGE,
                        colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                    ) {
                        Text(if (saisieCode == codeConsole) "Reconnect" else "Connect")
                    }
                }
            }

            // --- Dossier d'installation par défaut ---
            SectionReglages(titre = "Install folder", icone = Icons.Default.Folder) {
                Text(
                    nomDossier ?: "No folder selected: you will be asked on every install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TexteFonce
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { SoundEffects.clic(); onChoisirDossier() },
                    colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (nomDossier == null) "Choose a folder" else "Change folder")
                }
            }

            TitreGroupe("Sound")

            // --- Effets sonores de l'interface ---
            SectionReglages(titre = "Sound effects", icone = Icons.Default.VolumeUp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Clicks, menus, loading screen and reward jingle",
                        modifier = Modifier.weight(1f),
                        color = TexteFonce,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = sonsActifs,
                        onCheckedChange = onToggleSons,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BlancJeu,
                            checkedTrackColor = RougeJeu
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Effects volume", tint = RougeJeu)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = volumeSons,
                        onValueChange = onVolumeSonsChange,
                        // Un clic est joué au relâchement seulement : à chaque pixel,
                        // le curseur déclencherait une rafale de bruitages.
                        onValueChangeFinished = { onVolumeSonsFinalise(volumeSons) },
                        enabled = sonsActifs,
                        colors = SliderDefaults.colors(
                            thumbColor = RougeJeu,
                            activeTrackColor = RougeJeu
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${(volumeSons * 100).roundToInt()} %", color = TexteFonce, fontSize = 12.sp)
                }
            }

            // --- Musique de fond ---
            SectionReglages(titre = "Background music", icone = Icons.Default.MusicNote) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reglagesMusique.nom ?: "No file selected",
                        modifier = Modifier.weight(1f),
                        color = TexteFonce,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = reglagesMusique.active,
                        onCheckedChange = { actif -> SoundEffects.clic(); onToggleMusique(actif) },
                        enabled = reglagesMusique.uri != null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BlancJeu,
                            checkedTrackColor = RougeJeu
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { SoundEffects.clic(); onChoisirMusique() },
                        colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (reglagesMusique.uri == null) "Choose" else "Change")
                    }
                    if (reglagesMusique.uri != null) {
                        OutlinedButton(
                            onClick = { SoundEffects.clic(); onSupprimerMusique() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RougeJeu),
                            border = BorderStroke(1.dp, RougeJeu)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Remove")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = RougeJeu)
                    Spacer(Modifier.width(8.dp))
                    // onValueChange règle le volume en direct ; onValueChangeFinished ne
                    // persiste qu'une fois le doigt relevé (évite d'écrire à chaque pixel).
                    Slider(
                        value = volumeAffiche,
                        onValueChange = onVolumeChange,
                        onValueChangeFinished = { onVolumeFinalise(volumeAffiche) },
                        enabled = reglagesMusique.uri != null,
                        colors = SliderDefaults.colors(
                            thumbColor = RougeJeu,
                            activeTrackColor = RougeJeu
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${(volumeAffiche * 100).roundToInt()} %", color = TexteFonce, fontSize = 12.sp)
                }
            }

            TitreGroupe("Style")

            // Le choix d'apparence part sur son propre écran : un carré chromatique
            // et une grille d'aperçus demandent de la place, et cohabitaient mal
            // avec une liste de cases à cocher.
            SectionReglages(titre = "Themes and colours", icone = Icons.Default.Palette) {
                Text(
                    "Pick from the built-in themes or create your own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TexteFonce
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { SoundEffects.menuOuvrir(); onOuvrirStyle() },
                    colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open style")
                }
            }

            // La disposition a sa propre page : chaque proposition s'y montre en croquis,
            // ce qu'une ligne de texte dans les réglages ne saurait pas faire.
            SectionReglages(titre = "Library layout", icone = Icons.Default.GridView) {
                Text(
                    "Choose how games are arranged: mosaic, shelves, grid or list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TexteFonce
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { SoundEffects.menuOuvrir(); onOuvrirDisposition() },
                    colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                ) {
                    Icon(Icons.Default.GridView, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open layout")
                }
            }
        }

    }
}

/** Bouton de choix exclusif, façon onglet : la source retenue est pleine, l'autre bordée. */
@Composable
private fun ChoixSource(
    libelle: String,
    choisi: Boolean,
    modifier: Modifier = Modifier,
    onClic: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (choisi) RougeJeu else Color.Transparent)
            .border(1.dp, RougeJeu, RoundedCornerShape(10.dp))
            .clickable(onClick = onClic)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            libelle,
            color = if (choisi) BlancJeu else RougeJeu,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Lien vers une page extérieure, souligné et précédé d'une flèche sortante. */
@Composable
private fun LienExterne(libelle: String, onClic: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { SoundEffects.clic(); onClic() }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.OpenInNew,
            contentDescription = null,
            tint = RougeJeu,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            libelle,
            color = RougeJeu,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        )
    }
}

/** Intitulé d'une famille de réglages : sons avec les sons, connexion avec la connexion. */
@Composable
private fun TitreGroupe(libelle: String) {
    Text(
        libelle.uppercase(),
        color = RougeJeu,
        fontFamily = PoliceMoonshop,
        fontSize = 20.sp,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun SectionReglages(
    titre: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    contenu: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BlancCreme),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, RougeJeu.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icone, contentDescription = null, tint = RougeJeu)
                Spacer(Modifier.width(8.dp))
                Text(titre, color = RougeJeu, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            contenu()
        }
    }
}
