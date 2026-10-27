package com.jtr.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jtr.app.R

/**
 * FAB « remonter en haut » (v7.1.72) — composable PRÉSENTATIONNEL réutilisable.
 *
 * Volontairement découplé du type de liste (LazyList vs LazyGrid) : l'appelant calcule
 * [visible] depuis l'état de défilement ACTIF (p. ex. `firstVisibleItemIndex > seuil`, dérivé
 * via `derivedStateOf` pour n'évaluer qu'au franchissement du seuil, pas à chaque pixel) et
 * fournit l'[onClick] qui lance `animateScrollToItem(0)` sur le bon état. Ce composable ne
 * fait qu'afficher/masquer un petit FAB secondaire avec une animation d'échelle + fondu.
 *
 * Placé dans le slot `floatingActionButton` d'un `Scaffold` : positionné bas-fin par le
 * Scaffold (qui le remonte au-dessus de sa `bottomBar`), au-dessus de la barre de navigation
 * globale déjà exclue par le padding du Scaffold racine. L'app n'ayant PAS de FAB d'ajout
 * (l'ajout est une icône « + » dans la TopAppBar), aucun risque de chevauchement.
 */
@Composable
fun ScrollToTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.action_scroll_to_top)
            )
        }
    }
}

/**
 * Seuil de défilement (en index du 1er élément visible) au-delà duquel le FAB « remonter »
 * apparaît. Volontairement bas (3) : assez pour éviter le clignotement près du haut, mais
 * atteignable même sur l'écran Catégories (grille 2 colonnes, liste courte ~11 entrées → le
 * 1er index visible plafonne autour de 4-5 une fois défilé en bas). Sur l'Accueil (≈200
 * contacts) le FAB apparaît après un défilement franc dans tous les modes.
 */
const val SCROLL_TO_TOP_THRESHOLD = 3
