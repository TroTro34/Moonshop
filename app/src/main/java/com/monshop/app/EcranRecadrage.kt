package com.monshop.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Choix de la zone d'une illustration à montrer sur la tuile.
 *
 * L'image entière est affichée, et un cadre s'y déplace : ce qu'il entoure est
 * exactement ce que la tuile affichera. Régler un zoom et un décalage revenait au même
 * résultat par un chemin détourné — on voyait le contenu bouger sans jamais voir ce
 * qu'on abandonnait.
 *
 * Le cadre garde la proportion de la tuile d'où l'écran a été ouvert, sans quoi l'image
 * retenue serait déformée à l'affichage.
 */
@Composable
fun EcranRecadrage(
    item: CatalogItem,
    rapport: Float,
    onValider: (CadrageJeu) -> Unit,
    onAnnuler: () -> Unit
) {
    val context = LocalContext.current
    val meta = rememberMetadonneesAffichees(item)
    val depart = Personnalisations.de(item.nom) ?: CadrageJeu()
    val adresse = depart.uriImage ?: meta.jaquetteLarge ?: meta.image ?: meta.banniere

    // La taille de l'image est demandée séparément, et non lue sur un peintre : Coil ne
    // lance le chargement qu'au premier dessin, or l'écran attendait justement cette
    // taille pour dessiner quoi que ce soit. Le cadre restait donc en attente à jamais.
    var tailleImage by remember(adresse) { mutableStateOf<Size?>(null) }
    var echecImage by remember(adresse) { mutableStateOf(false) }

    LaunchedEffect(adresse) {
        if (adresse == null) return@LaunchedEffect
        val resultat = context.imageLoader.execute(
            ImageRequest.Builder(context).data(adresse).build()
        )
        val dessin = (resultat as? SuccessResult)?.drawable
        if (dessin != null && dessin.intrinsicWidth > 0 && dessin.intrinsicHeight > 0) {
            tailleImage = Size(dessin.intrinsicWidth.toFloat(), dessin.intrinsicHeight.toFloat())
        } else {
            echecImage = true
        }
    }

    // La zone en cours d'édition. Remise à celle enregistrée dès que le jeu change :
    // un état partagé entre deux jeux ferait hériter le second du cadrage du premier.
    var zoneRetenue by remember(item.nom) { mutableStateOf(depart) }

    BackHandler(onBack = onAnnuler)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            item.nom.substringBeforeLast('.'),
            color = Color.White,
            fontFamily = PoliceMoonshop,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Drag the frame over what you want to keep. Its corner resizes it.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val taille = tailleImage
            when {
                adresse == null -> Text("No image for this game yet", color = Color.White.copy(alpha = 0.6f))
                echecImage -> Text("This image could not be loaded", color = Color.White.copy(alpha = 0.6f))
                taille == null -> CircularProgressIndicator(color = AccentJaune)
                else -> SelecteurZone(
                    adresse = adresse,
                    rapportImage = taille.width / taille.height,
                    rapportTuile = rapport,
                    depart = depart,
                    onZone = { zone -> zoneRetenue = zone }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { SoundEffects.menuFermer(); onAnnuler() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
            OutlinedButton(
                onClick = { SoundEffects.clic(); zoneRetenue = CadrageJeu(depart.uriImage) },
                enabled = !zoneRetenue.zoneEntiere,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Whole image")
            }
            Button(
                onClick = { SoundEffects.clic(); onValider(zoneRetenue.copy(uriImage = depart.uriImage)) },
                colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * L'image entière, avec un cadre déplaçable par-dessus.
 *
 * Tout le calcul se fait en pixels du cadre affiché, puis se convertit en fractions au
 * moment d'être remonté : c'est ce qui rend le réglage valable pour n'importe quelle
 * taille de tuile.
 */
@Composable
private fun SelecteurZone(
    adresse: Any,
    rapportImage: Float,
    rapportTuile: Float,
    depart: CadrageJeu,
    onZone: (CadrageJeu) -> Unit
) {
    var vue by remember { mutableStateOf(IntSize.Zero) }
    // Coin haut-gauche et largeur de la zone, en fractions de l'image.
    var x by remember(depart) { mutableStateOf(depart.x) }
    var y by remember(depart) { mutableStateOf(depart.y) }
    var largeur by remember(depart) { mutableStateOf(depart.largeur) }

    // La hauteur découle de la largeur : le cadre doit avoir, une fois affiché, la
    // proportion de la tuile — donc en fractions d'image, le rapport des deux.
    val hauteur = (largeur * rapportImage / rapportTuile).coerceAtMost(1f)

    // Recalé à chaque changement pour que le cadre ne déborde jamais de l'image.
    fun borner() {
        largeur = largeur.coerceIn(0.1f, 1f)
        val h = (largeur * rapportImage / rapportTuile)
        if (h > 1f) largeur = rapportTuile / rapportImage
        x = x.coerceIn(0f, 1f - largeur)
        y = y.coerceIn(0f, 1f - (largeur * rapportImage / rapportTuile))
        onZone(CadrageJeu(depart.uriImage, x, y, largeur, largeur * rapportImage / rapportTuile))
    }

    LaunchedEffect(rapportImage, rapportTuile) { borner() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Lue ici : le corps d'un Canvas n'est pas composable, il ne peut pas interroger
    // le thème.
    val jaune = AccentJaune

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(rapportImage)
            .onSizeChanged { vue = it }
            .focusRequester(focus)
            .focusable()
            // À la manette : la croix déplace, les gâchettes agrandissent ou réduisent.
            .onKeyEvent { evenement ->
                if (evenement.type != KeyEventType.KeyDown) return@onKeyEvent false
                val pas = 0.02f
                when (evenement.key) {
                    Key.DirectionLeft -> { x -= pas; borner(); true }
                    Key.DirectionRight -> { x += pas; borner(); true }
                    Key.DirectionUp -> { y -= pas; borner(); true }
                    Key.DirectionDown -> { y += pas; borner(); true }
                    Key.ButtonR1, Key.ButtonR2 -> { largeur += 0.05f; borner(); true }
                    Key.ButtonL1, Key.ButtonL2 -> { largeur -= 0.05f; borner(); true }
                    else -> false
                }
            }
            .pointerInput(rapportImage, rapportTuile) {
                detectDragGestures { changement, deplacement ->
                    changement.consume()
                    if (vue.width == 0 || vue.height == 0) return@detectDragGestures
                    // Poignée de redimensionnement : le quart bas-droit du cadre.
                    val surPoignee = changement.position.x > (x + largeur) * vue.width - 44f &&
                        changement.position.y > (y + hauteur) * vue.height - 44f
                    if (surPoignee) {
                        largeur += deplacement.x / vue.width
                    } else {
                        x += deplacement.x / vue.width
                        y += deplacement.y / vue.height
                    }
                    borner()
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(adresse).build(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val gauche = x * size.width
            val haut = y * size.height
            val large = largeur * size.width
            val haute = hauteur * size.height

            // Assombrit tout ce qui sera perdu : c'est ce contraste qui rend le choix
            // lisible d'un coup d'œil, bien plus qu'un simple liseré.
            val voile = Color.Black.copy(alpha = 0.62f)
            drawRect(voile, size = Size(size.width, haut))
            drawRect(voile, topLeft = Offset(0f, haut + haute), size = Size(size.width, size.height - haut - haute))
            drawRect(voile, topLeft = Offset(0f, haut), size = Size(gauche, haute))
            drawRect(
                voile,
                topLeft = Offset(gauche + large, haut),
                size = Size(size.width - gauche - large, haute)
            )

            drawRect(
                color = jaune,
                topLeft = Offset(gauche, haut),
                size = Size(large, haute),
                style = Stroke(width = 3f)
            )
            // Poignée, dessinée là où le doigt doit se poser pour redimensionner.
            drawCircle(jaune, radius = 13f, center = Offset(gauche + large, haut + haute))
        }
    }
}
