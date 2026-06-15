package com.jtr.app.ui.person

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import com.jtr.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Côté maximal du bitmap décodé (sous-échantillonnage des photos très lourdes). */
private const val MAX_DECODE_DIM = 4096

/** Taille par défaut du cadre = fraction du plus grand cadre tenant dans l'image. */
private const val DEFAULT_FRAME_FRACTION = 0.8f

/** Zoom maximal de l'IMAGE (par-dessus l'ajustement FIT de base). */
private const val MAX_IMAGE_ZOOM = 5f

/** Coin du cadre saisi pour le redimensionnement (drag 1 doigt sur une poignée). */
private enum class CropCorner { TL, TR, BL, BR }

/**
 * Dialog de recadrage full-screen, sans bibliothèque tierce.
 *
 * - [CropShape.CIRCLE]    : masque circulaire (photos de profil)
 * - [CropShape.RECTANGLE] : masque rectangulaire (couvertures de catégories)
 *
 * @param cropAspectRatio W/H du cadre rectangulaire (1f = carré, 4/3f = paysage…).
 *                        Ignoré pour [CropShape.CIRCLE] (toujours 1:1).
 *
 * Modèle (v6.0.5) — **image affichée en FIT (entière, letterbox accepté) + CADRE
 * mobile/redimensionnable** (acquis v6.0.4), AUGMENTÉ du **zoom/pan de l'image**.
 * Séparation STRICTE des gestes :
 *  - **1 doigt sur le corps du cadre** → déplace le cadre ;
 *  - **1 doigt sur une poignée d'angle** → redimensionne le cadre (ratio verrouillé) ;
 *  - **2 doigts** → pincement = zoome l'image, glissement = déplace l'image.
 *
 * INVARIANT : le cadre reste TOUJOURS entièrement dans le rectangle RÉEL de l'image
 * (rect FIT × imageZoom + imageOffset) ∩ viewport — jamais sur le letterbox/noir,
 * à tout niveau de zoom/pan. Re-clamp du cadre après chaque transformation d'image.
 */
@Composable
fun ImageCropDialog(
    sourceUri: Uri,
    cropShape: CropShape,
    cropAspectRatio: Float = 1f,
    onCropComplete: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope   = rememberCoroutineScope()

    // Insets relevés dans la composition de l'ACTIVITÉ (avant d'entrer dans le Dialog) :
    // sur certains appareils (barre de navigation 3 boutons Samsung notamment), les
    // WindowInsets ne sont pas dispatchés à la fenêtre du Dialog malgré
    // decorFitsSystemWindows = false. Les insets de l'activité, eux, sont fiables.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var isCropping     by remember { mutableStateOf(false) }
    var isTransforming by remember { mutableStateOf(false) }
    var gestureActive  by remember { mutableStateOf(false) }

    // Transformation de l'IMAGE (par-dessus le FIT) : zoom + déplacement.
    var imageZoom    by remember { mutableFloatStateOf(1f) }
    var imageOffsetX by remember { mutableFloatStateOf(0f) }
    var imageOffsetY by remember { mutableFloatStateOf(0f) }

    // Cadre de rognage : null = valeurs par défaut.
    var frameCenterState by remember { mutableStateOf<Offset?>(null) }
    var frameSizeState by remember { mutableStateOf<Size?>(null) }

    // Décodage ORIENTÉ (EXIF appliqué) en arrière-plan — source unique d'affichage
    // ET d'extraction.
    val srcBitmap by produceState<Bitmap?>(null, sourceUri) {
        value = withContext(Dispatchers.IO) { decodeOrientedBitmap(context, sourceUri) }
    }

    // Grille rule-of-thirds : apparaît pendant le geste, s'estompe après.
    val gridAlpha by animateFloatAsState(
        targetValue    = if (isTransforming) 0.38f else 0f,
        animationSpec  = tween(if (isTransforming) 80 else 700),
        label          = "grid_alpha"
    )
    LaunchedEffect(gestureActive) {
        if (gestureActive) {
            isTransforming = true
        } else {
            delay(700)
            isTransforming = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false : rendu plein écran edge-to-edge. Les marges
        // de sécurité (boutons au-dessus des touches système) sont appliquées via les
        // insets capturés ci-dessus dans la composition de l'activité.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
      // RTL : le recadrage opère entièrement en pixels (cadre, zoom/pan via
      // graphicsLayer, alignement centré) → on force LTR pour un comportement
      // strictement identique en arabe (aucun miroir parasite du déplacement).
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val cW = constraints.maxWidth.toFloat()
            val cH = constraints.maxHeight.toFloat()
            val minFramePx = with(density) { 80.dp.toPx() }
            val cornerGrabPx = with(density) { 24.dp.toPx() } // zone de préhension ~44dp

            // Ratio W/H imposé au cadre : 1:1 pour le cercle, sinon le ratio cible.
            val aspect = if (cropShape == CropShape.CIRCLE) 1f else cropAspectRatio

            // Échelle FIT de base (image entière, centrée, non zoomée).
            fun fitScaleOf(bmp: Bitmap): Float =
                minOf(cW / bmp.width.toFloat(), cH / bmp.height.toFloat())

            // Rectangle RÉEL de l'image affichée = rect FIT × imageZoom + imageOffset.
            // (graphicsLayer met à l'échelle autour du centre du viewport.)
            fun realW(bmp: Bitmap) = bmp.width * fitScaleOf(bmp) * imageZoom
            fun realH(bmp: Bitmap) = bmp.height * fitScaleOf(bmp) * imageZoom
            fun realLeft(bmp: Bitmap) = cW / 2f + imageOffsetX - realW(bmp) / 2f
            fun realTop(bmp: Bitmap) = cH / 2f + imageOffsetY - realH(bmp) / 2f

            // Zone de travail = rectangle réel de l'image ∩ viewport : le cadre doit y
            // tenir (dans les pixels de l'image ET visible à l'écran).
            fun workL(bmp: Bitmap) = maxOf(0f, realLeft(bmp))
            fun workT(bmp: Bitmap) = maxOf(0f, realTop(bmp))
            fun workR(bmp: Bitmap) = minOf(cW, realLeft(bmp) + realW(bmp))
            fun workB(bmp: Bitmap) = minOf(cH, realTop(bmp) + realH(bmp))
            fun workW(bmp: Bitmap) = workR(bmp) - workL(bmp)
            fun workH(bmp: Bitmap) = workB(bmp) - workT(bmp)

            fun maxFrameW(bmp: Bitmap) = minOf(workW(bmp), workH(bmp) * aspect)
            fun minFrameW(bmp: Bitmap) = minOf(minFramePx, maxFrameW(bmp))

            fun defaultFrameSize(bmp: Bitmap): Size {
                val w = (maxFrameW(bmp) * DEFAULT_FRAME_FRACTION).coerceAtLeast(minFrameW(bmp))
                return Size(w, w / aspect)
            }

            fun curFrameSize(bmp: Bitmap): Size = frameSizeState ?: defaultFrameSize(bmp)
            fun curFrameCenter(): Offset = frameCenterState ?: Offset(cW / 2f, cH / 2f)

            // Replace le cadre pour qu'il reste INTÉGRALEMENT dans la zone de travail.
            fun clampFrameCenter(bmp: Bitmap) {
                val fs = curFrameSize(bmp)
                val minX = workL(bmp) + fs.width / 2f
                val maxX = workR(bmp) - fs.width / 2f
                val minY = workT(bmp) + fs.height / 2f
                val maxY = workB(bmp) - fs.height / 2f
                val c = curFrameCenter()
                frameCenterState = Offset(
                    c.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
                    c.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY))
                )
            }

            // Re-cale le cadre dans la zone de travail courante (après zoom/pan image) :
            // borne d'abord la TAILLE, puis la POSITION.
            fun clampFrameToImage(bmp: Bitmap) {
                val w = curFrameSize(bmp).width.coerceIn(minFrameW(bmp), maxFrameW(bmp))
                frameSizeState = Size(w, w / aspect)
                clampFrameCenter(bmp)
            }

            // 1 doigt sur le corps → déplacement du cadre.
            fun moveFrame(pan: Offset) {
                val bmp = srcBitmap ?: return
                frameCenterState = curFrameCenter() + pan
                clampFrameCenter(bmp)
            }

            // 1 doigt sur une poignée d'angle → redimensionnement (ratio verrouillé,
            // ancré au coin opposé), borné par la zone de travail.
            fun resizeFrame(corner: CropCorner, pan: Offset) {
                val bmp = srcBitmap ?: return
                val sx = if (corner == CropCorner.TR || corner == CropCorner.BR) 1f else -1f
                val sy = if (corner == CropCorner.BL || corner == CropCorner.BR) 1f else -1f
                val fs = curFrameSize(bmp)
                val fc = curFrameCenter()
                val anchor = Offset(fc.x - sx * fs.width / 2f, fc.y - sy * fs.height / 2f)
                val limX = if (sx > 0) workR(bmp) - anchor.x else anchor.x - workL(bmp)
                val limY = if (sy > 0) workB(bmp) - anchor.y else anchor.y - workT(bmp)
                val maxW = minOf(limX, limY * aspect)
                val d = (sx * pan.x + sy * pan.y) / 2f
                val newW = (fs.width + d).coerceIn(minFrameW(bmp), maxOf(minFrameW(bmp), maxW))
                val newH = newW / aspect
                frameSizeState = Size(newW, newH)
                frameCenterState = Offset(anchor.x + sx * newW / 2f, anchor.y + sy * newH / 2f)
                clampFrameCenter(bmp)
            }

            // 2 doigts → zoom (ancré sur le centroïde) + déplacement de l'IMAGE.
            fun applyImageGesture(zoomChange: Float, pan: Offset, centroid: Offset) {
                val bmp = srcBitmap ?: return
                val newZoom = (imageZoom * zoomChange).coerceIn(1f, MAX_IMAGE_ZOOM)
                val k = if (imageZoom != 0f) newZoom / imageZoom else 1f
                val dX = centroid.x - cW / 2f
                val dY = centroid.y - cH / 2f
                imageOffsetX = dX * (1f - k) + imageOffsetX * k + pan.x
                imageOffsetY = dY * (1f - k) + imageOffsetY * k + pan.y
                imageZoom = newZoom
                // Clamp pan image : ne jamais traîner l'image hors de vue.
                val maxX = maxOf(0f, (realW(bmp) - cW) / 2f)
                val maxY = maxOf(0f, (realH(bmp) - cH) / 2f)
                imageOffsetX = imageOffsetX.coerceIn(-maxX, maxX)
                imageOffsetY = imageOffsetY.coerceIn(-maxY, maxY)
                // INVARIANT : le cadre suit le nouveau rectangle d'image.
                clampFrameToImage(bmp)
            }

            // Hit-test des 4 poignées d'angle (drag 1 doigt = redimension).
            fun cornerAt(pos: Offset): CropCorner? {
                val bmp = srcBitmap ?: return null
                val fc = curFrameCenter()
                val fs = curFrameSize(bmp)
                val hw = fs.width / 2f
                val hh = fs.height / 2f
                val corners = listOf(
                    CropCorner.TL to Offset(fc.x - hw, fc.y - hh),
                    CropCorner.TR to Offset(fc.x + hw, fc.y - hh),
                    CropCorner.BL to Offset(fc.x - hw, fc.y + hh),
                    CropCorner.BR to Offset(fc.x + hw, fc.y + hh)
                )
                return corners.firstOrNull { (_, c) -> (pos - c).getDistance() <= cornerGrabPx }?.first
            }

            // (Ré)initialisation à chaque nouveau bitmap / changement de taille :
            // image au repos (FIT, zoom 1) et cadre par défaut.
            LaunchedEffect(srcBitmap, cW, cH) {
                imageZoom = 1f
                imageOffsetX = 0f
                imageOffsetY = 0f
                frameCenterState = null
                frameSizeState = null
            }

            // Valeurs d'affichage (cadre) — recalculées à chaque frame d'état.
            val bmp = srcBitmap
            val frameSize = if (bmp != null) curFrameSize(bmp) else Size.Zero
            val frameCenter = curFrameCenter()

            // ── Image (FIT) + transformation zoom/pan via graphicsLayer ───────
            srcBitmap?.let { image ->
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX       = imageZoom
                            scaleY       = imageZoom
                            translationX = imageOffsetX
                            translationY = imageOffsetY
                        }
                )
            }

            // ── Couche de gestes : routage strict (cadre vs image) ────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(srcBitmap, cW, cH) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (srcBitmap == null) return@awaitEachGesture
                            // Mode du geste 1 doigt figé au départ : poignée → resize,
                            // sinon → déplacement. (2 doigts = image, décidé par évènement.)
                            val startCorner = cornerAt(down.position)
                            gestureActive = true
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed == 0) break
                                    if (pressed >= 2) {
                                        applyImageGesture(
                                            event.calculateZoom(),
                                            event.calculatePan(),
                                            event.calculateCentroid()
                                        )
                                    } else if (startCorner != null) {
                                        resizeFrame(startCorner, event.calculatePan())
                                    } else {
                                        moveFrame(event.calculatePan())
                                    }
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            } finally {
                                gestureActive = false
                            }
                        }
                    }
            )

            // ── Overlay : fond sombre + découpe du cadre + grille + bordure + poignées ─
            if (bmp != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    val cx    = frameCenter.x
                    val cy    = frameCenter.y
                    val halfW = frameSize.width  / 2f
                    val halfH = frameSize.height / 2f

                    drawRect(Color.Black.copy(alpha = 0.58f))

                    if (cropShape == CropShape.CIRCLE) {
                        drawCircle(
                            color     = Color.Transparent,
                            radius    = halfW,
                            center    = Offset(cx, cy),
                            blendMode = BlendMode.Clear
                        )
                        drawCircle(
                            color  = Color.White,
                            radius = halfW,
                            center = Offset(cx, cy),
                            style  = Stroke(width = 1.5f.dp.toPx())
                        )
                    } else {
                        val tl = Offset(cx - halfW, cy - halfH)
                        val fr = Size(frameSize.width, frameSize.height)

                        drawRect(Color.Transparent, tl, fr, blendMode = BlendMode.Clear)

                        if (gridAlpha > 0f) {
                            repeat(2) { i ->
                                val gx = cx - halfW + frameSize.width * (i + 1) / 3f
                                val gy = cy - halfH + frameSize.height * (i + 1) / 3f
                                val sw = 0.6f.dp.toPx()
                                val gc = Color.White.copy(alpha = gridAlpha)
                                drawLine(gc, Offset(gx, cy - halfH), Offset(gx, cy + halfH), sw)
                                drawLine(gc, Offset(cx - halfW, gy), Offset(cx + halfW, gy), sw)
                            }
                        }

                        drawRect(
                            color   = Color.White.copy(alpha = 0.70f),
                            topLeft = tl, size = fr,
                            style   = Stroke(width = 1.5f.dp.toPx())
                        )
                    }

                    // Poignées d'angle (zone tactile = redimensionnement du cadre).
                    val hLen = 18.dp.toPx()
                    val hSW  = 2.5f.dp.toPx()
                    listOf(
                        Offset(cx - halfW, cy - halfH) to Pair(+1f, +1f),
                        Offset(cx + halfW, cy - halfH) to Pair(-1f, +1f),
                        Offset(cx - halfW, cy + halfH) to Pair(+1f, -1f),
                        Offset(cx + halfW, cy + halfH) to Pair(-1f, -1f)
                    ).forEach { (o, d) ->
                        drawLine(Color.White, o, o + Offset(d.first * hLen, 0f), hSW, StrokeCap.Round)
                        drawLine(Color.White, o, o + Offset(0f, d.second * hLen), hSW, StrokeCap.Round)
                    }
                }
            }

            // ── Indicateur de chargement ──────────────────────────────────────
            if (srcBitmap == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = Color.White
                )
            }

            // ── Bouton fermer ─────────────────────────────────────────────────
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = topInset)
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }

            // ── Instruction ───────────────────────────────────────────────────
            if (srcBitmap != null) {
                Text(
                    text      = stringResource(R.string.crop_hint),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset + 60.dp, start = 64.dp, end = 16.dp)
                )
            }

            // ── Barre du bas : Annuler / Recadrer ─────────────────────────────
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomInset)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
                }
                Button(
                    onClick = {
                        val image = srcBitmap ?: return@Button
                        isCropping = true
                        // Compose les DEUX transformations : FIT × imageZoom, + origine
                        // du rect réel (qui intègre imageOffset).
                        val totalScale = fitScaleOf(image) * imageZoom
                        val rL = realLeft(image)
                        val rT = realTop(image)
                        val fs = curFrameSize(image)
                        val fc = curFrameCenter()
                        val cropXf = (fc.x - fs.width / 2f - rL) / totalScale
                        val cropYf = (fc.y - fs.height / 2f - rT) / totalScale
                        val cropWf = fs.width / totalScale
                        val cropHf = fs.height / totalScale
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                executeCrop(context, image, cropXf, cropYf, cropWf, cropHf)
                            }
                            isCropping = false
                            if (result != null) onCropComplete(result)
                        }
                    },
                    enabled = !isCropping && srcBitmap != null,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = Color.Black
                    )
                ) {
                    if (isCropping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black
                        )
                    } else {
                        Text(stringResource(R.string.crop_action))
                    }
                }
            }
        }
      }
    }
}

/**
 * Décode [uri] en appliquant l'orientation EXIF : une photo Paysage reste
 * horizontale. API 28+ : [ImageDecoder] (orientation native) en allocation
 * LOGICIELLE (requise par createBitmap/compress). Avant : BitmapFactory +
 * rotation manuelle d'après [ExifInterface]. Les très grandes images sont
 * sous-échantillonnées (côté ≤ [MAX_DECODE_DIM]) pour fiabiliser la mémoire.
 */
private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDim = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (maxDim / sample > MAX_DECODE_DIM) sample *= 2
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
    } else {
        decodeWithExifFallback(context, uri)
    }
} catch (_: Exception) { null }

/** Repli API 26-27 : décodage brut + rotation manuelle selon le tag EXIF. */
private fun decodeWithExifFallback(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxDim / sample > MAX_DECODE_DIM) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null
    val rotation = context.contentResolver.openInputStream(uri)?.use { input ->
        when (ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    return if (rotation == 0f) raw
    else Bitmap.createBitmap(
        raw, 0, 0, raw.width, raw.height,
        Matrix().apply { postRotate(rotation) }, true
    )
}

/**
 * Extrait la région bitmap (déjà exprimée en pixels source) sous le cadre. Le
 * cadre étant toujours contraint dans l'image réelle, la région reste dans le
 * bitmap (coerce de sûreté). Résultat sauvegardé dans filesDir/crops/.
 */
private fun executeCrop(
    context: Context,
    sourceBitmap: Bitmap,
    cropX: Float,
    cropY: Float,
    cropW: Float,
    cropH: Float
): Uri? = try {
    val x = cropX.roundToInt().coerceIn(0, sourceBitmap.width - 1)
    val y = cropY.roundToInt().coerceIn(0, sourceBitmap.height - 1)
    val w = cropW.roundToInt().coerceIn(1, sourceBitmap.width - x)
    val h = cropH.roundToInt().coerceIn(1, sourceBitmap.height - y)

    val cropped = Bitmap.createBitmap(sourceBitmap, x, y, w, h)

    // filesDir/crops/ = stockage persistant (pas cacheDir)
    val dir  = File(context.filesDir, "crops").also { it.mkdirs() }
    val dest = File(dir, "crop_${UUID.randomUUID()}.jpg")
    dest.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    cropped.recycle()

    Uri.fromFile(dest)
} catch (_: Exception) { null }
