package com.monshop.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * L'assistant de première ouverture.
 *
 * Une appli qui réclame quatre identifiants dans un écran de réglages est une appli qu'on
 * désinstalle. Ici chaque service est présenté seul, avec ce qu'il apporte, l'endroit où
 * l'obtenir, et un bouton pour passer : rien n'est obligatoire, et l'ordre va du plus
 * utile au plus accessoire.
 */
@Composable
fun EcranAssistant(
    codeConsole: String,
    onCodeConsole: (String) -> Unit,
    reglagesDrive: ReglagesDrive,
    onDrive: (ReglagesDrive) -> Unit,
    cles: ClesApi,
    onCles: (ClesApi) -> Unit,
    onTerminer: () -> Unit
) {
    val services = ServiceApi.values()
    // L'étape 0 accueille ; les suivantes correspondent aux services, dans l'ordre.
    val nombreEtapes = services.size + 1
    var etape by remember { mutableStateOf(0) }

    var code by remember { mutableStateOf(codeConsole) }
    var cleDrive by remember { mutableStateOf(reglagesDrive.cleApi) }
    var igdbId by remember { mutableStateOf(cles.igdbId) }
    var igdbSecret by remember { mutableStateOf(cles.igdbSecret) }
    var sgdb by remember { mutableStateOf(cles.steamgriddb) }

    /** Écrit ce que l'étape courante a recueilli. Sauter n'écrit rien. */
    fun enregistrerEtape() {
        when (etape) {
            1 -> if (code.isNotBlank() && code != codeConsole) onCodeConsole(code)
            2 -> if (cleDrive != reglagesDrive.cleApi) onDrive(reglagesDrive.copy(cleApi = cleDrive.trim()))
            3 -> if (igdbId != cles.igdbId || igdbSecret != cles.igdbSecret) {
                onCles(cles.copy(igdbId = igdbId.trim(), igdbSecret = igdbSecret.trim()))
            }
            4 -> if (sgdb != cles.steamgriddb) onCles(cles.copy(steamgriddb = sgdb.trim()))
        }
    }

    fun avancer(enregistrer: Boolean) {
        if (enregistrer) enregistrerEtape()
        if (etape >= nombreEtapes - 1) onTerminer() else etape++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondClair)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(RougeJeu).height(HauteurBanniere).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MOONSHOP", color = BlancJeu, fontFamily = PoliceMoonshop, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            if (etape > 0) {
                Text("$etape / ${nombreEtapes - 1}", color = BlancJeu.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
        BordureFestonnee()

        AnimatedContent(
            targetState = etape,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(tween(120)))
            },
            label = "etape",
            modifier = Modifier.weight(1f)
        ) { indice ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (indice == 0) {
                    Bienvenue(nombreServices = services.size)
                } else {
                    val service = services[indice - 1]
                    EtapeService(service) {
                        when (service) {
                            ServiceApi.CONSOLE -> ChampCle(code, { code = it.uppercase() }, "Six-letter code")
                            ServiceApi.DRIVE -> ChampCle(cleDrive, { cleDrive = it }, "Google API key")
                            ServiceApi.IGDB -> {
                                ChampCle(igdbId, { igdbId = it }, "Client ID")
                                Spacer(Modifier.height(10.dp))
                                ChampCle(igdbSecret, { igdbSecret = it }, "Client secret")
                            }
                            ServiceApi.STEAMGRIDDB -> ChampCle(sgdb, { sgdb = it }, "API key")
                        }
                    }
                }
            }
        }

        Points(nombreEtapes, etape)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (etape > 0) {
                TextButton(
                    onClick = { SoundEffects.menuFermer(); avancer(enregistrer = false) },
                    colors = ButtonDefaults.textButtonColors(contentColor = TexteFonce.copy(alpha = 0.7f))
                ) {
                    Text("Skip")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { SoundEffects.clic(); avancer(enregistrer = true) },
                colors = ButtonDefaults.buttonColors(containerColor = RougeJeu, contentColor = BlancJeu),
                shape = RoundedCornerShape(50)
            ) {
                val dernier = etape >= nombreEtapes - 1
                Text(
                    when {
                        etape == 0 -> "Start"
                        dernier -> "Finish"
                        else -> "Next"
                    },
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (dernier) Icons.Default.Check else Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun Bienvenue(nombreServices: Int) {
    Spacer(Modifier.height(24.dp))
    Text(
        "Welcome",
        color = TexteFonce,
        fontFamily = PoliceMoonshop,
        fontSize = 40.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "Moonshop installs games from your own computer, and dresses them up with " +
            "cover art and descriptions along the way.",
        color = TexteFonce.copy(alpha = 0.85f),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(18.dp))
    Text(
        "The next $nombreServices steps set that up. Every one can be skipped now and " +
            "filled in later from Settings. Moonshop carries no keys of its own: skip a " +
            "step and that part simply stays off until you come back to it.",
        color = TexteFonce.copy(alpha = 0.65f),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun EtapeService(service: ServiceApi, champs: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(8.dp))
    PastilleService(service, taille = 76.dp)
    Spacer(Modifier.height(14.dp))
    Text(
        service.nom,
        color = TexteFonce,
        fontFamily = PoliceMoonshop,
        fontSize = 30.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        service.role,
        color = TexteFonce,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        service.pourquoi,
        color = TexteFonce.copy(alpha = 0.78f),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    if (service.lien != null) {
        Spacer(Modifier.height(6.dp))
        LienService(service)
    }
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth(), content = champs)
}

/** Points de progression : où l'on en est, sans avoir à lire un compteur. */
@Composable
private fun Points(total: Int, courant: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { indice ->
            val actif = indice == courant
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(if (actif) 20.dp else 7.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (actif) RougeJeu else RougeJeu.copy(alpha = 0.25f))
            )
        }
    }
}
