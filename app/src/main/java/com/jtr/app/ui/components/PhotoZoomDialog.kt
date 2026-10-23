package com.jtr.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jtr.app.R

/**
 * Visionneuse plein écran RÉUTILISABLE (v7.1.2, extraite de `PersonDetailScreen` en v7.1.49) :
 * zoom pincement, pan borné, double-tap ancré et glisser-pour-fermer façon Instagram.
 *
 * v7.1.91 — le moteur de gestes vit désormais dans [ZoomableImage] / [ZoomState] : cet écran
 * n'est plus que son ENVELOPPE (fond, insets de dialogue, contrôles). Le même moteur sert au
 * pager de la galerie — un SEUL réglage de gestes pour toute l'app, deux points d'usage.
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
    val state = rememberZoomState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      // RTL : la visionneuse opère en pixels (zoom/pan via graphicsLayer, ancrage
      // double-tap en coordonnées locales) → on force LTR pour rester direction-
      // agnostique (gestes identiques en arabe).
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val (_, navBarBottom) = rememberSystemBarsDp()
        // v7.1.91 (retouche) : recentre l'IMAGE sur l'écran réel. Appliquée à la seule photo
        // — surtout PAS au fond, qui doit continuer à couvrir toute la fenêtre.
        val centerCorrection = rememberVisualCenterCorrectionPx()

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // FOND noir / letterbox : s'estompe à mesure du glissement de fermeture
            // (alpha lu en phase de dessin → pas de recomposition). Tap simple =
            // fermeture IMMÉDIATE (aucun onDoubleTap ici → fermeture snappy).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - state.dismissFraction() }
                    .background(Color.Black.copy(alpha = 0.92f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )

            // Moteur de gestes partagé : mono-image, donc un glissement dans N'IMPORTE
            // quelle direction ferme (pas de pager à ménager ici).
            ZoomableImage(
                model = photoUri,
                onDismiss = onDismiss,
                modifier = Modifier.offset { IntOffset(0, centerCorrection) },
                state = state,
                horizontalDragGoesToParent = false,
                onTap = onDismiss
            )

            // Croix sur un scrim sombre circulaire → TOUJOURS visible, même sur une
            // photo claire/blanche.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    // S'estompe avec le glissement de fermeture (contrôles masqués).
                    .graphicsLayer { alpha = 1f - state.dismissFraction() }
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
            // ⚠️ Volontairement HORS de la zone photo : un bouton posé DEDANS verrait ses
            // taps ratés interceptés par le `detectTapGestures` de la photo, qui FERME la
            // visionneuse. Ici, de simples boutons cohabitent sans toucher au moteur de
            // gestes. Estompés par le MÊME `dismissFraction()` que la croix → les contrôles
            // disparaissent en synchro avec le fond, l'échelle et les coins.
            if (onReplace != null || onRemove != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = 1f - state.dismissFraction() }
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
 * Correction verticale (px) qui recentre le contenu d'un `Dialog` sur l'écran RÉEL
 * (v7.1.91, retouche).
 *
 * ⚠️ MESURÉ AU PIXEL SUR S21 — la fenêtre d'un dialogue plein écran reçoit bien la HAUTEUR
 * de l'écran (Compose y mesure un conteneur de 2400 et y centre la photo à y = 700 : centre
 * 1200, parfait en coordonnées de FENÊTRE), mais le gestionnaire de fenêtres la POSE sous la
 * barre d'état (origine y = 80) : elle déborde de 80 px sous l'écran et tout son contenu
 * centré apparaît **80 px trop bas**. L'image n'était pas décentrée, la FENÊTRE l'était.
 *
 * Deux pistes ont été essayées et MESURÉES comme fausses : `decorFitsSystemWindows = false`
 * ne déplace rien du tout, et `FLAG_LAYOUT_NO_LIMITS` replace bien la fenêtre à y = 0 mais
 * REDIMENSIONNE sa zone utile (2336 au lieu de 2400) → l'image repart 32 px trop HAUT.
 *
 * On ne force donc plus la géométrie de la fenêtre : on la MESURE (position réelle à l'écran
 * + hauteur réelle du contenu) et on décale l'image d'autant. Auto-correctif : aucune
 * hypothèse sur la hauteur des barres, la navigation par gestes ou le découpage d'écran.
 */
@Composable
internal fun rememberVisualCenterCorrectionPx(): Int {
    val view = LocalView.current
    var correction by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (view.height > 0) {
                val location = IntArray(2).also { view.getLocationOnScreen(it) }
                val screenHeight = view.context.realScreenHeightPx()
                correction = screenHeight / 2 - (location[1] + view.height / 2)
            }
        }
        observer.addOnGlobalLayoutListener(listener)
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }
    return correction
}

/** Hauteur de l'écran PHYSIQUE, barres comprises — et non celle de la fenêtre courante. */
@Suppress("DEPRECATION")
private fun Context.realScreenHeightPx(): Int {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        wm.maximumWindowMetrics.bounds.height()
    } else {
        android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.heightPixels
    }
}

/**
 * Hauteurs RÉELLES des barres système (haut, bas), partagées par les deux visionneuses.
 *
 * ⚠️ INSETS EN FENÊTRE DE DIALOG (v7.1.49) — mesuré sur S21, navigation 3 boutons : à
 * l'INTÉRIEUR d'un `Dialog`, TOUTES les sources d'insets rapportent ZÉRO — les
 * `WindowInsets` de Compose (`navigationBarsPadding()`), `decorFitsSystemWindows = false`,
 * et même `getRootWindowInsets` sur la vue du dialogue — alors que la fenêtre s'étend bel
 * et bien sous les barres : les contrôles se dessinaient PAR-DESSUS les touches. On
 * interroge donc la fenêtre de l'ACTIVITÉ, seule à être posée et mesurée, donc seule à
 * renvoyer l'inset RÉEL. 0 en navigation par gestes → aucun espace parasite.
 */
@Composable
internal fun rememberSystemBarsDp(): Pair<Dp, Dp> {
    val hostView = LocalView.current
    val density = LocalDensity.current
    return remember(hostView, density) {
        val decor = hostView.context.findActivity()?.window?.decorView
        val bars = decor?.let { ViewCompat.getRootWindowInsets(it) }
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        with(density) { (bars?.top ?: 0).toDp() to (bars?.bottom ?: 0).toDp() }
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
