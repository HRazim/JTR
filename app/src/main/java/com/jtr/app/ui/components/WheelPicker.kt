package com.jtr.app.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Molette (spinner) générique fiable : une [LazyColumn] avec accrochage
 * ([rememberSnapFlingBehavior]) et un rembourrage vertical qui CENTRE l'élément accroché.
 * L'élément le plus proche du centre est mis en évidence (tokens de thème) ; une bande
 * centrale matérialise la sélection. [onIndexSettled] est appelé à l'arrêt du défilement,
 * uniquement après une interaction utilisateur (pas de report parasite à l'init).
 *
 * Défilement vertical → indépendant du sens RTL. Hauteur FIXE (pas de géométrie fragile),
 * compatible avec le contenu défilant d'une feuille (contraintes bornées).
 *
 * v7.1.94 — EXTRAIT de `ReminderOffsetSheet` (v7.0.6), à l'identique, pour que la molette de
 * rappel et celles du panneau de détails d'image soient LE MÊME composant : un seul réglage
 * de défilement, d'accrochage et de surbrillance dans toute l'app.
 */
@Composable
internal fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onIndexSettled: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = 5
    val itemHeight = 40.dp
    // initialFirstVisibleItemIndex = valeur initiale : avec le rembourrage vertical
    // symétrique, l'élément est CENTRÉ dès le 1er rendu (pas de flash à l'index 0, y
    // compris à la ré-instanciation de la roue au changement de liste).
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)

    // Index de l'élément dont le centre est le plus proche du centre du viewport (live).
    val centerIndex by remember {
        derivedStateOf {
            val info = state.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) initialIndex
            else {
                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2f) - mid) }!!.index
            }
        }
    }

    // Report à l'arrêt du défilement, seulement après une vraie interaction (évite un
    // report parasite au premier rendu / à l'ouverture).
    var userInteracted by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) userInteracted = true
        else if (userInteracted) onIndexSettled(centerIndex)
    }

    // Isole la molette de la fermeture-par-glisser du ModalBottomSheet : on CONSOMME le
    // résidu vertical (overscroll en butée, ex. 1 minute) après que la roue a défilé, pour
    // qu'il ne remonte JAMAIS jusqu'au sheet. La roue défile normalement ; seul le surplus
    // en bout de course est absorbé (la feuille reste fermable par poignée/scrim/retour).
    val isolateOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset = Offset(0f, available.y)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .nestedScroll(isolateOverscroll),
        contentAlignment = Alignment.Center
    ) {
        // Bande de sélection centrale (surbrillance via tokens de thème).
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(itemHeight)
        ) {}
        LazyColumn(
            state = state,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, label ->
                val selected = index == centerIndex
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = if (selected) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}
