package com.monshop.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * La bibliothèque, dans la disposition choisie par l'utilisateur.
 *
 * Toutes les dispositions partagent la même tuile et la même barre de recherche ; elles
 * ne diffèrent que par l'agencement. Chacune demande son illustration au format qui lui
 * convient : c'est ce qui distingue une image cadrée d'une image rognée au hasard.
 */

// Deux petites tuiles empilées font exactement la hauteur d'une grande : sans cette
// égalité, les rangées ne s'alignent plus et la mosaïque paraît bâclée plutôt qu'irrégulière.
private val HauteurGrande = 208.dp
private val HauteurPetite = 98.dp
private val EcartTuiles = 12.dp

// Rayon d'étagère : assez haut pour une jaquette debout, assez court pour qu'une
// deuxième console apparaisse sous la première.
private val HauteurEtagere = 172.dp
private val LargeurCarteEtagere = 122.dp

/** Proportion d'une boîte de jeu, à quelques millimètres près. */
private const val RAPPORT_JAQUETTE = 0.72f

/**
 * Le format d'une tuile décide de l'illustration demandée.
 *
 * Une jaquette verticale glissée dans une tuile large perd son titre et la moitié du
 * personnage ; c'est exactement le défaut de cadrage que ce choix supprime.
 */
enum class FormatTuile { LARGE, PORTRAIT }

/** Genres proposés en filtre, alimentés au fur et à mesure que les fiches arrivent. */
object CacheGenres {
    private val parJeu = mutableStateMapOf<String, List<String>>()

    fun enregistrer(nomJeu: String, genres: List<String>) {
        if (genres.isNotEmpty() && parJeu[nomJeu] != genres) parJeu[nomJeu] = genres
    }

    fun genresDe(nomJeu: String): List<String> = parJeu[nomJeu].orEmpty()

    /** Genres connus, du plus représenté au moins fréquent. */
    fun genresConnus(): List<String> =
        parJeu.values.flatten()
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(12)
}

@Composable
fun EcranBibliotheque(
    catalogue: List<CatalogItem>,
    installes: Map<String, Boolean>,
    disposition: Disposition,
    chargement: Boolean,
    erreur: String?,
    onActualiser: () -> Unit,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onChoisirImageLocale: (CatalogItem) -> Unit,
    onRecadrer: (CatalogItem, Float) -> Unit
) {
    val context = LocalContext.current
    var recherche by remember { mutableStateOf("") }
    var genreChoisi by remember { mutableStateOf<String?>(null) }

    // Le jeu dont le menu long-appui est ouvert, avec la proportion de la tuile touchée :
    // le recadrage se règle dans le format où l'image sera vue, pas dans un cadre théorique.
    var menuJeu by remember { mutableStateOf<Pair<CatalogItem, Float>?>(null) }

    val genres = CacheGenres.genresConnus()

    // Filtres cumulables : le texte cherche dans le nom, le genre dans la fiche.
    // Calculé à chaque composition plutôt que mémorisé : les genres arrivent après coup,
    // au fil des fiches, et une liste mémorisée resterait figée sur ceux du début.
    val filtres =
        catalogue.filter { item ->
            val correspondTexte = recherche.isBlank() ||
                item.nom.contains(recherche.trim(), ignoreCase = true)
            val correspondGenre = genreChoisi == null ||
                CacheGenres.genresDe(item.nom).any { it.equals(genreChoisi, ignoreCase = true) }
            correspondTexte && correspondGenre
        }

    val rechercheActive = recherche.isNotBlank() || genreChoisi != null
    val ouvrirMenu: (CatalogItem, Float) -> Unit = { jeu, rapport -> menuJeu = jeu to rapport }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(EcartTuiles)
    ) {
        item(key = "recherche") {
            BarreRecherche(
                recherche = recherche,
                onRecherche = { recherche = it },
                chargement = chargement,
                onActualiser = onActualiser
            )
        }

        if (genres.isNotEmpty()) {
            item(key = "genres") {
                FiltresGenre(
                    genres = genres,
                    choisi = genreChoisi,
                    onChoisir = { genreChoisi = if (genreChoisi == it) null else it }
                )
            }
        }

        erreur?.let { message ->
            item(key = "erreur") {
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        if (filtres.isEmpty()) {
            item(key = "vide") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (chargement) "Loading…" else "Nothing here",
                        color = TexteFonce.copy(alpha = 0.6f)
                    )
                }
            }
            return@LazyColumn
        }

        // Recherche en cours : une seule série de résultats, sans sections — l'utilisateur
        // cherche quelque chose de précis, le classement ne l'intéresse plus.
        if (rechercheActive) {
            item(key = "titre-resultats") { TitreSection("Results", filtres.size) }
            agencer(disposition, filtres, installes, onOuvrirJeu, ouvrirMenu, "resultats")
            return@LazyColumn
        }

        // Une section par console, dans l'ordre alphabétique : c'est le classement que
        // l'utilisateur a lui-même donné à ses dossiers. Aucun jeu n'est repris dans deux
        // sections — une pastille suffit à signaler ceux déjà installés, et la vue
        // « Installed » du menu reste l'endroit où les retrouver seuls.
        filtres.groupBy { it.categorie }.toSortedMap().forEach { (categorie, jeux) ->
            item(key = "titre-$categorie") { TitreSection(categorie, jeux.size) }
            agencer(disposition, jeux, installes, onOuvrirJeu, ouvrirMenu, categorie)
        }
    }

    menuJeu?.let { (jeu, rapport) ->
        MenuJeu(
            item = jeu,
            personnalise = Personnalisations.de(jeu.nom) != null,
            onImageLocale = { menuJeu = null; onChoisirImageLocale(jeu) },
            onRecadrer = { menuJeu = null; onRecadrer(jeu, rapport) },
            onReinitialiser = {
                menuJeu = null
                Personnalisations.effacer(context, jeu.nom)
            },
            onFermer = { menuJeu = null }
        )
    }
}

/** Aiguille vers la disposition retenue ; toutes partagent la même tuile. */
private fun LazyListScope.agencer(
    disposition: Disposition,
    jeux: List<CatalogItem>,
    installes: Map<String, Boolean>,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit,
    prefixe: String
) {
    when (disposition) {
        Disposition.MOSAIQUE -> mosaique(jeux, installes, onOuvrirJeu, onMenu, prefixe)
        Disposition.ETAGERES -> etagere(jeux, installes, onOuvrirJeu, onMenu, prefixe)
        Disposition.GRILLE -> grille(jeux, installes, onOuvrirJeu, onMenu, prefixe)
        Disposition.LISTE -> liste(jeux, installes, onOuvrirJeu, onMenu, prefixe)
    }
}

/**
 * Mosaïque : rangées inégales et alternées.
 *
 * Le motif se répète toutes les trois tuiles, en inversant le côté de la grande à chaque
 * rangée : l'irrégularité est ainsi voulue et lisible, là où des tailles tirées au hasard
 * donneraient un damier accidenté.
 */
private fun LazyListScope.mosaique(
    jeux: List<CatalogItem>,
    installes: Map<String, Boolean>,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit,
    prefixe: String
) {
    val rangees = jeux.chunked(3)
    // Clés préfixées par la section : plusieurs séries se suivent dans la même liste,
    // et deux rangées ne doivent jamais porter la même clé.
    itemsIndexed(
        rangees,
        key = { indice, rangee -> "$prefixe-$indice-${rangee.first().nom}" }
    ) { indice, rangee ->
        val grandeAGauche = indice % 2 == 0
        Row(
            modifier = Modifier.fillMaxWidth().height(HauteurGrande),
            horizontalArrangement = Arrangement.spacedBy(EcartTuiles)
        ) {
            when {
                // Trois jeux : une grande et deux petites empilées.
                rangee.size == 3 -> {
                    val grande = rangee[0]
                    val petites = rangee.drop(1)
                    if (grandeAGauche) {
                        TuileJeu(grande, installes[grande.nom] == true, FormatTuile.LARGE,
                            Modifier.weight(1.35f).fillMaxHeight(), onOuvrirJeu, onMenu)
                        ColonnePetites(petites, installes, onOuvrirJeu, onMenu, Modifier.weight(1f))
                    } else {
                        ColonnePetites(petites, installes, onOuvrirJeu, onMenu, Modifier.weight(1f))
                        TuileJeu(grande, installes[grande.nom] == true, FormatTuile.LARGE,
                            Modifier.weight(1.35f).fillMaxHeight(), onOuvrirJeu, onMenu)
                    }
                }
                // Deux jeux : deux tuiles égales, pleine hauteur.
                rangee.size == 2 -> rangee.forEach { jeu ->
                    TuileJeu(jeu, installes[jeu.nom] == true, FormatTuile.LARGE,
                        Modifier.weight(1f).fillMaxHeight(), onOuvrirJeu, onMenu)
                }
                // Un seul : il occupe toute la largeur, comme une affiche.
                else -> TuileJeu(rangee[0], installes[rangee[0].nom] == true, FormatTuile.LARGE,
                    Modifier.weight(1f).fillMaxHeight(), onOuvrirJeu, onMenu)
            }
        }
    }
}

/**
 * Étagères : une rangée horizontale par section, jaquettes debout.
 *
 * C'est la disposition qui montre le mieux les boîtes, puisqu'elle leur rend leur format
 * d'origine, et celle qui laisse le plus de consoles visibles à l'écran en même temps.
 */
private fun LazyListScope.etagere(
    jeux: List<CatalogItem>,
    installes: Map<String, Boolean>,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit,
    prefixe: String
) {
    item(key = "$prefixe-etagere") {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(HauteurEtagere),
            horizontalArrangement = Arrangement.spacedBy(EcartTuiles)
        ) {
            items(jeux, key = { it.nom }) { jeu ->
                TuileJeu(jeu, installes[jeu.nom] == true, FormatTuile.PORTRAIT,
                    Modifier.width(LargeurCarteEtagere).fillMaxHeight(), onOuvrirJeu, onMenu)
            }
        }
    }
}

/** Grille régulière de jaquettes : la vue la plus dense, sans rythme particulier. */
private fun LazyListScope.grille(
    jeux: List<CatalogItem>,
    installes: Map<String, Boolean>,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit,
    prefixe: String
) {
    val rangees = jeux.chunked(4)
    itemsIndexed(
        rangees,
        key = { indice, rangee -> "$prefixe-g$indice-${rangee.first().nom}" }
    ) { _, rangee ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EcartTuiles)
        ) {
            rangee.forEach { jeu ->
                TuileJeu(jeu, installes[jeu.nom] == true, FormatTuile.PORTRAIT,
                    Modifier.weight(1f).aspectRatio(RAPPORT_JAQUETTE), onOuvrirJeu, onMenu)
            }
            // Rangée incomplète : des vides pour que les dernières jaquettes gardent
            // la largeur des autres au lieu de s'étirer.
            repeat(4 - rangee.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Liste : la disposition d'origine, une ligne par jeu avec sa description. */
private fun LazyListScope.liste(
    jeux: List<CatalogItem>,
    installes: Map<String, Boolean>,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit,
    prefixe: String
) {
    items(jeux, key = { "$prefixe-l-${it.nom}" }) { jeu ->
        LigneJeu(jeu, installes[jeu.nom] == true, onOuvrirJeu, onMenu)
    }
}

@Composable
private fun ColonnePetites(
    jeux: List<CatalogItem>,
    installes: Map<String, Boolean>,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(EcartTuiles)
    ) {
        jeux.forEach { jeu ->
            TuileJeu(jeu, installes[jeu.nom] == true, FormatTuile.LARGE,
                Modifier.fillMaxWidth().height(HauteurPetite), onOuvrirJeu, onMenu)
        }
    }
}

/**
 * Une tuile : l'illustration d'abord, le titre par-dessus.
 *
 * Le nom est posé sur un dégradé sombre plutôt que dans un bandeau plein, pour que
 * l'image reste entière — c'est elle qu'on parcourt des yeux, pas le texte.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TuileJeu(
    item: CatalogItem,
    estInstalle: Boolean,
    format: FormatTuile,
    modifier: Modifier = Modifier,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit
) {
    val meta = rememberMetadonneesAffichees(item)
    val cadrage = Personnalisations.de(item.nom)

    // Les genres remontent au filtre dès qu'une fiche est connue : la liste des genres
    // proposés se remplit donc à mesure que les tuiles apparaissent.
    LaunchedEffect(meta.fiche) {
        meta.fiche?.let { CacheGenres.enregistrer(item.nom, it.genres) }
    }

    var taille by remember { mutableStateOf(IntSize.Zero) }
    val rapport = if (taille.height > 0) taille.width.toFloat() / taille.height else 1.6f
    var focalise by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .onSizeChanged { taille = it }
            .clip(forme)
            .background(BlancCreme)
            .border(
                width = if (focalise) 3.dp else 1.dp,
                color = if (focalise) AccentJaune else RougeJeu.copy(alpha = 0.18f),
                shape = forme
            )
            .onFocusChanged { focalise = it.isFocused }
            // La manette n'envoie pas les mêmes touches qu'un clavier : A ouvre le jeu,
            // Y ouvre le menu de personnalisation, là où le tactile utilise l'appui long.
            .onKeyEvent { evenement ->
                if (evenement.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (evenement.key) {
                    Key.ButtonA -> { SoundEffects.clic(); onOuvrirJeu(item); true }
                    Key.ButtonY -> { SoundEffects.menuOuvrir(); onMenu(item, rapport); true }
                    else -> false
                }
            }
            .combinedClickable(
                onClick = { SoundEffects.clic(); onOuvrirJeu(item) },
                onLongClick = { SoundEffects.menuOuvrir(); onMenu(item, rapport) }
            )
    ) {
        IllustrationJeu(item, meta, cadrage, format, Modifier.fillMaxSize())

        // Dégradé sous le titre : lisible sur une jaquette claire comme sombre.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
        )

        Text(
            item.nom.substringBeforeLast('.'),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )

        if (estInstalle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Installed",
                    tint = AccentJaune,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * L'image d'une tuile, cadrée.
 *
 * Trois décisions se succèdent : l'image choisie à la main l'emporte sur tout ; sinon
 * l'illustration est prise au format de la tuile ; et faute de mieux, une jaquette
 * verticale est ancrée en haut, là où se trouvent le titre et le visage, plutôt que
 * centrée sur ce que le hasard veut bien laisser.
 */
@Composable
private fun IllustrationJeu(
    item: CatalogItem,
    meta: MetadonneesAffichees,
    cadrage: CadrageJeu?,
    format: FormatTuile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val auFormat = when (format) {
        FormatTuile.LARGE -> meta.jaquetteLarge ?: meta.banniere
        FormatTuile.PORTRAIT -> meta.image
    }
    val repli = auFormat == null
    val adresse = cadrage?.uriImage ?: auFormat ?: meta.image

    if (adresse == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.SportsEsports,
                contentDescription = null,
                tint = RougeJeu.copy(alpha = 0.5f),
                modifier = Modifier.size(34.dp)
            )
        }
        return
    }

    val zone = cadrage?.takeIf { !it.zoneEntiere }

    AsyncImage(
        model = ImageRequest.Builder(context).data(adresse).crossfade(250).build(),
        contentDescription = item.nom,
        // Une zone choisie à la main est étirée pour remplir exactement la tuile ;
        // sans zone, le recadrage automatique reprend la main.
        contentScale = if (zone != null) ContentScale.FillBounds else ContentScale.Crop,
        alignment = if (repli && cadrage == null) Alignment.TopCenter else Alignment.Center,
        modifier = modifier.graphicsLayer {
            // Agrandir de 1/largeur puis reculer du coin choisi amène la zone retenue
            // pile aux bords de la tuile. L'origine en haut à gauche rend ce calcul
            // indépendant de la taille réelle de l'image.
            zone?.let {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = 1f / it.largeur
                scaleY = 1f / it.hauteur
                translationX = -it.x * size.width / it.largeur
                translationY = -it.y * size.height / it.hauteur
            }
        }
    )
}

/** Une ligne de la disposition « List » : vignette debout, titre, description. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneJeu(
    item: CatalogItem,
    estInstalle: Boolean,
    onOuvrirJeu: (CatalogItem) -> Unit,
    onMenu: (CatalogItem, Float) -> Unit
) {
    val meta = rememberMetadonneesAffichees(item)
    val cadrage = Personnalisations.de(item.nom)

    LaunchedEffect(meta.fiche) {
        meta.fiche?.let { CacheGenres.enregistrer(item.nom, it.genres) }
    }

    var focalise by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(BlancCreme)
            .border(
                width = if (focalise) 3.dp else 1.dp,
                color = if (focalise) AccentJaune else RougeJeu.copy(alpha = 0.18f),
                shape = forme
            )
            .onFocusChanged { focalise = it.isFocused }
            .onKeyEvent { evenement ->
                if (evenement.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (evenement.key) {
                    Key.ButtonA -> { SoundEffects.clic(); onOuvrirJeu(item); true }
                    Key.ButtonY -> { SoundEffects.menuOuvrir(); onMenu(item, RAPPORT_JAQUETTE); true }
                    else -> false
                }
            }
            .combinedClickable(
                onClick = { SoundEffects.clic(); onOuvrirJeu(item) },
                onLongClick = { SoundEffects.menuOuvrir(); onMenu(item, RAPPORT_JAQUETTE) }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .aspectRatio(RAPPORT_JAQUETTE)
                .clip(RoundedCornerShape(8.dp))
        ) {
            IllustrationJeu(item, meta, cadrage, FormatTuile.PORTRAIT, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.nom.substringBeforeLast('.'),
                color = TexteFonce,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta.description.isNotBlank()) {
                Text(
                    meta.description,
                    color = TexteFonce.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (estInstalle) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Installed",
                tint = AccentJaune,
                modifier = Modifier.padding(start = 8.dp).size(20.dp)
            )
        }
    }
}

/** Menu d'un jeu : ce que l'appli n'a pas su deviner, l'utilisateur le corrige ici. */
@Composable
private fun MenuJeu(
    item: CatalogItem,
    personnalise: Boolean,
    onImageLocale: () -> Unit,
    onRecadrer: () -> Unit,
    onReinitialiser: () -> Unit,
    onFermer: () -> Unit
) {
    Dialog(onDismissRequest = onFermer) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BlancCreme,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 14.dp)) {
                Text(
                    item.nom.substringBeforeLast('.'),
                    color = TexteFonce,
                    fontFamily = PoliceMoonshop,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(6.dp))
                ActionMenu(Icons.Default.Image, "Pick a local image") { onImageLocale() }
                ActionMenu(Icons.Default.Crop, "Adjust framing") { onRecadrer() }
                if (personnalise) {
                    ActionMenu(Icons.Default.RestartAlt, "Back to automatic") { onReinitialiser() }
                }
                ActionMenu(Icons.Default.Close, "Cancel") { onFermer() }
            }
        }
    }
}

@Composable
private fun ActionMenu(icone: androidx.compose.ui.graphics.vector.ImageVector, texte: String, onClic: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { SoundEffects.clic(); onClic() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, contentDescription = null, tint = RougeJeu)
        Spacer(Modifier.width(14.dp))
        Text(texte, color = TexteFonce, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TitreSection(titre: String, compte: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            titre,
            color = TexteFonce,
            fontFamily = PoliceMoonshop,
            fontSize = 23.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$compte",
            color = RougeJeu,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun BarreRecherche(
    recherche: String,
    onRecherche: (String) -> Unit,
    chargement: Boolean,
    onActualiser: () -> Unit
) {
    val gestionFocus = LocalFocusManager.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = recherche,
                onValueChange = onRecherche,
                singleLine = true,
                placeholder = { Text("Search a game") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RougeJeu) },
                trailingIcon = {
                    if (recherche.isNotEmpty()) {
                        IconButton(onClick = { SoundEffects.menuFermer(); onRecherche("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = RougeJeu)
                        }
                    }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    // Un champ de saisie garde les flèches pour déplacer le curseur, ce qui
                    // piège la manette dedans : plus rien ne répond une fois entré. Haut et
                    // bas ne servent à rien sur une seule ligne, et B ressort — ils rendent
                    // donc la navigation au reste de l'écran.
                    .onPreviewKeyEvent { evenement ->
                        if (evenement.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (evenement.key) {
                            Key.DirectionDown -> gestionFocus.moveFocus(FocusDirection.Down)
                            Key.DirectionUp -> gestionFocus.moveFocus(FocusDirection.Up)
                            Key.ButtonB, Key.Back -> { gestionFocus.clearFocus(); true }
                            else -> false
                        }
                    },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RougeJeu,
                    unfocusedBorderColor = RougeJeu.copy(alpha = 0.35f),
                    cursorColor = RougeJeu,
                    focusedTextColor = TexteFonce,
                    unfocusedTextColor = TexteFonce
                )
            )
            IconButton(onClick = { SoundEffects.clic(); onActualiser() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = RougeJeu)
            }
        }
        if (chargement) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(4.dp)),
                color = RougeJeu
            )
        }
    }
}

@Composable
private fun FiltresGenre(genres: List<String>, choisi: String?, onChoisir: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(genres) { genre ->
            val actif = genre.equals(choisi, ignoreCase = true)
            var focalise by remember { mutableStateOf(false) }
            val echelle by animateFloatAsState(if (focalise) 1.06f else 1f, label = "pastille")
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = echelle; scaleY = echelle }
                    .clip(RoundedCornerShape(50))
                    .background(if (actif) RougeJeu else Color.Transparent)
                    .border(
                        width = if (focalise) 2.dp else 1.dp,
                        color = if (focalise) AccentJaune else RougeJeu.copy(alpha = if (actif) 1f else 0.35f),
                        shape = RoundedCornerShape(50)
                    )
                    .onFocusChanged { focalise = it.isFocused }
                    .clickable { SoundEffects.clic(); onChoisir(genre) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    genre,
                    color = if (actif) BlancJeu else RougeJeu,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}
