package com.jtr.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.jtr.app.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Visionneuse plein écran RÉUTILISABLE (v7.1.2, extraite de `PersonDetailScreen` en v7.1.49) :
 * zoom pincement, pan borné, double-tap ancré et glisser-pour-fermer façon Instagram.
 *
 * Le moteur de gestes est finement réglé (arbitrage slop / vélocité / barrières de pan) — un
 * SEUL exemplaire pour toute l'app : avatars de contact ET images de catégorie.
 *
 * [onReplace] / [onRemove] sont OPTIONNELS : `null` (défaut) ⇒ le bouton correspondant est
 * absent et la visionneuse est en LECTURE SEULE — c'est le mode des fiches contact, dont
 * l'édition de photo passe par `EditPhotoOverlay`.
 *
 * @param photoUri modèle accepté par Coil (chemin absolu, [android.net.Uri], URL…).
 */
@Composable
internal fun PhotoZoomDialog(
    photoUri: Any,
    onDismiss: () -> Unit,
    onReplace: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    // Échelle/translation ANIMABLES → double-tap zoom doux + recentrage fluide.
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    // Translation de FERMETURE par glissement (façon Instagram), distincte du pan de
    // zoom : à l'échelle de base, la photo suit le doigt puis revient en ressort.
    val dismissX = remember { Animatable(0f) }
    val dismissY = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Taille intrinsèque de l'image chargée → dimensions réellement affichées (Fit).
    var intrinsicSize by remember { mutableStateOf<Size?>(null) }
    val density = LocalDensity.current
    // Rayon MAX des coins arrondis au plein glissement de fermeture (« carte flottante »),
    // converti en px UNE fois (pas de dp.toPx() par frame dans le graphicsLayer).
    val dismissCornerPx = with(density) { 24.dp.toPx() }

    // Rectangle réellement occupé par la photo (mode Fit, à l'échelle 1) — base du
    // dimensionnement de la zone tactile « photo » (le reste = noir/letterbox).
    fun dispSize(): Size {
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

    // Barrières géométriques du pan à une échelle donnée : l'image ne peut JAMAIS
    // être traînée hors écran (révéler du fond noir).
    fun maxPanAt(s: Float): Offset {
        val c = containerSize
        if (c == IntSize.Zero) return Offset.Zero
        val d = dispSize()
        return Offset(
            ((d.width * s - c.width) / 2f).coerceAtLeast(0f),
            ((d.height * s - c.height) / 2f).coerceAtLeast(0f)
        )
    }

    // Progression du glissement de fermeture (0 = repos, 1 = plein effet), basée sur la
    // distance verticale rapportée à ~30 % de la hauteur écran. Pilote l'estompage du
    // fond noir et le léger rétrécissement de la photo (lue en phase de dessin).
    fun dismissFraction(): Float {
        val h = containerSize.height
        if (h <= 0) return 0f
        return (abs(dismissY.value) / (h * 0.30f)).coerceIn(0f, 1f)
    }

    val backSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)

    // Retour élastique : la photo reprend sa place (translation de fermeture → 0).
    fun springBackDismiss() {
        scope.launch { dismissX.animateTo(0f, backSpring) }
        scope.launch { dismissY.animateTo(0f, backSpring) }
    }

    // Fermeture confirmée : la photo poursuit sa sortie dans le sens du doigt (fond
    // déjà estompé via dismissFraction), puis on referme le visualiseur.
    fun animateOutAndDismiss() {
        scope.launch {
            val h = containerSize.height.toFloat().takeIf { it > 0f } ?: 2000f
            val dir = if (dismissY.value < 0f) -1f else 1f
            dismissY.animateTo(dir * h, tween(durationMillis = 200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    // Double-tap ANIMÉ (~280 ms, easing doux) : zoom 1× → 2.5× ANCRÉ sur le point
    // touché (le point sous le doigt y reste), ou dézoom centré si déjà zoomée.
    // Ancrage identique au pinch : offset' = d·(1−k) + offset·k, d = point − centre.
    fun animateZoomToPoint(point: Offset) {
        scope.launch {
            val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
            if (scale.value > 1f) {
                launch { scale.animateTo(1f, spec) }
                launch { offsetX.animateTo(0f, spec) }
                launch { offsetY.animateTo(0f, spec) }
            } else {
                val target = 2.5f
                val k = target / scale.value
                val d = dispSize()
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      // RTL : la visionneuse opère en pixels (zoom/pan via graphicsLayer, ancrage
      // double-tap en coordonnées locales) → on force LTR pour rester direction-
      // agnostique (gestes identiques en arabe).
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // ⚠️ INSETS EN FENÊTRE DE DIALOG (v7.1.49) — mesuré sur S21, navigation 3 boutons :
        // à l'INTÉRIEUR d'un `Dialog`, TOUTES les sources d'insets rapportent ZÉRO — les
        // `WindowInsets` de Compose (`navigationBarsPadding()`), `decorFitsSystemWindows =
        // false`, et même `getRootWindowInsets` sur la vue du dialogue — alors que la
        // fenêtre s'étend bel et bien SOUS la barre de navigation : les actions du bas se
        // dessinaient PAR-DESSUS les touches. On interroge donc la fenêtre de l'ACTIVITÉ,
        // seule à être posée et mesurée, donc seule à renvoyer l'inset RÉEL.
        // 0 en navigation par gestes → aucun espace parasite sur les appareils modernes.
        val hostView = LocalView.current
        val navBarBottomPx = remember(hostView) {
            val decor = hostView.context.findActivity()?.window?.decorView
            decor?.let {
                ViewCompat.getRootWindowInsets(it)
                    ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
            } ?: 0
        }
        val navBarBottom = with(density) { navBarBottomPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            // FOND noir / letterbox : s'estompe à mesure du glissement de fermeture
            // (alpha lu en phase de dessin → pas de recomposition). Tap simple =
            // fermeture IMMÉDIATE (aucun onDoubleTap ici → fermeture snappy).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - dismissFraction() }
                    .background(Color.Black.copy(alpha = 0.92f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )

            // ZONE PHOTO : dimensionnée au rectangle Fit ; le graphicsLayer applique
            // zoom/pan ET fait suivre la zone tactile à l'image agrandie.
            val disp = dispSize()
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
                        // TOUT dérive de la MÊME progression de glissement p (source unique
                        // que l'alpha du fond) → fond, échelle et coins restent synchronisés ;
                        // p=0 au repos ET quand l'image est zoomée (dismissY reste 0 en mode
                        // pan/zoom) → aucun coin ni rétrécissement parasite. Lu en phase de
                        // DESSIN (pas de recomposition, pas de retard derrière le doigt).
                        val p = dismissFraction()
                        // Échelle = zoom × léger rétrécissement de fermeture (jusqu'à ~0.85) ;
                        // translation = pan de zoom + suivi du doigt.
                        val ds = 1f - 0.15f * p
                        scaleX = scale.value * ds
                        scaleY = scale.value * ds
                        translationX = offsetX.value + dismissX.value
                        translationY = offsetY.value + dismissY.value
                        // Coins arrondis progressifs « carte flottante » : 0 → 24dp pilotés
                        // par p. Clip sur le render node (GPU). Au repos/zoom (p=0) : clip
                        // désactivé + RectangleShape (singleton, zéro allocation) → rendu et
                        // gestes inchangés. Au retour (drag annulé), p suit le ressort de
                        // dismissY → les coins reviennent à 0 EN SYNCHRO avec échelle/fond.
                        clip = p > 0f
                        shape = if (p > 0f) RoundedCornerShape(dismissCornerPx * p) else RectangleShape
                    }
                    // Geste unifié : pinch/pan quand la photo est zoomée (>1×), sinon
                    // glisser-pour-fermer à l'échelle de base. Les taps restent gérés
                    // par le detectTapGestures ci-dessous (cohabitation par slop).
                    .pointerInput(Unit) {
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
                                                acting = true
                                                mode = if (scale.value > 1f) 1 else 2
                                                dismissing = mode == 2
                                            }
                                        }
                                    }

                                    if (acting) {
                                        // 2e doigt pendant la fermeture → bascule en zoom.
                                        if (mode == 2 && pressed >= 2) {
                                            springBackDismiss()
                                            dismissing = false
                                            mode = 1
                                        }
                                        if (mode == 1) {
                                            // snapTo lancés sur le scope de composition :
                                            // l'AwaitPointerEventScope est suspendu restreint.
                                            scope.launch {
                                                scale.snapTo((scale.value * zoom).coerceIn(1f, 5f))
                                                val max = maxPanAt(scale.value)
                                                offsetX.snapTo((offsetX.value + pan.x).coerceIn(-max.x, max.x))
                                                offsetY.snapTo((offsetY.value + pan.y).coerceIn(-max.y, max.y))
                                            }
                                        } else {
                                            event.changes.firstOrNull { it.pressed }
                                                ?.let { velocityTracker.addPointerInputChange(it) }
                                            scope.launch {
                                                dismissX.snapTo(dismissX.value + pan.x)
                                                dismissY.snapTo(dismissY.value + pan.y)
                                            }
                                        }
                                        event.changes.forEach { if (it.pressed) it.consume() }
                                    }
                                }
                            } while (!canceled && event.changes.any { it.pressed })

                            // Relâchement : seuil de distance (20 % hauteur) OU de vélocité
                            // → fermeture fluide ; sinon retour élastique à l'origine.
                            if (dismissing) {
                                if (canceled) {
                                    springBackDismiss()
                                } else {
                                    val v = velocityTracker.calculateVelocity()
                                    val h = containerSize.height
                                    val farEnough = h > 0 && abs(dismissY.value) > h * 0.20f
                                    val fastEnough = abs(v.y) > 1200f
                                    if (farEnough || fastEnough) animateOutAndDismiss()
                                    else springBackDismiss()
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            // Tap sur la photo : dézoom (animé) si zoomée, sinon ferme.
                            onTap = { if (scale.value > 1f) animateZoomToPoint(it) else onDismiss() },
                            // Double-tap : zoom DOUX animé, ancré sur le point touché.
                            onDoubleTap = { animateZoomToPoint(it) }
                        )
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUri).crossfade(200).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            intrinsicSize = state.painter.intrinsicSize
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Croix sur un scrim sombre circulaire → TOUJOURS visible, même sur une
            // photo claire/blanche.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    // S'estompe avec le glissement de fermeture (contrôles masqués).
                    .graphicsLayer { alpha = 1f - dismissFraction() }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White
                )
            }

            // ACTIONS OPTIONNELLES (v7.1.49) — Remplacer / Retirer.
            // ⚠️ Volontairement HORS du Box photo, en enfant direct du Box racine : un
            // bouton posé DANS la zone photo verrait ses taps ratés interceptés par le
            // `detectTapGestures` de la photo, qui FERME la visionneuse. Ici, de simples
            // boutons cohabitent sans toucher au `awaitEachGesture` (aucun pointerInput
            // concurrent). Estompés par le MÊME `dismissFraction()` que la croix → les
            // contrôles disparaissent en synchro avec le fond, l'échelle et les coins.
            if (onReplace != null || onRemove != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = 1f - dismissFraction() }
                        .padding(bottom = navBarBottom + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onReplace?.let { replace ->
                        PhotoActionButton(
                            icon = Icons.Default.PhotoLibrary,
                            label = stringResource(R.string.categories_change_image),
                            tint = Color.White,
                            onClick = replace
                        )
                    }
                    onRemove?.let { remove ->
                        PhotoActionButton(
                            icon = Icons.Default.Delete,
                            label = stringResource(R.string.categories_remove_photo),
                            // Rouge CLAIR (et non `colorScheme.error`) : le fond de la
                            // visionneuse est noir quel que soit le thème de l'app.
                            tint = Color(0xFFFF8A80),
                            onClick = remove
                        )
                    }
                }
            }
        }
      }
    }
}

/**
 * Remonte la chaîne des [ContextWrapper] jusqu'à l'[Activity] hôte : dans un `Dialog`, le
 * `LocalContext` est un contexte ENVELOPPÉ, un simple `as? Activity` échouerait.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Bouton d'action de la visionneuse : pilule sombre lisible sur toute photo. */
@Composable
private fun PhotoActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = tint),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label)
    }
}
