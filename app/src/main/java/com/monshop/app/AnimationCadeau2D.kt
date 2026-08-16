package com.monshop.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * La célébration en aplat, telle qu'elle a toujours été.
 *
 * Le cadeau tombe et rebondit, s'agite trois fois, le couvercle saute, la jaquette sort
 * au milieu d'une gerbe d'étoiles, puis le nom s'affiche. Rien n'y est projeté dans
 * l'espace : c'est un dessin plat, et il assume de l'être.
 *
 * Conservée à côté de la version en volume plutôt que remplacée par elle — les deux
 * cohabitent, et le choix revient à l'utilisateur.
 */
private val TailleCadeau2D = 190.dp
private const val NombreEtoiles2D = 14
private const val NombreRayons2D = 12
private const val NombreConfettis2D = 28

/** Un confetti : direction, vitesse et rotation tirées une fois pour toutes, sinon
 *  chaque recomposition ferait sauter la gerbe. */
private data class Confetti2D(
    val angle: Float,
    val distance: Float,
    val taille: Float,
    val rotation: Float,
    val teinte: Int
)

@Composable
fun EcranCadeau2D(item: CatalogItem, onTermine: () -> Unit) {
    val meta = rememberMetadonneesAffichees(item)
    val context = LocalContext.current
    val densite = LocalDensity.current

    val entree = remember { Animatable(0f) }    // 0 = hors écran en haut, 1 = posé au centre
    val secousse = remember { Animatable(0f) }  // inclinaison en degrés
    val ouverture = remember { Animatable(0f) } // 0 = fermé, 1 = couvercle envolé, jaquette sortie
    val apparitionTexte = remember { Animatable(0f) }
    // Rotation lente et continue des rayons derrière la jaquette : c'est ce qui fait
    // vivre l'écran une fois le cadeau ouvert, quand plus rien d'autre ne bouge.
    val rotationRayons = rememberInfiniteTransition(label = "rayons").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
        label = "angleRayons"
    )
    val confettis = remember {
        val tirage = Random(7)
        List(NombreConfettis2D) {
            Confetti2D(
                angle = tirage.nextFloat() * 360f,
                distance = 0.45f + tirage.nextFloat() * 0.75f,
                taille = 7f + tirage.nextFloat() * 9f,
                rotation = tirage.nextFloat() * 720f - 360f,
                teinte = tirage.nextInt(3)
            )
        }
    }

    // Garde-fou : l'utilisateur peut fermer pendant l'animation, la fin programmée ne
    // doit alors pas rappeler onTermine une seconde fois.
    var dejaTermine by remember { mutableStateOf(false) }
    val fermer = {
        if (!dejaTermine) {
            dejaTermine = true
            onTermine()
        }
    }

    BackHandler { fermer() }

    LaunchedEffect(item.nom) {
        SoundEffects.menuOuvrir()
        entree.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        repeat(3) {
            secousse.animateTo(9f, tween(90))
            secousse.animateTo(-9f, tween(90))
        }
        secousse.animateTo(0f, tween(90))
        SoundEffects.cadeauOuvert()
        ouverture.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
        SoundEffects.recompense()
        apparitionTexte.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
        delay(2400)
        fermer()
    }

    val accent = AccentJaune
    val blanc = BlancJeu
    val hauteurChute = with(densite) { 420.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RougeJeu)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = fermer
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(
                modifier = Modifier
                    .size(TailleCadeau2D * 1.6f)
                    .graphicsLayer {
                        translationY = -hauteurChute * (1f - entree.value)
                        rotationZ = secousse.value
                        alpha = entree.value.coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rayons de lumière + halo, dessinés au fond : ils donnent au cadeau
                // ouvert l'allure d'une récompense plutôt que d'une simple image.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (ouverture.value <= 0f) return@Canvas
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val avancement = ouverture.value.coerceIn(0f, 1f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(blanc.copy(alpha = 0.34f), Color.Transparent),
                            center = centre,
                            radius = size.minDimension * 0.52f * avancement
                        ),
                        radius = size.minDimension * 0.52f * avancement,
                        center = centre
                    )

                    rotate(degrees = rotationRayons.value, pivot = centre) {
                        val longueur = size.minDimension * 0.62f * avancement
                        for (i in 0 until NombreRayons2D) {
                            val angle = Math.PI * 2 * i / NombreRayons2D
                            val demiLargeur = 0.055
                            val rayon = Path().apply {
                                moveTo(centre.x, centre.y)
                                lineTo(
                                    centre.x + (longueur * Math.cos(angle - demiLargeur)).toFloat(),
                                    centre.y + (longueur * Math.sin(angle - demiLargeur)).toFloat()
                                )
                                lineTo(
                                    centre.x + (longueur * Math.cos(angle + demiLargeur)).toFloat(),
                                    centre.y + (longueur * Math.sin(angle + demiLargeur)).toFloat()
                                )
                                close()
                            }
                            drawPath(rayon, color = blanc, alpha = 0.16f * avancement)
                        }
                    }
                }

                // Gerbe d'étoiles projetée au moment de l'ouverture.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (ouverture.value <= 0f) return@Canvas
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val distance = size.minDimension * (0.15f + 0.42f * ouverture.value)
                    val opacite = (1f - ouverture.value).coerceIn(0f, 1f)
                    for (i in 0 until NombreEtoiles2D) {
                        val angle = 2.0 * Math.PI * i / NombreEtoiles2D
                        val position = Offset(
                            centre.x + (distance * Math.cos(angle)).toFloat(),
                            centre.y + (distance * Math.sin(angle)).toFloat()
                        )
                        drawPath(
                            dessinerEtoile(position, 10f + 8f * ouverture.value),
                            color = blanc,
                            alpha = opacite
                        )
                    }
                }

                // La jaquette monte hors de la boîte : dessinée avant le corps du cadeau
                // pour qu'elle semble bien sortir de l'intérieur.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = ouverture.value
                            scaleY = ouverture.value
                            translationY = -with(densite) { 86.dp.toPx() } * ouverture.value
                            // Léger redressement pendant la montée : la jaquette arrive
                            // de biais et se remet droite, au lieu de monter comme un
                            // ascenseur.
                            rotationZ = -9f * (1f - ouverture.value.coerceIn(0f, 1f))
                            // Le ressort dépasse volontairement 1 (rebond) : l'alpha, lui,
                            // doit rester dans [0,1].
                            alpha = ouverture.value.coerceIn(0f, 1f)
                        }
                ) {
                    Card(
                        modifier = Modifier.size(width = 96.dp, height = 120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BlancCreme),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        if (meta.image != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(meta.image).crossfade(200).build(),
                                contentDescription = item.nom,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = RougeJeu,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }
                }

                // Corps de la boîte (bas) : ruban vertical + bandeau horizontal.
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(TailleCadeau2D)
                        .height(TailleCadeau2D * 0.62f)
                ) {
                    val rayon = androidx.compose.ui.geometry.CornerRadius(size.width * 0.06f)
                    drawRoundRect(color = accent, size = size, cornerRadius = rayon)
                    val largeurRuban = size.width * 0.16f
                    drawRect(
                        color = blanc,
                        topLeft = Offset((size.width - largeurRuban) / 2f, 0f),
                        size = Size(largeurRuban, size.height)
                    )
                    drawRect(
                        color = blanc,
                        topLeft = Offset(0f, size.height * 0.18f),
                        size = Size(size.width, size.height * 0.10f),
                        alpha = 0.35f
                    )
                }

                // Couvercle : il saute vers le haut en tournant au moment de l'ouverture.
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -TailleCadeau2D * 0.62f)
                        .width(TailleCadeau2D * 1.08f)
                        .height(TailleCadeau2D * 0.34f)
                        .graphicsLayer {
                            translationY = -with(densite) { 130.dp.toPx() } * ouverture.value
                            translationX = with(densite) { 34.dp.toPx() } * ouverture.value
                            rotationZ = 32f * ouverture.value
                            alpha = (1f - ouverture.value * 0.55f).coerceIn(0f, 1f)
                        }
                ) {
                    val hauteurSlab = size.height * 0.45f
                    val hautSlab = size.height - hauteurSlab
                    val rayon = androidx.compose.ui.geometry.CornerRadius(size.width * 0.05f)
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(0f, hautSlab),
                        size = Size(size.width, hauteurSlab),
                        cornerRadius = rayon
                    )
                    val largeurRuban = size.width * 0.15f
                    drawRect(
                        color = blanc,
                        topLeft = Offset((size.width - largeurRuban) / 2f, hautSlab),
                        size = Size(largeurRuban, hauteurSlab)
                    )
                    // Nœud : deux boucles et un centre.
                    val rayonBoucle = size.height * 0.28f
                    drawCircle(color = blanc, radius = rayonBoucle, center = Offset(size.width / 2f - rayonBoucle * 0.9f, hautSlab - rayonBoucle * 0.5f))
                    drawCircle(color = blanc, radius = rayonBoucle, center = Offset(size.width / 2f + rayonBoucle * 0.9f, hautSlab - rayonBoucle * 0.5f))
                    drawCircle(color = accent, radius = rayonBoucle * 0.42f, center = Offset(size.width / 2f - rayonBoucle * 0.9f, hautSlab - rayonBoucle * 0.5f))
                    drawCircle(color = accent, radius = rayonBoucle * 0.42f, center = Offset(size.width / 2f + rayonBoucle * 0.9f, hautSlab - rayonBoucle * 0.5f))
                    drawCircle(color = blanc, radius = rayonBoucle * 0.5f, center = Offset(size.width / 2f, hautSlab - rayonBoucle * 0.35f))
                }

                // Confettis : projetés depuis le couvercle, ils retombent en tournant.
                // Dessinés en dernier pour passer devant la boîte comme devant la jaquette.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (ouverture.value <= 0f) return@Canvas
                    val avancement = ouverture.value.coerceIn(0f, 1f)
                    val centre = Offset(size.width / 2f, size.height * 0.42f)
                    val opacite = (1f - (avancement - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    if (opacite <= 0f) return@Canvas

                    confettis.forEach { confetti ->
                        val angle = Math.toRadians(confetti.angle.toDouble())
                        val portee = size.minDimension * 0.5f * confetti.distance * avancement
                        // La chute est quadratique : montée franche puis retombée, au
                        // lieu d'une expansion radiale uniforme qui ferait « explosion ».
                        val chute = size.minDimension * 0.35f * avancement * avancement * confetti.distance
                        val position = Offset(
                            centre.x + (portee * Math.cos(angle)).toFloat(),
                            centre.y + (portee * Math.sin(angle)).toFloat() + chute
                        )
                        val couleur = when (confetti.teinte) {
                            0 -> blanc
                            1 -> accent
                            else -> blanc.copy(alpha = 0.75f)
                        }
                        rotate(degrees = confetti.rotation * avancement, pivot = position) {
                            drawRect(
                                color = couleur,
                                topLeft = Offset(position.x - confetti.taille / 2f, position.y - confetti.taille / 4f),
                                size = Size(confetti.taille, confetti.taille / 2f),
                                alpha = opacite
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = apparitionTexte.value.coerceIn(0f, 1f)
                        translationY = with(densite) { 18.dp.toPx() } * (1f - apparitionTexte.value)
                        // Le titre arrive légèrement trop gros puis se pose : sans ce
                        // dépassement, il apparaît platement en fondu.
                        val echelle = 0.88f + 0.12f * apparitionTexte.value
                        scaleX = echelle
                        scaleY = echelle
                    }
            ) {
                Text(
                    "${item.nom} installed",
                    color = blanc,
                    fontSize = 26.sp,
                    fontFamily = PoliceMoonshop,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap anywhere to continue",
                    color = blanc.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
