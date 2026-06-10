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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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

/** Cible du geste en cours : image (pan/zoom), cadre (déplacement) ou coin (taille). */
private enum class FrameDragMode { IMAGE, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }

/**
 * Dialog de recadrage full-screen, sans bibliothèque tierce.
 *
 * - [CropShape.CIRCLE]    : masque circulaire (photos de profil)
 * - [CropShape.RECTANGLE] : masque rectangulaire (couvertures de catégories)
 *
 * @param cropAspectRatio W/H du cadre rectangulaire (1f = carré, 4/3f = paysage…).
 *                        Ignoré pour [CropShape.CIRCLE].
 *
 * Mécanique (v5.1) :
 *  - décodage ORIENTÉ : l'orientation EXIF est appliquée au chargement
 *    (ImageDecoder API 28+, sinon BitmapFactory + ExifInterface) — une photo
 *    Paysage reste affichée horizontalement, sans rotation parasite ;
 *  - le bitmap décodé est AUSSI la source d'affichage : ce que l'utilisateur
 *    voit est exactement ce qui est extrait (aucun double traitement) ;
 *  - cadre de sélection central explicite, DÉPLAÇABLE (glisser à l'intérieur)
 *    et REDIMENSIONNABLE (poignées de coin, ratio verrouillé) ;
 *  - l'image reste manipulable : 1 doigt hors du cadre = pan, pincement
 *    (2 doigts) = zoom, où que soient les doigts ;
 *  - bornes strictes : le cadre ne sort jamais de l'image visible ni de
 *    l'écran ; l'image ne découvre jamais le cadre.
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

    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Cadre de rognage : null = valeurs par défaut (centré, taille standard).
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val cW = constraints.maxWidth.toFloat()
            val cH = constraints.maxHeight.toFloat()
            val minFramePx = with(density) { 96.dp.toPx() }
            val cornerTouchPx = with(density) { 28.dp.toPx() }

            // ── Cadre par défaut (centré) ─────────────────────────────────────
            val cropBase = with(density) { 280.dp.toPx() }
            val defaultFrame: Size = when {
                cropShape == CropShape.CIRCLE -> {
                    val sz = cropBase.coerceAtMost(minOf(cW, cH) * 0.80f)
                    Size(sz, sz)
                }
                cropAspectRatio >= 1f -> {
                    val w = cropBase.coerceAtMost(cW * 0.85f)
                    Size(w, (w / cropAspectRatio).coerceAtMost(cH * 0.65f))
                }
                else -> {
                    val h = cropBase.coerceAtMost(cH * 0.65f)
                    Size(h * cropAspectRatio, h)
                }
            }

            // Lectures FRAÎCHES de l'état du cadre (utilisées par les gestes, dont le
            // lambda pointerInput ne re-capture pas les valeurs de composition).
            fun curFrameSize(): Size = frameSizeState ?: defaultFrame
            fun curFrameCenter(): Offset = frameCenterState ?: Offset(cW / 2f, cH / 2f)

            fun fitScaleOf(bmp: Bitmap): Float =
                minOf(cW / bmp.width.toFloat(), cH / bmp.height.toFloat())

            // Échelle minimale : l'image doit toujours COUVRIR le cadre courant.
            fun currentMinScale(bmp: Bitmap): Float {
                val fs = curFrameSize()
                val fit = fitScaleOf(bmp)
                return maxOf(
                    fs.width / (bmp.width * fit),
                    fs.height / (bmp.height * fit),
                    1f
                )
            }

            // Bornes du pan : aucun bord du cadre ne peut découvrir le fond.
            fun clampImageOffsets(bmp: Bitmap) {
                val fit = fitScaleOf(bmp)
                val dispW = bmp.width * fit * scale
                val dispH = bmp.height * fit * scale
                val fc = curFrameCenter()
                val fs = curFrameSize()
                val dX = fc.x - cW / 2f
                val dY = fc.y - cH / 2f
                val loX = dX + fs.width / 2f - dispW / 2f
                val hiX = dX - fs.width / 2f + dispW / 2f
                val loY = dY + fs.height / 2f - dispH / 2f
                val hiY = dY - fs.height / 2f + dispH / 2f
                offsetX = offsetX.coerceIn(minOf(loX, hiX), maxOf(loX, hiX))
                offsetY = offsetY.coerceIn(minOf(loY, hiY), maxOf(loY, hiY))
            }

            fun applyImageGesture(zoomChange: Float, panChange: Offset) {
                val bmp = srcBitmap ?: return
                val newScale = (scale * zoomChange).coerceIn(currentMinScale(bmp), 8f)
                // STABILITÉ (v5.3.4) : le zoom est ANCRÉ au centre du cadre de
                // rognage, pas au centre de l'écran. Le point de l'image visé par
                // le cadre reste immobile pendant le pincement — fini le fond qui
                // « fuit » hors de la zone quand l'image est décalée. Dérivation :
                // offset' = d·(1−k) + offset·k, avec d = centre cadre − centre
                // conteneur et k = rapport d'échelle.
                val k = if (scale != 0f) newScale / scale else 1f
                val fc = curFrameCenter()
                val dX = fc.x - cW / 2f
                val dY = fc.y - cH / 2f
                offsetX = dX * (1f - k) + offsetX * k + panChange.x
                offsetY = dY * (1f - k) + offsetY * k + panChange.y
                scale = newScale
                // Verrou de translation : l'image ne peut JAMAIS découvrir le cadre
                // (bornes recalculées à chaque évènement du geste).
                clampImageOffsets(bmp)
            }

            // Déplace le cadre, borné à l'intersection écran ∩ image affichée.
            fun moveFrame(pan: Offset) {
                val bmp = srcBitmap ?: return
                val fit = fitScaleOf(bmp)
                val dispW = bmp.width * fit * scale
                val dispH = bmp.height * fit * scale
                val imgL = cW / 2f + offsetX - dispW / 2f
                val imgR = cW / 2f + offsetX + dispW / 2f
                val imgT = cH / 2f + offsetY - dispH / 2f
                val imgB = cH / 2f + offsetY + dispH / 2f
                val fs = curFrameSize()
                val minX = maxOf(0f, imgL) + fs.width / 2f
                val maxX = minOf(cW, imgR) - fs.width / 2f
                val minY = maxOf(0f, imgT) + fs.height / 2f
                val maxY = minOf(cH, imgB) - fs.height / 2f
                val c = curFrameCenter() + pan
                frameCenterState = Offset(
                    c.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
                    c.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY))
                )
            }

            // Redimensionne le cadre depuis un coin (ratio verrouillé, ancré au coin
            // opposé), borné par l'écran et l'image affichée.
            fun resizeFrame(mode: FrameDragMode, pan: Offset) {
                val bmp = srcBitmap ?: return
                val sx = if (mode == FrameDragMode.RESIZE_TR || mode == FrameDragMode.RESIZE_BR) 1f else -1f
                val sy = if (mode == FrameDragMode.RESIZE_BL || mode == FrameDragMode.RESIZE_BR) 1f else -1f
                val fs = curFrameSize()
                val fc = curFrameCenter()
                val aspect = fs.width / fs.height
                val anchor = Offset(fc.x - sx * fs.width / 2f, fc.y - sy * fs.height / 2f)
                val fit = fitScaleOf(bmp)
                val dispW = bmp.width * fit * scale
                val dispH = bmp.height * fit * scale
                val imgL = cW / 2f + offsetX - dispW / 2f
                val imgR = cW / 2f + offsetX + dispW / 2f
                val imgT = cH / 2f + offsetY - dispH / 2f
                val imgB = cH / 2f + offsetY + dispH / 2f
                val limX = if (sx > 0) minOf(cW, imgR) - anchor.x else anchor.x - maxOf(0f, imgL)
                val limY = if (sy > 0) minOf(cH, imgB) - anchor.y else anchor.y - maxOf(0f, imgT)
                val maxW = minOf(limX, limY * aspect)
                val d = (sx * pan.x + sy * pan.y) / 2f
                val newW = (fs.width + d).coerceIn(minFramePx, maxOf(minFramePx, maxW))
                val newH = newW / aspect
                frameSizeState = Size(newW, newH)
                frameCenterState = Offset(anchor.x + sx * newW / 2f, anchor.y + sy * newH / 2f)
                clampImageOffsets(bmp)
            }

            // Routage du geste d'après le point de départ : coin → taille ;
            // intérieur du cadre → déplacement du cadre ; ailleurs → image.
            fun frameHitTest(pos: Offset): FrameDragMode {
                val fc = curFrameCenter()
                val fs = curFrameSize()
                val halfW = fs.width / 2f
                val halfH = fs.height / 2f
                val corners = listOf(
                    FrameDragMode.RESIZE_TL to Offset(fc.x - halfW, fc.y - halfH),
                    FrameDragMode.RESIZE_TR to Offset(fc.x + halfW, fc.y - halfH),
                    FrameDragMode.RESIZE_BL to Offset(fc.x - halfW, fc.y + halfH),
                    FrameDragMode.RESIZE_BR to Offset(fc.x + halfW, fc.y + halfH)
                )
                corners.forEach { (mode, corner) ->
                    if ((pos - corner).getDistance() <= cornerTouchPx) return mode
                }
                val inside = pos.x >= fc.x - halfW && pos.x <= fc.x + halfW &&
                    pos.y >= fc.y - halfH && pos.y <= fc.y + halfH
                return if (inside) FrameDragMode.MOVE else FrameDragMode.IMAGE
            }

            // Initialisation au chargement du bitmap : cadre par défaut + zoom minimal.
            LaunchedEffect(srcBitmap, cW, cH) {
                val bmp = srcBitmap ?: return@LaunchedEffect
                frameCenterState = null
                frameSizeState = null
                offsetX = 0f
                offsetY = 0f
                scale = maxOf(
                    defaultFrame.width / (bmp.width * fitScaleOf(bmp)),
                    defaultFrame.height / (bmp.height * fitScaleOf(bmp)),
                    1f
                )
            }

            // Valeurs de composition (affichage Canvas) — recalculées à chaque frame d'état.
            val frameSize = frameSizeState ?: defaultFrame
            val frameCenter = frameCenterState ?: Offset(cW / 2f, cH / 2f)

            // ── Image + routeur de gestes unifié (pas de transformable : un seul
            //    canal tactile décide entre image / cadre / coin, fluide à 120 Hz) ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(srcBitmap, cW, cH) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (srcBitmap == null) return@awaitEachGesture
                            val mode = frameHitTest(down.position)
                            gestureActive = true
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed == 0) break
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (pressed >= 2 || mode == FrameDragMode.IMAGE) {
                                        // Pincement (où que soient les doigts) ou pan hors
                                        // cadre → manipulation de l'IMAGE.
                                        applyImageGesture(zoomChange, panChange)
                                    } else if (mode == FrameDragMode.MOVE) {
                                        moveFrame(panChange)
                                    } else {
                                        resizeFrame(mode, panChange)
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
            ) {
                srcBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX       = scale
                                scaleY       = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
                }
            }

            // ── Overlay : fond sombre + découpe + grille + bordure + poignées ─
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

                    // Grille rule-of-thirds (animée)
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

                    // Bordure fine
                    drawRect(
                        color   = Color.White.copy(alpha = 0.70f),
                        topLeft = tl, size = fr,
                        style   = Stroke(width = 1.5f.dp.toPx())
                    )
                }

                // Poignées de coin (déplacement/redimensionnement) — pour les DEUX
                // formes : elles matérialisent la zone de prise du cadre.
                val hLen = 18.dp.toPx()
                val hSW  = 2.5f.dp.toPx()
                val tlc  = Offset(cx - halfW, cy - halfH)
                val trc  = Offset(cx + halfW, cy - halfH)
                val blc  = Offset(cx - halfW, cy + halfH)
                val brc  = Offset(cx + halfW, cy + halfH)
                listOf(tlc to Pair(+1f, +1f), trc to Pair(-1f, +1f),
                       blc to Pair(+1f, -1f), brc to Pair(-1f, -1f))
                    .forEach { (o, d) ->
                        drawLine(Color.White, o, o + Offset(d.first * hLen, 0f), hSW, StrokeCap.Round)
                        drawLine(Color.White, o, o + Offset(0f, d.second * hLen), hSW, StrokeCap.Round)
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
            // Remontée au-dessus de la barre de gestes / des touches système via les
            // insets de l'ACTIVITÉ (le Dialog est plein écran, edge-to-edge).
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
                        val bmp = srcBitmap ?: return@Button
                        isCropping = true
                        val fs = curFrameSize()
                        val fc = curFrameCenter()
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                executeCrop(
                                    context, bmp,
                                    cropFrameW = fs.width, cropFrameH = fs.height,
                                    frameDeltaX = fc.x - cW / 2f,
                                    frameDeltaY = fc.y - cH / 2f,
                                    fitScale = fitScaleOf(bmp), scale = scale,
                                    offsetX = offsetX, offsetY = offsetY
                                )
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
 * Extrait la région bitmap correspondant au cadre de recadrage (possiblement
 * DÉCENTRÉ : [frameDeltaX]/[frameDeltaY] = centre du cadre − centre du conteneur).
 *
 * Mathématiques (espace centré sur le conteneur) :
 *   totalScale = fitScale × scale
 *   cropX_bmp  = bmpW/2 + (frameDeltaX − cadreW/2 − offsetX) / totalScale
 *   cropW_bmp  = cadreW / totalScale
 *
 * Résultat sauvegardé dans filesDir/crops/ pour persistance.
 */
private fun executeCrop(
    context: Context,
    sourceBitmap: Bitmap,
    cropFrameW: Float,
    cropFrameH: Float,
    frameDeltaX: Float,
    frameDeltaY: Float,
    fitScale: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Uri? = try {
    val totalScale = fitScale * scale

    val cropX = (sourceBitmap.width / 2f + (frameDeltaX - cropFrameW / 2f - offsetX) / totalScale)
        .roundToInt().coerceIn(0, sourceBitmap.width  - 1)
    val cropY = (sourceBitmap.height / 2f + (frameDeltaY - cropFrameH / 2f - offsetY) / totalScale)
        .roundToInt().coerceIn(0, sourceBitmap.height - 1)
    val cropW = (cropFrameW / totalScale).roundToInt()
        .coerceIn(1, sourceBitmap.width  - cropX)
    val cropH = (cropFrameH / totalScale).roundToInt()
        .coerceIn(1, sourceBitmap.height - cropY)

    val cropped = Bitmap.createBitmap(sourceBitmap, cropX, cropY, cropW, cropH)

    // filesDir/crops/ = stockage persistant (pas cacheDir)
    val dir  = File(context.filesDir, "crops").also { it.mkdirs() }
    val dest = File(dir, "crop_${UUID.randomUUID()}.jpg")
    dest.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    cropped.recycle()

    Uri.fromFile(dest)
} catch (_: Exception) { null }
