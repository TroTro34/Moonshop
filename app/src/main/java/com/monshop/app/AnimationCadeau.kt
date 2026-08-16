package com.monshop.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Célébration de fin d'installation : le jeu sort d'un cadeau en volume.
 *
 * La boîte n'est pas un empilement de rectangles mais un vrai cube projeté : ses huit
 * sommets sont tournés dans l'espace puis ramenés à l'écran avec une perspective, ses
 * six faces sont triées par profondeur et éclairées selon leur orientation. C'est ce qui
 * lui donne du volume quand elle tourne, là où des rectangles superposés se trahissent
 * dès le premier degré de rotation.
 *
 * Tout est dessiné : aucune ressource 3D à embarquer, et la boîte prend les couleurs du
 * thème choisi par l'utilisateur.
 */

// ---------- Petite mécanique 3D ----------

private data class Point3(val x: Float, val y: Float, val z: Float)

private fun Point3.tournerY(angle: Float): Point3 {
    val c = cos(angle)
    val s = sin(angle)
    return Point3(x * c + z * s, y, -x * s + z * c)
}

private fun Point3.tournerX(angle: Float): Point3 {
    val c = cos(angle)
    val s = sin(angle)
    return Point3(x, y * c - z * s, y * s + z * c)
}

private fun Point3.tournerZ(angle: Float): Point3 {
    val c = cos(angle)
    val s = sin(angle)
    return Point3(x * c - y * s, x * s + y * c, z)
}

private operator fun Point3.plus(autre: Point3) = Point3(x + autre.x, y + autre.y, z + autre.z)

/** Projection perspective : plus un point est loin, plus il se rapproche du centre. */
private fun Point3.projeter(centre: Offset, distance: Float): Offset {
    val facteur = distance / max(distance + z, 1f)
    return Offset(centre.x + x * facteur, centre.y + y * facteur)
}

/** Les huit sommets d'un pavé, dans l'ordre attendu par [FACES]. */
private fun sommets(demiLargeur: Float, demiHauteur: Float, demiProfondeur: Float): List<Point3> = listOf(
    Point3(-demiLargeur, -demiHauteur, -demiProfondeur),
    Point3(demiLargeur, -demiHauteur, -demiProfondeur),
    Point3(demiLargeur, demiHauteur, -demiProfondeur),
    Point3(-demiLargeur, demiHauteur, -demiProfondeur),
    Point3(-demiLargeur, -demiHauteur, demiProfondeur),
    Point3(demiLargeur, -demiHauteur, demiProfondeur),
    Point3(demiLargeur, demiHauteur, demiProfondeur),
    Point3(-demiLargeur, demiHauteur, demiProfondeur)
)

/** Chaque face : ses quatre sommets, sa normale, et si elle porte un ruban croisé. */
private data class Face(val coins: IntArray, val normale: Point3, val dessus: Boolean)

private val FACES = listOf(
    Face(intArrayOf(0, 1, 2, 3), Point3(0f, 0f, -1f), false),  // avant
    Face(intArrayOf(5, 4, 7, 6), Point3(0f, 0f, 1f), false),   // arrière
    Face(intArrayOf(4, 0, 3, 7), Point3(-1f, 0f, 0f), false),  // gauche
    Face(intArrayOf(1, 5, 6, 2), Point3(1f, 0f, 0f), false),   // droite
    Face(intArrayOf(4, 5, 1, 0), Point3(0f, -1f, 0f), true),   // dessus
    Face(intArrayOf(3, 2, 6, 7), Point3(0f, 1f, 0f), true)     // dessous
)

// Lumière fixe venant du haut-avant-gauche : c'est elle qui creuse le volume.
private val LUMIERE = Point3(-0.45f, -0.75f, -0.5f).let {
    val norme = sqrt(it.x * it.x + it.y * it.y + it.z * it.z)
    Point3(it.x / norme, it.y / norme, it.z / norme)
}

private fun cheminQuad(coins: List<Offset>): Path = Path().apply {
    moveTo(coins[0].x, coins[0].y)
    for (i in 1 until coins.size) lineTo(coins[i].x, coins[i].y)
    close()
}

private fun entre(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/**
 * Bande traversant un quadrilatère projeté. Interpoler sur les arêtes plutôt que de
 * dessiner un rectangle droit : le ruban épouse ainsi la perspective de la face.
 */
private fun bande(coins: List<Offset>, debut: Float, fin: Float, vertical: Boolean): Path {
    val (a, b, c, d) = listOf(coins[0], coins[1], coins[2], coins[3])
    return if (vertical) {
        cheminQuad(listOf(entre(a, b, debut), entre(a, b, fin), entre(d, c, fin), entre(d, c, debut)))
    } else {
        cheminQuad(listOf(entre(a, d, debut), entre(b, c, debut), entre(b, c, fin), entre(a, d, fin)))
    }
}

/** Dessine un pavé texturé « papier cadeau » : faces éclairées + rubans. */
private fun DrawScope.dessinerPave(
    centre: Offset,
    demiLargeur: Float,
    demiHauteur: Float,
    demiProfondeur: Float,
    rotationY: Float,
    rotationX: Float,
    rotationZ: Float = 0f,
    decalage: Point3 = Point3(0f, 0f, 0f),
    couleur: Color,
    couleurRuban: Color,
    distance: Float,
    opacite: Float = 1f,
    avecRubans: Boolean = true,
    /** De 0 à 1 : noircit la face du dessus pour qu'on voie l'intérieur de la boîte
     *  une fois le couvercle parti. Sans cela, la boîte reste visuellement fermée. */
    ouvert: Float = 0f
) {
    if (opacite <= 0f) return

    val transformes = sommets(demiLargeur, demiHauteur, demiProfondeur).map {
        it.tournerY(rotationY).tournerX(rotationX).tournerZ(rotationZ) + decalage
    }
    val projetes = transformes.map { it.projeter(centre, distance) }

    // Peintre : les faces lointaines d'abord, les proches par-dessus. Suffisant pour
    // un convexe comme un pavé, et sans le coût d'un tampon de profondeur.
    val ordonnees = FACES.sortedByDescending { face ->
        face.coins.map { transformes[it].z }.average()
    }

    ordonnees.forEach { face ->
        val normale = face.normale.tournerY(rotationY).tournerX(rotationX).tournerZ(rotationZ)
        // Face tournée vers l'arrière : inutile de la peindre, elle est masquée.
        if (normale.z > 0.02f) return@forEach

        val quad = face.coins.map { projetes[it] }
        val eclairement = 0.58f + 0.42f * max(
            0f,
            normale.x * LUMIERE.x + normale.y * LUMIERE.y + normale.z * LUMIERE.z
        )

        // La face du dessus (et elle seule) s'assombrit à l'ouverture : c'est le fond
        // de la boîte que l'on aperçoit une fois le couvercle envolé.
        val creux = if (face.dessus && face.normale.y < 0f) ouvert.coerceIn(0f, 1f) else 0f
        val teinte = 1f - 0.82f * creux

        drawPath(
            cheminQuad(quad),
            color = Color(
                red = (couleur.red * eclairement * teinte).coerceIn(0f, 1f),
                green = (couleur.green * eclairement * teinte).coerceIn(0f, 1f),
                blue = (couleur.blue * eclairement * teinte).coerceIn(0f, 1f),
                alpha = couleur.alpha
            ),
            alpha = opacite
        )

        if (!avecRubans) return@forEach
        val opaciteRuban = opacite * eclairement * (1f - creux)
        if (opaciteRuban <= 0.02f) return@forEach
        drawPath(bande(quad, 0.42f, 0.58f, vertical = true), couleurRuban, alpha = opaciteRuban)
        if (face.dessus) {
            drawPath(bande(quad, 0.42f, 0.58f, vertical = false), couleurRuban, alpha = opaciteRuban)
        }
    }
}

// ---------- Couleurs du cadeau ----------
// Rouge et blanc, quel que soit le thème : c'est le papier cadeau qu'on attend.
private val RougeCadeau = Color(0xFFE33B2A)
private val BlancCadeau = Color(0xFFFFFFFF)

/** Troisième teinte des confettis : sans elle, la gerbe n'est que rouge et blanche
 *  et se perd sur le fond sombre. */
private val AccentJaunePapier = Color(0xFFFFC845)

/** Assombrit une couleur en gardant sa teinte, pour le fond de la scène. */
private fun assombrir(couleur: Color, facteur: Float) =
    Color(couleur.red * facteur, couleur.green * facteur, couleur.blue * facteur, 1f)

// ---------- Confettis ----------

private const val NOMBRE_CONFETTIS = 110

private data class Confetti(
    val angle: Float,
    val portee: Float,
    val taille: Float,
    val vrille: Float,
    val teinte: Int,
    val retard: Float
)

// ---------- L'écran ----------

private val RougeNoeud = Color(0xFFC81E12)

private const val PREFS_CADEAU = "mon_shop_prefs"
private const val CLE_CADEAU_3D = "cadeau_en_relief"

/** Vrai pour la boîte en volume, faux pour la version plate. */
fun lireCadeau3D(context: android.content.Context): Boolean =
    context.getSharedPreferences(PREFS_CADEAU, android.content.Context.MODE_PRIVATE)
        .getBoolean(CLE_CADEAU_3D, true)

fun ecrireCadeau3D(context: android.content.Context, enRelief: Boolean) {
    context.getSharedPreferences(PREFS_CADEAU, android.content.Context.MODE_PRIVATE).edit()
        .putBoolean(CLE_CADEAU_3D, enRelief)
        .apply()
}

/** Point d'une courbe cubique, pour dessiner les boucles du nœud. */
private fun cubique(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
}

/**
 * Le contour d'une boucle, échantillonné en points.
 *
 * Deux arcs qui repartent du même point : c'est ce pincement au centre qui fait un nœud
 * plutôt qu'un rond. `sens` donne la boucle gauche ou la droite.
 */
private fun contourBoucle(rayon: Float, sens: Float, pas: Int = 11): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()
    // Aller : du cœur vers la pointe extérieure.
    for (i in 0..pas) {
        val f = i / pas.toFloat()
        points += cubique(0f, 0.55f, 2.05f, 1.75f, f) * rayon * sens to
            cubique(0f, -1.35f, -0.95f, -0.10f, f) * rayon
    }
    // Retour : par-dessous, plus serré, ce qui creuse la boucle.
    for (i in 1..pas) {
        val f = i / pas.toFloat()
        points += cubique(1.75f, 1.55f, 0.72f, 0f, f) * rayon * sens to
            cubique(-0.10f, 0.62f, 0.36f, 0f, f) * rayon
    }
    return points
}

/**
 * Le nœud du couvercle, construit dans l'espace comme le reste de la boîte.
 *
 * Ses boucles sont de vraies formes en trois dimensions, écartées de part et d'autre :
 * elles tournent, se raccourcissent et passent l'une devant l'autre en même temps que le
 * couvercle. Un nœud dessiné à plat par-dessus restait collé à l'écran pendant que la
 * boîte tournait dessous, et c'est précisément ce qui le trahissait.
 */
private fun DrawScope.dessinerNoeud3D(
    rayon: Float,
    sommetY: Float,
    rotationY: Float,
    rotationX: Float,
    rotationZ: Float,
    decalage: Point3,
    centre: Offset,
    distance: Float,
    opacite: Float
) {
    if (opacite <= 0.01f || rayon <= 0.5f) return

    fun transformer(p: Point3): Point3 =
        p.tournerY(rotationY).tournerX(rotationX).tournerZ(rotationZ) + decalage

    // Chaque pièce : ses points dans le repère du couvercle, et sa teinte.
    val pieces = mutableListOf<Pair<List<Point3>, Boolean>>()

    // Les deux pans, à plat sur le couvercle.
    listOf(-1f, 1f).forEach { sens ->
        pieces += listOf(
            Point3(0f, sommetY, 0f),
            Point3(sens * rayon * 1.15f, sommetY, rayon * 0.95f),
            Point3(sens * rayon * 0.70f, sommetY, rayon * 1.45f),
            Point3(0f, sommetY, rayon * 0.30f)
        ).map { transformer(it) } to false
    }

    // Les deux boucles, dressées et écartées : sans cet écart, elles disparaîtraient
    // toutes les deux ensemble dès que la boîte se présente de profil.
    listOf(-1f to -0.48f, 1f to 0.48f).forEach { (sens, ecart) ->
        pieces += contourBoucle(rayon, sens).map { (x, y) ->
            transformer(Point3(x, sommetY + y, 0f).tournerY(ecart))
        } to true
    }

    // Peintre : le plus lointain d'abord, comme pour les faces de la boîte.
    pieces.sortByDescending { (points, _) -> points.map { it.z }.average() }

    pieces.forEach { (points, estBoucle) ->
        val projetes = points.map { it.projeter(centre, distance) }
        val chemin = Path().apply {
            moveTo(projetes[0].x, projetes[0].y)
            projetes.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        // La profondeur moyenne assombrit la pièce : c'est ce dégradé entre les deux
        // boucles qui donne le volume, faute de pouvoir les éclairer face par face.
        val profondeur = points.map { it.z }.average().toFloat()
        val eclairement = (0.72f - profondeur / (rayon * 9f)).coerceIn(0.5f, 1f)
        drawPath(chemin, assombrir(RougeNoeud, eclairement), alpha = opacite)
        drawPath(
            chemin,
            BlancCadeau,
            alpha = opacite * (if (estBoucle) 0.9f else 0.6f),
            style = Stroke(width = rayon * 0.16f)
        )
    }

    // La bride qui serre les deux boucles, par-dessus tout : elle masque leur jonction.
    // Construite en volume comme le reste — un disque plat posé là restait face à
    // l'écran quand tout le reste tournait, et c'est ce qui sautait aux yeux.
    val largeurBride = rayon * 0.30f
    val hauteurBride = rayon * 0.52f
    val bride = listOf(
        Point3(-largeurBride, sommetY - rayon * 0.62f, 0f),
        Point3(largeurBride, sommetY - rayon * 0.62f, 0f),
        Point3(largeurBride, sommetY + rayon * 0.10f, 0f),
        Point3(-largeurBride, sommetY + rayon * 0.10f, 0f)
    ).map { transformer(Point3(it.x, it.y, it.z - hauteurBride * 0.35f)).projeter(centre, distance) }

    val cheminBride = Path().apply {
        moveTo(bride[0].x, bride[0].y)
        bride.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(cheminBride, assombrir(RougeNoeud, 0.88f), alpha = opacite)
    drawPath(
        cheminBride, BlancCadeau,
        alpha = opacite * 0.9f, style = Stroke(width = rayon * 0.14f)
    )
}

private val TailleScene = 300.dp

/**
 * La célébration, dans la version choisie par l'utilisateur.
 *
 * Les deux animations sont complètes et indépendantes : la plate a sa mise en scène,
 * celle en volume la sienne. Les mêler aurait donné une troisième version, moins bonne
 * que les deux.
 */
@Composable
fun EcranCadeauInstalle(item: CatalogItem, onTermine: () -> Unit) {
    val context = LocalContext.current
    // Relue à chaque ouverture : l'animation ne vit que le temps d'une célébration,
    // et l'utilisateur peut avoir changé d'avis entre deux installations.
    val enRelief = remember { lireCadeau3D(context) }
    if (enRelief) EcranCadeau3D(item, onTermine) else EcranCadeau2D(item, onTermine)
}

@Composable
private fun EcranCadeau3D(item: CatalogItem, onTermine: () -> Unit) {
    val meta = rememberMetadonneesAffichees(item)
    val context = LocalContext.current
    val densite = LocalDensity.current

    val chute = remember { Animatable(0f) }        // 0 = hors champ, 1 = posé
    val ecrasement = remember { Animatable(0f) }   // rebond à l'atterrissage
    val rotation = remember { Animatable(0f) }     // toupie qui ralentit jusqu'à l'arrêt
    val ouverture = remember { Animatable(0f) }    // couvercle qui saute
    val sortie = remember { Animatable(0f) }       // jaquette qui monte en tournant
    val texte = remember { Animatable(0f) }

    val rayons = rememberInfiniteTransition(label = "rayons").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "angleRayons"
    )

    val confettis = remember {
        val tirage = Random(11)
        List(NOMBRE_CONFETTIS) {
            Confetti(
                angle = tirage.nextFloat() * 360f,
                // Portées très étalées : la gerbe doit traverser l'écran, pas se
                // contenter d'un halo autour de la boîte.
                portee = 0.35f + tirage.nextFloat() * 1.5f,
                taille = 7f + tirage.nextFloat() * 13f,
                vrille = tirage.nextFloat() * 1100f - 550f,
                teinte = tirage.nextInt(3),
                retard = tirage.nextFloat() * 0.14f
            )
        }
    }

    var dejaTermine by remember { mutableStateOf(false) }
    val fermer = {
        if (!dejaTermine) {
            dejaTermine = true
            onTermine()
        }
    }

    BackHandler { fermer() }

    LaunchedEffectCadeau(
        chute = chute,
        ecrasement = ecrasement,
        rotation = rotation,
        ouverture = ouverture,
        sortie = sortie,
        texte = texte,
        cle = item.nom,
        fermer = fermer
    )

    val blanc = BlancCadeau
    // Fond assombri en dégradé : un cadeau rouge sur le rouge du thème serait
    // invisible. La teinte du thème reste lisible, mais en retrait.
    val fond = Brush.radialGradient(
        colors = listOf(assombrir(RougeJeu, 0.42f), assombrir(RougeJeu, 0.16f))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fond)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = fermer
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(modifier = Modifier.size(TailleScene), contentAlignment = Alignment.Center) {

                // --- Fond : ombre portée, halo, rayons, boîte
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centre = Offset(size.width / 2f, size.height * 0.54f)
                    val distance = size.minDimension * 1.5f
                    val avanceOuverture = ouverture.value.coerceIn(0f, 1f)

                    // Hauteur de chute, en unités du modèle : la boîte arrive de très haut.
                    val hauteur = (1f - chute.value) * size.minDimension * 1.6f

                    if (avanceOuverture > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(blanc.copy(alpha = 0.40f), Color.Transparent),
                                center = centre,
                                radius = size.minDimension * 0.55f * avanceOuverture
                            ),
                            radius = size.minDimension * 0.55f * avanceOuverture,
                            center = centre
                        )
                        rotate(degrees = rayons.value, pivot = centre) {
                            val longueur = size.minDimension * 0.66f * avanceOuverture
                            for (i in 0 until 14) {
                                val angle = Math.PI * 2 * i / 14
                                val ouvert = 0.05
                                drawPath(
                                    Path().apply {
                                        moveTo(centre.x, centre.y)
                                        lineTo(
                                            centre.x + (longueur * cos(angle - ouvert)).toFloat(),
                                            centre.y + (longueur * sin(angle - ouvert)).toFloat()
                                        )
                                        lineTo(
                                            centre.x + (longueur * cos(angle + ouvert)).toFloat(),
                                            centre.y + (longueur * sin(angle + ouvert)).toFloat()
                                        )
                                        close()
                                    },
                                    color = blanc,
                                    alpha = 0.17f * avanceOuverture
                                )
                            }
                        }
                    }

                    // Ombre au sol : elle se resserre quand la boîte descend, ce qui
                    // ancre la chute au lieu de la laisser flotter.
                    val proximite = chute.value.coerceIn(0f, 1f)
                    val largeurOmbre = size.minDimension * (0.16f + 0.20f * proximite)
                    drawOval(
                        color = Color.Black,
                        alpha = 0.10f + 0.16f * proximite,
                        topLeft = Offset(centre.x - largeurOmbre, centre.y + size.minDimension * 0.20f),
                        size = Size(largeurOmbre * 2f, largeurOmbre * 0.42f)
                    )

                    if (chute.value <= 0.001f) return@Canvas

                    // Écrasement à l'impact : la boîte s'aplatit puis retrouve sa forme.
                    val aplati = 1f - 0.22f * ecrasement.value
                    val elargi = 1f + 0.16f * ecrasement.value
                    val cote = size.minDimension * 0.17f

                    dessinerPave(
                            centre = centre,
                            demiLargeur = cote * elargi,
                            demiHauteur = cote * aplati,
                            demiProfondeur = cote * elargi,
                            rotationY = rotation.value,
                            rotationX = 0.30f,
                            decalage = Point3(0f, -hauteur + cote * (1f - aplati), 0f),
                            couleur = RougeCadeau,
                            couleurRuban = BlancCadeau,
                            distance = distance,
                            opacite = 1f,
                            // Le fond s'assombrit dès que le couvercle décolle.
                        ouvert = (avanceOuverture * 3f).coerceIn(0f, 1f)
                    )
                }

                // --- La jaquette sort de la boîte en pivotant face à l'écran.
                // rotationY + cameraDistance : c'est Compose qui applique ici la
                // perspective, inutile de la recalculer à la main.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            val avance = sortie.value.coerceIn(0f, 1.4f)
                            val visible = avance.coerceIn(0f, 1f)
                            cameraDistance = 14f * density
                            rotationY = 540f * (1f - visible)
                            scaleX = avance
                            scaleY = avance
                            translationY = -with(densite) { 104.dp.toPx() } * visible
                            alpha = visible
                        }
                ) {
                    Card(
                        modifier = Modifier.size(width = 104.dp, height = 132.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BlancCreme),
                        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
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
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }

                // --- Devant : couvercle qui s'envole, puis confettis.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centre = Offset(size.width / 2f, size.height * 0.54f)
                    val distance = size.minDimension * 1.5f
                    val cote = size.minDimension * 0.17f
                    val avance = ouverture.value.coerceIn(0f, 1.25f)

                    if (chute.value > 0.001f) {
                        val hauteur = (1f - chute.value) * size.minDimension * 1.6f
                        val aplati = 1f - 0.22f * ecrasement.value
                        val elargi = 1f + 0.16f * ecrasement.value
                        // Le couvercle repose sur la boîte, puis part vers le haut en
                        // basculant : la même mécanique 3D, avec un pavé très plat.
                        val envol = avance * size.minDimension * 0.85f
                        val opaciteCouvercle = (1f - (avance - 0.55f) / 0.7f).coerceIn(0f, 1f)

                        dessinerPave(
                            centre = centre,
                            demiLargeur = cote * 1.10f * elargi,
                            demiHauteur = cote * 0.20f,
                            demiProfondeur = cote * 1.10f * elargi,
                            rotationY = rotation.value,
                            rotationX = 0.30f + 0.9f * avance,
                            rotationZ = 0.55f * avance,
                            decalage = Point3(
                                x = 0.55f * envol,
                                // Demi-hauteur de la boîte + demi-épaisseur du couvercle :
                                // il se pose ainsi au ras, sans flotter ni s'enfoncer.
                                y = -hauteur - cote * (aplati + 0.20f) - envol,
                                z = 0f
                            ),
                            couleur = RougeCadeau,
                            couleurRuban = BlancCadeau,
                            distance = distance,
                            opacite = opaciteCouvercle
                        )

                        // Le nœud partage le repère du couvercle : mêmes rotations,
                        // même décalage, donc il tourne et s'envole avec lui.
                        dessinerNoeud3D(
                            rayon = cote * 0.40f,
                            sommetY = -cote * 0.20f,
                            rotationY = rotation.value,
                            rotationX = 0.30f + 0.9f * avance,
                            rotationZ = 0.55f * avance,
                            decalage = Point3(
                                x = 0.55f * envol,
                                y = -hauteur - cote * (aplati + 0.20f) - envol,
                                z = 0f
                            ),
                            centre = centre,
                            distance = distance,
                            opacite = opaciteCouvercle
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = texte.value.coerceIn(0f, 1f)
                        translationY = with(densite) { 20.dp.toPx() } * (1f - texte.value)
                        val echelle = 0.86f + 0.14f * texte.value
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
                Text("Tap anywhere to continue", color = blanc.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }

        // Confettis dessinés par-dessus tout l'écran, et non dans la scène : à
        // l'intérieur, ils seraient coupés net au bord de la boîte de 300 dp, alors
        // que toute la gerbe consiste justement à traverser l'écran.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val avance = ouverture.value.coerceIn(0f, 1.25f)
            if (avance <= 0f) return@Canvas
            val opacite = (1f - (avance - 0.55f) / 0.7f).coerceIn(0f, 1f)
            if (opacite <= 0f) return@Canvas

            val depart = Offset(size.width / 2f, size.height * 0.42f)
            val etalement = size.minDimension

            confettis.forEach { confetti ->
                val local = ((avance - confetti.retard) / (1f - confetti.retard)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val angle = Math.toRadians(confetti.angle.toDouble())
                val rayon = etalement * 0.62f * confetti.portee * local
                // Chute quadratique : jaillissement franc, puis la gravité l'emporte.
                val gravite = etalement * 0.55f * local * local * confetti.portee
                val position = Offset(
                    depart.x + (rayon * cos(angle)).toFloat(),
                    depart.y + (rayon * sin(angle)).toFloat() + gravite
                )
                val couleur = when (confetti.teinte) {
                    0 -> BlancCadeau
                    1 -> RougeCadeau
                    else -> AccentJaunePapier
                }
                // La largeur oscille : le confetti se présente tantôt de biais,
                // tantôt de face, comme une paillette qui tournoie.
                val largeur = confetti.taille * abs(cos(confetti.vrille * local * 0.05f))
                rotate(degrees = confetti.vrille * local, pivot = position) {
                    drawRect(
                        color = couleur,
                        topLeft = Offset(position.x - largeur / 2f, position.y - confetti.taille / 4f),
                        size = Size(max(largeur, 1.5f), confetti.taille / 2f),
                        alpha = opacite
                    )
                }
            }
        }
    }
}

/**
 * Déroulé de l'animation, isolé pour que la composition reste lisible : chute,
 * impact, hésitation, ouverture, révélation, titre.
 */
/** Nombre de tours effectués avant l'arrêt, et angle final choisi pour montrer trois
 *  faces à la fois — de face, la boîte n'aurait plus l'air d'un volume. */
private const val TOURS_TOUPIE = 7
private const val ANGLE_ARRET = 0.52f

/**
 * Décélération très marquée : la boîte part comme une toupie lancée à pleine vitesse
 * et s'immobilise en douceur. Une courbe standard freinerait trop tôt, l'effet
 * « toupie qui s'arrête » vient justement de cette longue traîne.
 */
private val Ralentissement = CubicBezierEasing(0f, 0.88f, 0.18f, 1f)

@Composable
private fun LaunchedEffectCadeau(
    // Type complet plutôt qu'une projection étoile : `animateTo` manipule le vecteur
    // d'animation dans ses paramètres, et une projection le rendrait inappelable.
    chute: Animatable<Float, AnimationVector1D>,
    ecrasement: Animatable<Float, AnimationVector1D>,
    rotation: Animatable<Float, AnimationVector1D>,
    ouverture: Animatable<Float, AnimationVector1D>,
    sortie: Animatable<Float, AnimationVector1D>,
    texte: Animatable<Float, AnimationVector1D>,
    cle: String,
    fermer: () -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(cle) {
        SoundEffects.menuOuvrir()

        // La chute et l'impact se déroulent pendant que la boîte tourne déjà : les
        // deux mouvements sont indépendants, d'où la coroutine séparée.
        launch {
            chute.animateTo(1f, tween(520, easing = LinearOutSlowInEasing))
            SoundEffects.clic()
            ecrasement.snapTo(1f)
            ecrasement.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }

        // 1) toupie : lancée très vite, elle ralentit jusqu'à s'arrêter net.
        rotation.animateTo(
            (Math.PI * 2).toFloat() * TOURS_TOUPIE + ANGLE_ARRET,
            tween(2200, easing = Ralentissement)
        )

        // 2) court temps d'arrêt : c'est lui qui fait attendre l'ouverture.
        delay(160)

        // 3) le couvercle saute et la gerbe de confettis part.
        SoundEffects.cadeauOuvert()
        launch { ouverture.animateTo(1.25f, tween(1000, easing = LinearOutSlowInEasing)) }

        // 4) la jaquette monte pendant que le couvercle s'envole encore : les deux
        // mouvements se chevauchent, c'est ce qui rend la scène vivante.
        delay(200)
        SoundEffects.recompense()
        sortie.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        )
        texte.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
        delay(2400)
        fermer()
    }
}
