package com.jtr.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * État du moteur de ZOOM/PAN/GLISSER-POUR-FERMER (v7.1.91) — extrait de `PhotoZoomDialog`
 * (v7.1.2/7.1.49) pour avoir **UN SEUL** moteur de gestes dans toute l'app : la visionneuse
 * d'avatar/catégorie et le pager de la galerie partagent exactement le même réglage.
 *
 * Hoistable : l'écran hôte lit [isZoomed] (pour verrouiller un pager pendant le zoom) et
 * [dismissFraction] (pour estomper son fond et ses contrôles EN SYNCHRO avec l'image).
 */
class ZoomState internal constructor(internal val scope: CoroutineScope) {

    /** Zoom courant (1× = repos) et translation du PAN, animables. */
    internal val scale = Animatable(1f)
    internal val offsetX = Animatable(0f)
    internal val offsetY = Animatable(0f)

    /** Translation du GLISSER-POUR-FERMER, distincte du pan de zoom. */
    internal val dismissX = Animatable(0f)
    internal val dismissY = Animatable(0f)

    internal var containerSize by mutableStateOf(IntSize.Zero)
    internal var intrinsicSize by mutableStateOf<Size?>(null)

    /** Vrai dès que l'image est agrandie — l'hôte verrouille alors son pager. */
    val isZoomed: Boolean get() = scale.value > 1f

    /**
     * Rectangle réellement occupé par la photo (mode Fit, à l'échelle 1) : base du
     * dimensionnement de la zone tactile ; le reste du conteneur est du letterbox.
     */
    internal fun displayedSize(): Size {
        val c = containerSize
        if (c == IntSize.Zero) return Size.Zero
        val i = intrinsicSize
        return if (i != null && i.width > 0f && i.height > 0f) {
            val fit = minOf(c.width / i.width, c.height / i.height)
            Size(i.width * fit, i.height * fit)
        } else {
            Size(c.width.toFloat(), c.height.toFloat())
        }
    }

    /** Barrières du pan : l'image ne peut JAMAIS être traînée hors de son conteneur. */
    internal fun maxPanAt(s: Float): Offset {
        val c = containerSize
        if (c == IntSize.Zero) return Offset.Zero
        val d = displayedSize()
        return Offset(
            ((d.width * s - c.width) / 2f).coerceAtLeast(0f),
            ((d.height * s - c.height) / 2f).coerceAtLeast(0f)
        )
    }

    /**
     * Progression du glissement de fermeture (0 = repos, 1 = plein effet), rapportée à
     * ~30 % de la hauteur. Pilote l'estompage du fond et le léger rétrécissement.
     */
    fun dismissFraction(): Float {
        val h = containerSize.height
        if (h <= 0) return 0f
        return (abs(dismissY.value) / (h * 0.30f)).coerceIn(0f, 1f)
    }

    private val backSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)

    /** Retour élastique : la photo reprend sa place après un glissement annulé. */
    internal fun springBackDismiss() {
        scope.launch { dismissX.animateTo(0f, backSpring) }
        scope.launch { dismissY.animateTo(0f, backSpring) }
    }

    /** Sortie confirmée : la photo poursuit dans le sens du doigt, puis on referme. */
    internal fun animateOutAndDismiss(onDismiss: () -> Unit) {
        scope.launch {
            val h = containerSize.height.toFloat().takeIf { it > 0f } ?: 2000f
            val dir = if (dismissY.value < 0f) -1f else 1f
            dismissY.animateTo(dir * h, tween(durationMillis = 200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    /**
     * Double-tap ANIMÉ (~280 ms) : 1× → 2,5× ANCRÉ sur le point touché (le point sous le
     * doigt y reste), ou dézoom centré si déjà zoomée. Même ancrage que le pinch :
     * offset' = d·(1−k) + offset·k, avec d = point − centre.
     */
    internal fun animateZoomToPoint(point: Offset) {
        scope.launch {
            val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
            if (scale.value > 1f) {
                launch { scale.animateTo(1f, spec) }
                launch { offsetX.animateTo(0f, spec) }
                launch { offsetY.animateTo(0f, spec) }
            } else {
                val target = 2.5f
                val k = target / scale.value
                val d = displayedSize()
                val dx = point.x - d.width / 2f
                val dy = point.y - d.height / 2f
                val max = maxPanAt(target)
                val newOffX = (dx * (1f - k) + offsetX.value * k).coerceIn(-max.x, max.x)
                val newOffY = (dy * (1f - k) + offsetY.value * k).coerceIn(-max.y, max.y)
                launch { scale.animateTo(target, spec) }
                launch { offsetX.animateTo(newOffX, spec) }
                launch { offsetY.animateTo(newOffY, spec) }
            }
        }
    }

    /** Remet zoom, pan et glissement à zéro — utilisé au changement d'image d'un pager. */
    fun reset() {
        scope.launch { scale.snapTo(1f) }
        scope.launch { offsetX.snapTo(0f) }
        scope.launch { offsetY.snapTo(0f) }
        scope.launch { dismissX.snapTo(0f) }
        scope.launch { dismissY.snapTo(0f) }
    }
}

@Composable
fun rememberZoomState(): ZoomState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ZoomState(scope) }
}

/**
 * Image ZOOMABLE plein conteneur (v7.1.91) — pinch, pan borné, double-tap ancré et
 * glisser-pour-fermer. **Unique** moteur de gestes de l'app : utilisé par la visionneuse
 * d'avatar/catégorie ([PhotoZoomDialog]) ET par le pager de la galerie.
 *
 * @param horizontalDragGoesToParent quand l'image est AU REPOS (1×), laisse les glissements
 *   à dominante HORIZONTALE au parent sans les consommer — c'est ce qui permet à un pager de
 *   changer d'image, tandis que le glissement VERTICAL ferme toujours. À `false` (la
 *   visionneuse mono-image), un glissement dans n'importe quelle direction ferme, comme avant.
 *   Zoomée (>1×), l'image prend TOUS les glissements pour son pan : l'hôte doit alors
 *   verrouiller son pager en lisant [ZoomState.isZoomed].
 */
@Composable
fun ZoomableImage(
    model: Any,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: ZoomState = rememberZoomState(),
    horizontalDragGoesToParent: Boolean = false,
    contentDescription: String? = null,
    onTap: (() -> Unit)? = null
) {
    val density: Density = LocalDensity.current
    // Rayon MAX des coins arrondis au plein glissement (« carte flottante »), converti UNE
    // fois en px (jamais de dp.toPx() par frame dans le graphicsLayer).
    val dismissCornerPx = remember(density) { with(density) { 24.dp.toPx() } }

    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { state.containerSize = it },
        contentAlignment = Alignment.Center
    ) {
        val disp = state.displayedSize()
        val photoModifier = if (disp != Size.Zero) {
            Modifier.size(
                with(density) { disp.width.toDp() },
                with(density) { disp.height.toDp() }
            )
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = photoModifier
                .align(Alignment.Center)
                .graphicsLayer {
                    // TOUT dérive de la MÊME progression p (source unique partagée avec
                    // l'alpha du fond de l'hôte) → fond, échelle et coins restent
                    // synchronisés. Lu en phase de DESSIN : pas de recomposition, donc
                    // aucun retard derrière le doigt.
                    val p = state.dismissFraction()
                    val ds = 1f - 0.15f * p
                    scaleX = state.scale.value * ds
                    scaleY = state.scale.value * ds
                    translationX = state.offsetX.value + state.dismissX.value
                    translationY = state.offsetY.value + state.dismissY.value
                    clip = p > 0f
                    shape = if (p > 0f) RoundedCornerShape(dismissCornerPx * p) else RectangleShape
                }
                // Geste unifié : pinch/pan quand la photo est zoomée, sinon glisser-pour-
                // fermer. Les taps sont gérés par le detectTapGestures ci-dessous
                // (cohabitation par slop).
                .pointerInput(horizontalDragGoesToParent) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var acting = false
                        var mode = 0            // 1 = zoom/pan, 2 = fermeture
                        var dismissing = false
                        var slop = Offset.Zero
                        var canceled = false
                        val touchSlop = viewConfiguration.touchSlop
                        val velocityTracker = VelocityTracker()

                        do {
                            val event = awaitPointerEvent()
                            canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val pressed = event.changes.count { it.pressed }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()

                                // Détermination du mode au franchissement du slop :
                                // 2 doigts OU déjà zoomée → zoom/pan ; sinon fermeture.
                                if (!acting) {
                                    if (pressed >= 2) {
                                        acting = true; mode = 1
                                    } else {
                                        slop += pan
                                        if (slop.getDistance() > touchSlop) {
                                            // ARBITRAGE (v7.1.91) : au repos et en contexte
                                            // de pager, un glissement à dominante
                                            // HORIZONTALE est LAISSÉ au parent — on
                                            // n'engage rien et on ne consomme rien, donc le
                                            // pager change d'image. Le vertical ferme.
                                            val horizontal = abs(slop.x) > abs(slop.y)
                                            if (state.scale.value <= 1f &&
                                                horizontalDragGoesToParent && horizontal
                                            ) {
                                                return@awaitEachGesture
                                            }
                                            acting = true
                                            mode = if (state.scale.value > 1f) 1 else 2
                                            dismissing = mode == 2
                                        }
                                    }
                                }

                                if (acting) {
                                    // 2e doigt pendant la fermeture → bascule en zoom.
                                    if (mode == 2 && pressed >= 2) {
                                        state.springBackDismiss()
                                        dismissing = false
                                        mode = 1
                                    }
                                    if (mode == 1) {
                                        // snapTo lancés sur le scope de composition :
                                        // l'AwaitPointerEventScope est suspendu restreint.
                                        state.scope.launch {
                                            state.scale.snapTo(
                                                (state.scale.value * zoom).coerceIn(1f, 5f))
                                            val max = state.maxPanAt(state.scale.value)
                                            state.offsetX.snapTo(
                                                (state.offsetX.value + pan.x).coerceIn(-max.x, max.x))
                                            state.offsetY.snapTo(
                                                (state.offsetY.value + pan.y).coerceIn(-max.y, max.y))
                                        }
                                    } else {
                                        event.changes.firstOrNull { it.pressed }
                                            ?.let { velocityTracker.addPointerInputChange(it) }
                                        state.scope.launch {
                                            state.dismissX.snapTo(state.dismissX.value + pan.x)
                                            state.dismissY.snapTo(state.dismissY.value + pan.y)
                                        }
                                    }
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        // Relâchement : seuil de distance (20 % hauteur) OU de vélocité →
                        // fermeture fluide ; sinon retour élastique à l'origine.
                        if (dismissing) {
                            if (canceled) {
                                state.springBackDismiss()
                            } else {
                                val v = velocityTracker.calculateVelocity()
                                val h = state.containerSize.height
                                val farEnough = h > 0 && abs(state.dismissY.value) > h * 0.20f
                                val fastEnough = abs(v.y) > 1200f
                                if (farEnough || fastEnough) state.animateOutAndDismiss(onDismiss)
                                else state.springBackDismiss()
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Tap : dézoom (animé) si zoomée, sinon l'action de l'hôte.
                        onTap = {
                            if (state.scale.value > 1f) state.animateZoomToPoint(it)
                            else onTap?.invoke()
                        },
                        // Double-tap : zoom DOUX animé, ancré sur le point touché.
                        onDoubleTap = { state.animateZoomToPoint(it) }
                    )
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model).crossfade(200).build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                onState = { s ->
                    if (s is AsyncImagePainter.State.Success) {
                        state.intrinsicSize = s.painter.intrinsicSize
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
