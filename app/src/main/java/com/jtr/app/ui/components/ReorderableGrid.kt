package com.jtr.app.ui.components

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Grille réordonnable par glisser-déposer (drag & drop 2D) — MOTEUR PARTAGÉ.
 *
 * Extrait en v7.1.125 de `ReorderableTopGrid` (catégories, v7.1.78) pour servir aussi la
 * GALERIE : un seul moteur, donc un seul endroit où vivent les correctifs durement acquis
 * (copie de travail semée, hit-test en coordonnées fenêtre, auto-défilement aux bords,
 * action de drop différée hors du canal tactile).
 *
 * Ce qui est générique vit ici ; ce qui est métier reste chez l'appelant, qui fournit le
 * rendu d'une tuile ([itemContent]) et décide du sort d'un drop au CENTRE d'une autre tuile
 * ([canCenterDrop] / [onCenterDrop] — fusion de catégories, insertion dans un dossier…).
 * Un appelant sans « drop au centre », comme la galerie, les laisse nuls : tout survol
 * devient alors un réordonnancement.
 *
 * @param items source de vérité (déjà triée) ; la grille en tient une COPIE de travail
 *   qu'elle remanie pendant le geste et resynchronise hors glissement.
 * @param key identité stable d'un élément (clé de `itemsIndexed` et des comparaisons).
 * @param onPersistOrder appelé au drop avec l'ordre visible COMPLET, hors canal tactile.
 * @param canCenterDrop `null` = aucune zone centrale (tout est réordonnancement). La zone
 *   n'est active que si [onCenterDrop] est fourni AUSSI : l'un sans l'autre ferait disparaître
 *   le drop en silence.
 * @param autoScrollBottomZone hauteur de la zone basse de déclenchement du défilement : à
 *   élargir quand un footer recouvre le bas de la grille (mode sélection des catégories).
 * @param itemContent rendu d'une tuile ; `modifier` porte soit la translation de l'élément
 *   suivi par le doigt, soit l'animation de décalage des voisins — il doit être appliqué.
 */
@Composable
fun <T : Any> ReorderableGrid(
    items: List<T>,
    key: (T) -> Any,
    columns: Int,
    onPersistOrder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    canCenterDrop: ((dragged: T, target: T) -> Boolean)? = null,
    onCenterDrop: ((dragged: T, target: T) -> Unit)? = null,
    autoScrollBottomZone: Dp = 64.dp,
    itemContent: @Composable (
        item: T,
        isDragged: Boolean,
        isCenterTarget: Boolean,
        modifier: Modifier
    ) -> Unit
) {
    // Fix v7.1.81 (2e cause) — la copie de travail est SEMÉE dès sa création. Vide, la
    // grille se posait une première fois avec ZÉRO item : l'état de défilement partagé
    // était alors CLAMPÉ à l'index 0 (flash + retour en haut), que le remplissage suivant
    // ne rattrapait plus.
    val data = remember { mutableStateListOf<T>().apply { addAll(items) } }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var initialOffset by remember { mutableStateOf(IntOffset.Zero) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    // Position du doigt en coordonnées GLOBALES (Window) — stable, indépendante de la
    // tuile déplacée. Base unique et fiable de la détection de collision.
    var pointerWin by remember { mutableStateOf(Offset.Zero) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Clé de la tuile survolée en son CENTRE (cible d'un drop « au centre »).
    var centerTargetKey by remember { mutableStateOf<Any?>(null) }
    // Actions de drop DIFFÉRÉES : posées DANS le geste, traitées EN DEHORS (anti-crash
    // InputDispatcher — jamais de dialogue ni de Room dans le callback tactile).
    var pendingCenterDrop by remember { mutableStateOf<Pair<T, T>?>(null) }
    var pendingReorder by remember { mutableStateOf<List<T>?>(null) }
    // v7.1.78 — AUTO-DÉFILEMENT pendant le drag : vitesse en px/FRAME (signée, négative =
    // vers le haut), posée par onDrag selon la proximité du bord et remise à zéro en fin
    // de geste. Appliquée par la boucle plus bas.
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    val autoDensity = LocalDensity.current
    val autoScrollZonePx = with(autoDensity) { 64.dp.toPx() }
    val autoScrollBottomZonePx = with(autoDensity) { autoScrollBottomZone.toPx() }
    val autoScrollMaxStepPx = with(autoDensity) { 10.dp.toPx() }

    LaunchedEffect(items) {
        if (draggingIndex == null) { data.clear(); data.addAll(items) }
    }

    // Traite les drops hors du canal tactile (libéré) : dialogue ou ViewModel sans
    // stresser l'InputDispatcher.
    LaunchedEffect(pendingCenterDrop) {
        pendingCenterDrop?.let { (dragged, target) ->
            onCenterDrop?.invoke(dragged, target)
            pendingCenterDrop = null
        }
    }
    LaunchedEffect(pendingReorder) {
        pendingReorder?.let { ordered ->
            onPersistOrder(ordered)
            pendingReorder = null
        }
    }

    fun draggedTranslation(): Offset {
        val idx = draggingIndex ?: return Offset.Zero
        val current = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        val off = current?.offset ?: IntOffset.Zero
        return Offset((initialOffset.x + delta.x) - off.x, (initialOffset.y + delta.y) - off.y)
    }
    // Coin haut-gauche d'une tuile (offset local de la grille) en coordonnées Window.
    fun itemWindowTopLeft(localOffset: IntOffset): Offset {
        val c = gridCoords ?: return Offset(localOffset.x.toFloat(), localOffset.y.toFloat())
        return c.localToWindow(Offset(localOffset.x.toFloat(), localOffset.y.toFloat()))
    }
    fun itemAt(win: Offset, except: Int?) =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            if (info.index == except || info.index !in data.indices) return@firstOrNull false
            val tl = itemWindowTopLeft(info.offset)
            win.x >= tl.x && win.x <= tl.x + info.size.width &&
                win.y >= tl.y && win.y <= tl.y + info.size.height
        }
    // Le doigt est-il dans la zone CENTRALE (50 % des deux axes) de la tuile ?
    // Intention de fusion/insertion ; sur les bords, réordonnancement.
    fun inCenterZone(info: LazyGridItemInfo): Boolean {
        val tl = itemWindowTopLeft(info.offset)
        val w = info.size.width.toFloat()
        val h = info.size.height.toFloat()
        return pointerWin.x >= tl.x + w * 0.25f && pointerWin.x <= tl.x + w * 0.75f &&
            pointerWin.y >= tl.y + h * 0.25f && pointerWin.y <= tl.y + h * 0.75f
    }
    // CORRECTIF v5.5 (superposition) : le réordonnancement n'est déclenché qu'une fois le
    // doigt passé AU-DELÀ du centre de la tuile survolée, du côté opposé à l'emplacement de
    // la tuile déplacée. Avant, l'échange partait dès le bord proche : la cible fuyait par
    // swap avant que le doigt n'atteigne la zone centrale.
    fun fingerPastTargetCenter(from: Int, info: LazyGridItemInfo): Boolean {
        val fromInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from }
            ?: return true
        val fromTl = itemWindowTopLeft(fromInfo.offset)
        val fromCenter = Offset(
            fromTl.x + fromInfo.size.width / 2f, fromTl.y + fromInfo.size.height / 2f)
        val tl = itemWindowTopLeft(info.offset)
        val center = Offset(tl.x + info.size.width / 2f, tl.y + info.size.height / 2f)
        val dirX = center.x - fromCenter.x
        val dirY = center.y - fromCenter.y
        return (pointerWin.x - center.x) * dirX + (pointerWin.y - center.y) * dirY >= 0f
    }
    // Résolution cible : drop au centre ou réordonnancement. PARTAGÉE entre onDrag (le doigt
    // bouge) et l'auto-défilement (la GRILLE glisse sous un doigt immobile : sans cette
    // réévaluation à chaque pas, les échanges ne cascaderaient pas).
    fun resolveDragTarget() {
        val from = draggingIndex ?: return
        val dragged = data.getOrNull(from) ?: return
        val target = itemAt(pointerWin, except = from)
        val targetItem = target?.let { data.getOrNull(it.index) }
        // Le HANDLER est exigé autant que la zone : un appelant qui déclarerait une zone
        // centrale sans fournir [onCenterDrop] verrait son drop s'évaporer en silence — ni
        // action métier, ni ordre persisté. Sans les deux, tout survol est un
        // réordonnancement, ce qui est le repli sûr.
        val centerAllowed = canCenterDrop != null && onCenterDrop != null &&
            targetItem != null && canCenterDrop(dragged, targetItem)
        // `target` d'abord : c'est lui qui porte le smart cast des deux appels suivants
        // (le tester après `centerAllowed`, qui l'implique déjà, est une condition morte).
        if (target != null && centerAllowed && inCenterZone(target)) {
            val targetKey = key(data[target.index])
            // Tracé au CHANGEMENT de cible seulement : le doigt traverse la zone centrale
            // sur des dizaines d'événements, un log par événement noierait la trace.
            if (centerTargetKey != targetKey) {
                Log.d("JTR_DRAG", "onDrag CENTER target=$targetKey")
            }
            centerTargetKey = targetKey
        } else {
            centerTargetKey = null
            if (target != null && fingerPastTargetCenter(from, target)) {
                val to = target.index
                Log.d("JTR_DRAG", "onDrag SWAP ${key(dragged)} idx $from -> $to")
                data.add(to, data.removeAt(from))
                draggingIndex = to
            }
        }
    }
    // Pas d'auto-défilement (px/frame) : nul hors des zones de bord, proportionnel à la
    // pénétration dans la zone, avec un plancher de 25 % pour rester franc dès l'entrée.
    fun autoScrollStepFor(pointerY: Float): Float {
        val c = gridCoords ?: return 0f
        if (!c.isAttached) return 0f
        val top = c.localToWindow(Offset.Zero).y
        val bottom = top + c.size.height
        return when {
            pointerY <= top + autoScrollZonePx ->
                -autoScrollMaxStepPx *
                    ((top + autoScrollZonePx - pointerY) / autoScrollZonePx).coerceIn(0.25f, 1f)
            pointerY >= bottom - autoScrollBottomZonePx ->
                autoScrollMaxStepPx *
                    ((pointerY - (bottom - autoScrollBottomZonePx)) / autoScrollBottomZonePx)
                        .coerceIn(0.25f, 1f)
            else -> 0f
        }
    }

    // Boucle d'auto-défilement : UNE itération par frame tant qu'un drag est en cours.
    // S'arrête d'elle-même hors zone, au drop et à l'annulation ; en butée, scrollBy ne
    // consomme rien — aucun emballement possible.
    LaunchedEffect(draggingIndex != null) {
        while (draggingIndex != null) {
            val step = autoScrollSpeed
            if (step != 0f) {
                gridState.scrollBy(step)
                resolveDragTarget()
            }
            withFrameMillis { }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { gridCoords = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val coords = gridCoords
                        if (coords == null || !coords.isAttached)
                            return@detectDragGesturesAfterLongPress
                        // `offset` est local à la grille : passage en coordonnées Window.
                        pointerWin = coords.localToWindow(offset)
                        val hit = itemAt(pointerWin, except = null)
                        if (hit != null) {
                            draggingIndex = hit.index
                            initialOffset = hit.offset
                            delta = Offset.Zero
                            centerTargetKey = null
                            autoScrollSpeed = 0f
                            Log.d("JTR_DRAG", "onDragStart grab=${key(data[hit.index])}" +
                                " idx=${hit.index} finger(win)=(${pointerWin.x},${pointerWin.y})")
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (draggingIndex == null) return@detectDragGesturesAfterLongPress
                        val coords = gridCoords
                        if (coords == null || !coords.isAttached)
                            return@detectDragGesturesAfterLongPress
                        delta += dragAmount
                        pointerWin = coords.localToWindow(change.position)
                        resolveDragTarget()
                        autoScrollSpeed = autoScrollStepFor(pointerWin.y)
                    },
                    onDragEnd = {
                        // ANTI-CRASH : on NE déclenche RIEN ici (ni dialogue, ni Room). On
                        // lit l'état figé, on DÉPOSE l'action, et on libère le canal tactile.
                        try {
                            val coords = gridCoords
                            if (coords == null || !coords.isAttached) {
                                // Layout invalide au lâcher : on annule poliment, sans
                                // forcer le moindre calcul de coordonnées.
                                Log.d("JTR_DRAG", "onDragEnd CANCEL (layout detached)")
                            } else {
                                val from = draggingIndex
                                val dragged = from?.let { data.getOrNull(it) }
                                val target = centerTargetKey?.let { k ->
                                    data.firstOrNull { key(it) == k } }
                                if (dragged != null && target != null &&
                                    key(dragged) != key(target)
                                ) {
                                    Log.d("JTR_DRAG", "onDragEnd ACTION=CENTER_DROP" +
                                        " ${key(dragged)} -> ${key(target)}")
                                    pendingCenterDrop = dragged to target
                                } else {
                                    Log.d("JTR_DRAG", "onDragEnd ACTION=REORDER" +
                                        " n=${data.size} order=${data.map { key(it) }}")
                                    pendingReorder = data.toList()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("JTR_ERROR", "Crash evite dans onDragEnd", e)
                        } finally {
                            draggingIndex = null
                            centerTargetKey = null
                            autoScrollSpeed = 0f
                        }
                    },
                    onDragCancel = {
                        draggingIndex = null; centerTargetKey = null; autoScrollSpeed = 0f
                    }
                )
            },
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(data, key = { _, e -> key(e) }) { index, item ->
            val isDragged = index == draggingIndex
            val mod = if (isDragged) {
                Modifier.zIndex(1f).graphicsLayer {
                    val t = draggedTranslation(); translationX = t.x; translationY = t.y
                }
            } else {
                Modifier.animateItem()
            }
            itemContent(item, isDragged, centerTargetKey == key(item), mod)
        }
    }
}
