package com.jtr.app.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import kotlin.math.min

/**
 * Vue de schéma 3×3 (v6.2.0). Reliage des points par glissement, tracé visible ;
 * à la fin du geste, renvoie la suite d'indices (0..8, ligne par ligne) via
 * [onComplete]. Le minimum de nœuds est validé par l'appelant ([SecurityManager.MIN_PATTERN_SIZE]).
 *
 * État purement local et éphémère (aucun secret conservé ici).
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    onComplete: (List<Int>) -> Unit
) {
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val activeColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val selected: SnapshotStateList<Int> = remember { mutableStateListOf() }
    var pointer by remember { mutableStateOf<Offset?>(null) }

    // RTL — CRITIQUE : le verrou schéma DOIT rester strictement LTR. Les indices de
    // nœuds (0..8, ligne par ligne) sont sérialisés puis hachés ; sans ce verrou la
    // grille se miroiterait en arabe et un schéma défini en LTR ne se déverrouillerait
    // plus (et inversement). Forcer LTR garantit : un schéma se déverrouille sous
    // n'importe quelle langue.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                // Géométrie recalculée à CHAQUE toucher depuis la taille LIVE du Canvas
                // (PointerInputScope.size) — exactement la même que celle utilisée au
                // dessin (DrawScope.size). Le hit-test et les points dessinés partagent
                // donc TOUJOURS le même repère en pixels bruts : aucun décalage
                // toucher/visuel même si le Canvas est re-mesuré (ce que le passage en
                // RTL peut déclencher). Repère indépendant du sens de lecture → indices
                // de nœuds (0..8) identiques quelle que soit la langue.
                fun addHit(pos: Offset) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val centers = nodeCenters(w, h)
                    val hitRadius = min(w, h) / 6f * 0.62f
                    centers.forEachIndexed { index, c ->
                        if (index !in selected && hypot(pos.x - c.x, pos.y - c.y) <= hitRadius) {
                            selected.add(index)
                        }
                    }
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        selected.clear()
                        pointer = offset
                        addHit(offset)
                    },
                    onDrag = { change, _ ->
                        pointer = change.position
                        addHit(change.position)
                    },
                    onDragEnd = {
                        pointer = null
                        if (selected.isNotEmpty()) onComplete(selected.toList())
                    },
                    onDragCancel = {
                        pointer = null
                        selected.clear()
                    }
                )
            }
    ) {
        val centers = nodeCenters(size.width, size.height)
        val nodeRadius = min(size.width, size.height) / 6f * 0.30f

        // Segments reliant les nœuds sélectionnés, dans l'ordre.
        for (i in 0 until selected.size - 1) {
            drawLine(
                color = activeColor,
                start = centers[selected[i]],
                end = centers[selected[i + 1]],
                strokeWidth = nodeRadius * 0.55f,
                cap = StrokeCap.Round
            )
        }
        // Segment vers le doigt pendant le tracé.
        val last = selected.lastOrNull()
        val p = pointer
        if (last != null && p != null) {
            drawLine(
                color = activeColor,
                start = centers[last],
                end = p,
                strokeWidth = nodeRadius * 0.55f,
                cap = StrokeCap.Round
            )
        }
        // Nœuds : creux par défaut, pleins quand sélectionnés.
        centers.forEachIndexed { index, c ->
            val isOn = index in selected
            drawNode(c, nodeRadius, if (isOn) activeColor else dotColor, isOn)
        }
    }
    }
}

private fun DrawScope.drawNode(center: Offset, radius: Float, color: Color, filled: Boolean) {
    drawCircle(color = color, radius = radius, center = center)
    if (filled) {
        drawCircle(
            color = color.copy(alpha = 0.22f),
            radius = radius * 2.1f,
            center = center
        )
    }
}

/** Centres des 9 nœuds (3×3) inscrits dans un carré centré. */
private fun nodeCenters(width: Float, height: Float): List<Offset> {
    val side = min(width, height)
    val offsetX = (width - side) / 2f
    val offsetY = (height - side) / 2f
    val step = side / 3f
    val result = ArrayList<Offset>(9)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            result.add(
                Offset(
                    offsetX + step * (col + 0.5f),
                    offsetY + step * (row + 0.5f)
                )
            )
        }
    }
    return result
}
