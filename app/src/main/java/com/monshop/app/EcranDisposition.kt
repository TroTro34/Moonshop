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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Choix de la disposition de la bibliothèque.
 *
 * Chaque proposition est accompagnée d'un croquis plutôt que d'un simple nom : « Mosaic »
 * et « Shelves » ne veulent rien dire tant qu'on ne les a pas vues, et essayer les quatre
 * pour comprendre serait une façon pénible de choisir.
 */
@Composable
fun EcranDisposition(
    dispositionActuelle: Disposition,
    onChoisir: (Disposition) -> Unit,
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
                "Layout",
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
                "How games are arranged in the library.",
                color = TexteFonce,
                style = MaterialTheme.typography.bodyMedium
            )

            Disposition.values().forEach { proposition ->
                CarteDisposition(
                    disposition = proposition,
                    choisie = proposition == dispositionActuelle,
                    onChoisir = { SoundEffects.clic(); onChoisir(proposition) }
                )
            }
        }
    }
}

@Composable
private fun CarteDisposition(
    disposition: Disposition,
    choisie: Boolean,
    onChoisir: () -> Unit
) {
    val forme = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(BlancCreme)
            .border(
                width = if (choisie) 2.dp else 1.dp,
                color = if (choisie) RougeJeu else RougeJeu.copy(alpha = 0.25f),
                shape = forme
            )
            .clickable(onClick = onChoisir)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Croquis(disposition, modifier = Modifier.width(84.dp).height(60.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                disposition.etiquette,
                color = TexteFonce,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                disposition.explication,
                color = TexteFonce.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (choisie) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = RougeJeu)
        }
    }
}

/** Croquis de l'agencement : des rectangles disposés comme le seront les jeux. */
@Composable
private fun Croquis(disposition: Disposition, modifier: Modifier = Modifier) {
    val trait = RougeJeu.copy(alpha = 0.55f)
    val ecart = 3.dp

    Box(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(FondClair).padding(4.dp)) {
        when (disposition) {
            Disposition.MOSAIQUE -> Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(ecart)
            ) {
                Pave(trait, Modifier.weight(1.35f).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ecart)) {
                    Pave(trait, Modifier.fillMaxWidth().weight(1f))
                    Pave(trait, Modifier.fillMaxWidth().weight(1f))
                }
            }

            Disposition.ETAGERES -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ecart)
            ) {
                repeat(2) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(ecart)) {
                        repeat(4) { Pave(trait, Modifier.width(11.dp).fillMaxHeight()) }
                        // Rectangle coupé au bord : la rangée continue au-delà de l'écran.
                        Pave(trait.copy(alpha = 0.25f), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }

            Disposition.GRILLE -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ecart)
            ) {
                repeat(2) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(ecart)) {
                        repeat(4) { Pave(trait, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }

            Disposition.LISTE -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ecart)
            ) {
                repeat(3) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(ecart)
                    ) {
                        Pave(trait, Modifier.width(10.dp).fillMaxHeight())
                        Pave(trait.copy(alpha = 0.3f), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun Pave(couleur: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(2.dp)).background(couleur))
}
