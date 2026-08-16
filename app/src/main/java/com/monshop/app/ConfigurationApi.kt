package com.monshop.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Le catalogue des services que Moonshop interroge.
 *
 * Rassemblés ici pour que l'assistant de première ouverture et la page des réglages
 * disent exactement la même chose : deux textes séparés auraient fini par diverger, et
 * l'utilisateur aurait lu deux explications différentes du même service.
 */
enum class ServiceApi(
    val nom: String,
    /** Ce que l'appli perd sans lui — dit du point de vue de l'utilisateur. */
    val role: String,
    /** Pourquoi il faut quelque chose de personnel plutôt que rien. */
    val pourquoi: String,
    val lien: String?,
    val libelleLien: String,
    val icone: ImageVector,
    /** Teinte du pastillage, choisie proche de l'identité du service. */
    val couleur: Color
) {
    CONSOLE(
        nom = "Moonshop srv",
        role = "Your PC. This is where the games come from.",
        pourquoi = "Open Moonshop srv on your computer and press Launch — it shows a " +
            "six-letter code. Type it here and the console finds your PC on its own, " +
            "at home or anywhere else.",
        lien = null,
        libelleLien = "",
        icone = Icons.Default.Computer,
        couleur = Color(0xFFD8452F)
    ),
    DRIVE(
        nom = "Google Drive",
        role = "An optional second source, when the PC is off.",
        pourquoi = "Install from a shared Drive folder instead of your computer. " +
            "The key is free and stays on this console; without it, Moonshop simply " +
            "uses your PC.",
        lien = SourceDrive.URL_CLE_API,
        libelleLien = "Create a key on Google Cloud",
        icone = Icons.Default.Cloud,
        couleur = Color(0xFF2E7CF6)
    ),
    IGDB(
        nom = "IGDB",
        role = "Descriptions, release year, genre, studio and rating.",
        pourquoi = "Moonshop asks for your own account rather than shipping one: a key " +
            "written into the app can be read by anyone who opens it, then spent or " +
            "revoked. Without this, your games still install — they just show no " +
            "description, year or rating. It is free and takes two minutes.",
        lien = "https://dev.twitch.tv/console/apps/create",
        libelleLien = "Create an app on the Twitch console",
        icone = Icons.Default.MenuBook,
        couleur = Color(0xFF8A5CF0)
    ),
    STEAMGRIDDB(
        nom = "SteamGridDB",
        role = "Cover art, banners and logos for your games.",
        pourquoi = "Same reasoning, and this is the one you will notice: without a key " +
            "your library shows filenames on plain tiles instead of cover art. Free, " +
            "and granted straight away.",
        lien = "https://www.steamgriddb.com/profile/preferences/api",
        libelleLien = "Get your key on SteamGridDB",
        icone = Icons.Default.Collections,
        couleur = Color(0xFF1FA9A0)
    )
}

/** Le pastillage d'un service : un carré teinté qui lui sert de logo. */
@Composable
fun PastilleService(service: ServiceApi, taille: androidx.compose.ui.unit.Dp = 42.dp) {
    Box(
        modifier = Modifier
            .size(taille)
            .clip(RoundedCornerShape(taille / 3.4f))
            .background(service.couleur.copy(alpha = 0.16f))
            .border(1.dp, service.couleur.copy(alpha = 0.45f), RoundedCornerShape(taille / 3.4f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            service.icone,
            contentDescription = service.nom,
            tint = service.couleur,
            modifier = Modifier.size(taille * 0.52f)
        )
    }
}

/** État d'un service : renseigné par l'utilisateur, ou pas encore. */
enum class EtatService(val etiquette: String) {
    RENSEIGNE("Ready"),
    ABSENT("Not set")
}

@Composable
fun EtiquetteEtat(etat: EtatService) {
    val couleur = when (etat) {
        EtatService.RENSEIGNE -> Color(0xFF2E9E5B)
        EtatService.ABSENT -> TexteFonce.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(couleur.copy(alpha = 0.14f))
            .border(1.dp, couleur.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(etat.etiquette, color = couleur, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Le lien « où l'obtenir », identique dans l'assistant et dans les réglages. */
@Composable
fun LienService(service: ServiceApi) {
    val context = LocalContext.current
    val adresse = service.lien ?: return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { SoundEffects.clic(); ouvrirLien(context, adresse) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = RougeJeu, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            service.libelleLien,
            color = RougeJeu,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Champ de saisie d'une clé, au style commun aux deux écrans. */
@Composable
fun ChampCle(
    valeur: String,
    onValeur: (String) -> Unit,
    etiquette: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onValeur,
        label = { Text(etiquette) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RougeJeu,
            unfocusedBorderColor = RougeJeu.copy(alpha = 0.35f),
            cursorColor = RougeJeu,
            focusedTextColor = TexteFonce,
            unfocusedTextColor = TexteFonce,
            focusedLabelColor = RougeJeu
        )
    )
}

/**
 * Page dédiée aux services et à leurs clés.
 *
 * Séparée des réglages parce qu'elle se lit autant qu'elle se règle : chaque service y
 * explique ce qu'il apporte et où obtenir sa clé, ce qui tient mal entre deux cases à
 * cocher. C'est aussi ici qu'on revient après avoir sauté une étape de l'assistant.
 */
@Composable
fun EcranClesApi(
    codeConsole: String,
    onCodeConsole: (String) -> Unit,
    reglagesDrive: ReglagesDrive,
    onDrive: (ReglagesDrive) -> Unit,
    cles: ClesApi,
    onCles: (ClesApi) -> Unit,
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
                "API",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Moonshop carries no keys of its own — one written into the app could be " +
                    "read, spent or revoked by anyone. Yours stay on this console.",
                color = TexteFonce.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )
            SectionClesApi(codeConsole, onCodeConsole, reglagesDrive, onDrive, cles, onCles)
        }
    }
}

/** Le corps de la page : un service par carte, dépliable. */
@Composable
fun SectionClesApi(
    codeConsole: String,
    onCodeConsole: (String) -> Unit,
    reglagesDrive: ReglagesDrive,
    onDrive: (ReglagesDrive) -> Unit,
    cles: ClesApi,
    onCles: (ClesApi) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CarteService(
            service = ServiceApi.CONSOLE,
            etat = if (codeConsole.isNotBlank()) EtatService.RENSEIGNE else EtatService.ABSENT
        ) {
            var saisie by remember(codeConsole) { mutableStateOf(codeConsole) }
            ChampCle(saisie, { saisie = it.uppercase() }, "Six-letter code")
            BoutonEnregistrer(actif = saisie != codeConsole) { onCodeConsole(saisie) }
        }

        CarteService(
            service = ServiceApi.DRIVE,
            etat = if (reglagesDrive.cleApi.isNotBlank()) EtatService.RENSEIGNE else EtatService.ABSENT
        ) {
            var saisie by remember(reglagesDrive.cleApi) { mutableStateOf(reglagesDrive.cleApi) }
            ChampCle(saisie, { saisie = it }, "Google API key")
            BoutonEnregistrer(actif = saisie != reglagesDrive.cleApi) {
                onDrive(reglagesDrive.copy(cleApi = saisie.trim()))
            }
        }

        CarteService(
            service = ServiceApi.IGDB,
            etat = if (cles.igdbId.isNotBlank() && cles.igdbSecret.isNotBlank())
                EtatService.RENSEIGNE else EtatService.ABSENT
        ) {
            var id by remember(cles.igdbId) { mutableStateOf(cles.igdbId) }
            var secret by remember(cles.igdbSecret) { mutableStateOf(cles.igdbSecret) }
            ChampCle(id, { id = it }, "Client ID")
            Spacer(Modifier.height(8.dp))
            ChampCle(secret, { secret = it }, "Client secret")
            BoutonEnregistrer(actif = id != cles.igdbId || secret != cles.igdbSecret) {
                onCles(cles.copy(igdbId = id.trim(), igdbSecret = secret.trim()))
            }
        }

        CarteService(
            service = ServiceApi.STEAMGRIDDB,
            etat = if (cles.steamgriddb.isNotBlank()) EtatService.RENSEIGNE else EtatService.ABSENT
        ) {
            var saisie by remember(cles.steamgriddb) { mutableStateOf(cles.steamgriddb) }
            ChampCle(saisie, { saisie = it }, "API key")
            BoutonEnregistrer(actif = saisie != cles.steamgriddb) {
                onCles(cles.copy(steamgriddb = saisie.trim()))
            }
        }
    }
}

/** Carte d'un service : repliée elle ne montre que son état, dépliée elle se règle. */
@Composable
private fun CarteService(
    service: ServiceApi,
    etat: EtatService,
    contenu: @Composable ColumnScope.() -> Unit
) {
    var depliee by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(BlancCreme)
            .border(1.dp, service.couleur.copy(alpha = 0.3f), forme)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { SoundEffects.clic(); depliee = !depliee }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastilleService(service)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    service.nom,
                    color = TexteFonce,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    service.role,
                    color = TexteFonce.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            EtiquetteEtat(etat)
        }

        if (depliee) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(
                    service.pourquoi,
                    color = TexteFonce.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
                LienService(service)
                Spacer(Modifier.height(4.dp))
                contenu()
            }
        }
    }
}

@Composable
private fun BoutonEnregistrer(actif: Boolean, onClic: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { SoundEffects.clic(); onClic() },
        enabled = actif,
        colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu)
    ) {
        Text("Save")
    }
}
