package com.monshop.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Écran d'apparence : catalogue des thèmes livrés, création de ses propres palettes,
 * et réglages d'habillage.
 *
 * Séparé des réglages parce qu'il s'y regarde autant qu'il s'y règle : un carré
 * chromatique et une grille d'aperçus demandent de la place, et cohabitaient mal avec
 * une liste de cases à cocher.
 */
@Composable
fun EcranStyle(
    themes: List<ThemeOption>,
    themeActuel: ThemeOption,
    onChoisirTheme: (ThemeOption) -> Unit,
    onCreerTheme: (ThemeOption) -> Unit,
    onSupprimerTheme: (ThemeOption) -> Unit,
    motifActif: Boolean,
    onToggleMotif: (Boolean) -> Unit,
    cadeauEnRelief: Boolean,
    onToggleCadeau: (Boolean) -> Unit,
    fondEcran: ReglagesFondEcran,
    onChoisirImage: () -> Unit,
    onEffacerFond: () -> Unit,
    onVoileChange: (Float) -> Unit,
    onVoileFinalise: (Float) -> Unit,
    messageFond: String,
    onRetour: () -> Unit
) {
    BackHandler(onBack = onRetour)

    Column(modifier = Modifier.fillMaxSize().background(FondClair)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RougeJeu)
                .statusBarsPadding()
                .height(HauteurBanniere)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { SoundEffects.menuFermer(); onRetour() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BlancJeu)
            }
            Text(
                "Style",
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
            SectionReglagesPublique(titre = "Themes") {
                GrilleThemes(
                    themes = themes,
                    themeActuel = themeActuel,
                    onChoisir = onChoisirTheme,
                    onSupprimer = onSupprimerTheme
                )
            }

            SectionReglagesPublique(titre = "Create your own") {
                CreateurTheme(onEnregistrer = onCreerTheme)
            }

            SectionReglagesPublique(titre = "Interface") {
                Text("Wallpaper", color = TexteFonce, fontWeight = FontWeight.Bold)
                Text(
                    "Show one of your pictures behind the game list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TexteFonce.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(10.dp))

                if (fondEcran.uri != null) {
                    // Aperçu voilé exactement comme il le sera derrière la liste.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = fondEcran.uri,
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
                    Spacer(Modifier.height(10.dp))
                    Text("Fade", style = MaterialTheme.typography.bodySmall, color = TexteFonce)
                    Slider(
                        value = fondEcran.voile,
                        onValueChange = onVoileChange,
                        onValueChangeFinished = { onVoileFinalise(fondEcran.voile) },
                        valueRange = 0f..0.92f,
                        colors = SliderDefaults.colors(
                            thumbColor = RougeJeu,
                            activeTrackColor = RougeJeu
                        )
                    )
                }

                // Le fond d'écran du système a été retiré : Android en interdit la
                // lecture aux applications ordinaires, l'option ne pouvait qu'échouer.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { SoundEffects.clic(); onChoisirImage() },
                        colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
                    ) {
                        Text(if (fondEcran.uri == null) "Choose a picture" else "Change picture")
                    }
                }
                if (fondEcran.uri != null) {
                    TextButton(onClick = { SoundEffects.clic(); onEffacerFond() }) {
                        Text("Remove wallpaper", color = RougeJeu)
                    }
                }
                if (messageFond.isNotBlank()) {
                    Text(
                        messageFond,
                        style = MaterialTheme.typography.bodySmall,
                        color = TexteFonce.copy(alpha = 0.75f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = RougeJeu.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background pattern", color = TexteFonce, fontWeight = FontWeight.Bold)
                        Text(
                            "The faint stars and hearts behind the game list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TexteFonce.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = motifActif,
                        onCheckedChange = { SoundEffects.clic(); onToggleMotif(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BlancJeu,
                            checkedTrackColor = RougeJeu
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = RougeJeu.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gift animation", color = TexteFonce, fontWeight = FontWeight.Bold)
                        Text(
                            if (cadeauEnRelief)
                                "A box in the round, spinning as it lands."
                            else
                                "A flat box, drawn like a sticker.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TexteFonce.copy(alpha = 0.7f)
                        )
                    }
                    ChoixCadeau(enRelief = cadeauEnRelief, onChoisir = onToggleCadeau)
                }
            }
        }
    }
}

/** Deux pastilles au lieu d'un interrupteur : « 3D activé » ne dirait pas ce qu'est
 *  l'autre choix, alors que les deux noms côte à côte se comprennent seuls. */
@Composable
private fun ChoixCadeau(enRelief: Boolean, onChoisir: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(false to "2D", true to "3D").forEach { (valeur, libelle) ->
            val actif = valeur == enRelief
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (actif) RougeJeu else Color.Transparent)
                    .border(1.dp, RougeJeu.copy(alpha = if (actif) 1f else 0.35f), RoundedCornerShape(50))
                    .clickable { SoundEffects.clic(); onChoisir(valeur) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    libelle,
                    color = if (actif) BlancJeu else RougeJeu,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** Carte de section, identique visuellement à celle des réglages. */
@Composable
private fun SectionReglagesPublique(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BlancCreme),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, RougeJeu.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(titre, color = RougeJeu, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            contenu()
        }
    }
}

/**
 * Catalogue des thèmes en vignettes plutôt qu'en liste : une palette se juge à l'œil,
 * pas à son nom. Chaque vignette montre le bandeau, le fond de carte et l'accent, soit
 * exactement ce que le thème colore dans l'appli.
 */
@Composable
private fun GrilleThemes(
    themes: List<ThemeOption>,
    themeActuel: ThemeOption,
    onChoisir: (ThemeOption) -> Unit,
    onSupprimer: (ThemeOption) -> Unit
) {
    // Grille à deux colonnes construite à la main : une LazyVerticalGrid dans une
    // colonne déjà défilante impose une hauteur fixe, ce que le nombre variable de
    // thèmes rend impossible à choisir.
    themes.chunked(2).forEach { rangee ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rangee.forEach { theme ->
                VignetteTheme(
                    theme = theme,
                    actif = theme.id == themeActuel.id,
                    modifier = Modifier.weight(1f),
                    onChoisir = { SoundEffects.clic(); onChoisir(theme) },
                    onSupprimer = { SoundEffects.clic(); onSupprimer(theme) }
                )
            }
            // Nombre impair : la dernière vignette ne doit pas s'étirer sur toute la largeur.
            if (rangee.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun VignetteTheme(
    theme: ThemeOption,
    actif: Boolean,
    modifier: Modifier = Modifier,
    onChoisir: () -> Unit,
    onSupprimer: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.fond)
            .border(
                width = if (actif) 3.dp else 1.dp,
                color = if (actif) theme.primaire else TexteFonce.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onChoisir)
    ) {
        // Aperçu : bandeau, carte et pastille d'accent, dans les vraies couleurs.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(theme.primaire),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "MOONSHOP",
                color = theme.texteBandeau,
                fontFamily = PoliceMoonshop,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
            if (actif) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Active theme",
                    tint = theme.texteBandeau,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(18.dp)
                )
            }
        }
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Une seule pastille : le fond de carte. L'accent y figurait aussi, mais
            // il ne correspondait à rien de visible dans l'appli et se lisait comme
            // une couleur du thème qu'on n'aurait choisie nulle part.
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.texte.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.weight(1f))
            if (estThemePersonnalise(theme)) {
                IconButton(onClick = onSupprimer, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete this theme",
                        tint = theme.texte.copy(alpha = 0.55f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Text(
            theme.nom,
            color = theme.texte,
            fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
        )
    }
}

// ---------- Création d'un thème ----------

// Décalage de teinte de l'accent, fixé plutôt que proposé au choix : il ne colore que
// des détails (badges, coche d'installé) et l'offrir en option laissait croire à un
// réglage visible, sans effet perceptible.
private const val DECALAGE_ACCENT = 180f

@Composable
private fun CreateurTheme(onEnregistrer: (ThemeOption) -> Unit) {
    var nom by remember { mutableStateOf("") }
    var teinte by remember { mutableStateOf(210f) }
    var saturation by remember { mutableStateOf(0.72f) }
    var valeur by remember { mutableStateOf(0.80f) }
    var sombre by remember { mutableStateOf(false) }

    val primaire = Color.hsv(teinte, saturation, valeur)
    val accent = Color.hsv((teinte + DECALAGE_ACCENT) % 360f, 0.85f, 0.98f)
    val apercu = remember(teinte, saturation, valeur, sombre, nom) {
        themeDepuisChoix(
            identifiant = "apercu",
            nom = nom.ifBlank { "Your theme" },
            teinte = teinte,
            primaire = primaire,
            accent = accent,
            sombre = sombre
        )
    }

    Text(
        "Drag inside the square to pick a colour, and the strip below to change the hue.",
        style = MaterialTheme.typography.bodyMedium,
        color = TexteFonce
    )
    Spacer(Modifier.height(14.dp))

    CarreChromatique(
        teinte = teinte,
        saturation = saturation,
        valeur = valeur,
        onChangement = { s, v -> saturation = s; valeur = v }
    )
    Spacer(Modifier.height(12.dp))
    BandeTeinte(teinte = teinte, onChangement = { teinte = it })

    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Dark background", modifier = Modifier.weight(1f), color = TexteFonce)
        Switch(
            checked = sombre,
            onCheckedChange = { SoundEffects.clic(); sombre = it },
            colors = SwitchDefaults.colors(checkedThumbColor = BlancJeu, checkedTrackColor = RougeJeu)
        )
    }

    Spacer(Modifier.height(14.dp))
    ApercuTheme(apercu, nom.ifBlank { "Your theme" })

    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = nom,
        onValueChange = { nom = it.take(24) },
        singleLine = true,
        label = { Text("Name") },
        placeholder = { Text("My theme") },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RougeJeu,
            focusedLabelColor = RougeJeu,
            cursorColor = RougeJeu,
            focusedTextColor = TexteFonce,
            unfocusedTextColor = TexteFonce
        )
    )

    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            SoundEffects.clic()
            onEnregistrer(
                themeDepuisChoix(
                    identifiant = nouvelIdentifiantTheme(),
                    nom = nom.trim().ifBlank { "My theme" },
                    teinte = teinte,
                    primaire = primaire,
                    accent = accent,
                    sombre = sombre
                )
            )
            nom = ""
        },
        colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
    ) {
        Text("Save and apply")
    }
}

/**
 * Carré chromatique : la saturation sur l'axe horizontal, la luminosité sur le vertical.
 *
 * Deux dégradés superposés suffisent à le peindre — blanc vers la teinte pure, puis
 * transparent vers le noir — et le point choisi se lit directement en coordonnées, sans
 * calcul inverse.
 */
@Composable
private fun CarreChromatique(
    teinte: Float,
    saturation: Float,
    valeur: Float,
    onChangement: (Float, Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, TexteFonce.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    onChangement(
                        (position.x / size.width).coerceIn(0f, 1f),
                        1f - (position.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { changement, _ ->
                    changement.consume()
                    onChangement(
                        (changement.position.x / size.width).coerceIn(0f, 1f),
                        1f - (changement.position.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
    ) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.White, Color.hsv(teinte, 1f, 1f)))
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
        )
        val centre = Offset(saturation * size.width, (1f - valeur) * size.height)
        // Double cercle : un liseré blanc et un noir, pour rester visible sur toute
        // la surface, du blanc pur au noir.
        drawCircle(Color.White, radius = 11f, center = centre, style = Stroke(width = 4f))
        drawCircle(Color.Black, radius = 11f, center = centre, style = Stroke(width = 1.5f))
    }
}

/** Bande de teintes, du rouge au rouge en passant par tout le cercle chromatique. */
@Composable
private fun BandeTeinte(teinte: Float, onChangement: (Float) -> Unit) {
    val couleurs = remember { (0..360 step 30).map { Color.hsv(it.toFloat() % 360f, 1f, 1f) } }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, TexteFonce.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    onChangement((position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { changement, _ ->
                    changement.consume()
                    onChangement((changement.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(couleurs))
        val x = (teinte / 360f) * size.width
        drawLine(
            color = Color.White,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 5f
        )
        drawLine(
            color = Color.Black,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.5f
        )
    }
}

/** Aperçu réaliste : le bandeau, une carte de jeu et un bouton, dans le thème en cours. */
@Composable
private fun ApercuTheme(theme: ThemeOption, nom: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.fond)
            .border(1.dp, TexteFonce.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(32.dp).background(theme.primaire),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                nom,
                color = theme.texteBandeau,
                fontFamily = PoliceMoonshop,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.primaire.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Zelda.iso", color = theme.texte, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("GameCube", color = theme.primaire, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.primaire)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Install", color = theme.texteBandeau, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
