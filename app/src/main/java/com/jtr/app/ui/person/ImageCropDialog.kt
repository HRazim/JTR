package com.jtr.app.ui.person

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jtr.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Dialog de recadrage full-screen, sans bibliothèque tierce.
 *
 * - [CropShape.CIRCLE]    : masque circulaire (photos de profil)
 * - [CropShape.RECTANGLE] : masque rectangulaire (couvertures de catégories)
 *
 * @param cropAspectRatio W/H du cadre rectangulaire (1f = carré, 4/3f = paysage…).
 *                        Ignoré pour [CropShape.CIRCLE].
 *
 * Corrections vs. version précédente :
 *  - scale initialisé à minScale dès le chargement du bitmap
 *  - cadre rectangulaire non-carré correctement supporté
 *  - halfExcess contraint à ≥ 0 (évite les coerceIn invalides)
 *  - sauvegarde dans filesDir (persistant, pas cacheDir)
 *  - grille rule-of-thirds animée pour RECTANGLE
 *  - poignées de coin pour RECTANGLE
 *  - indicateur de chargement pendant la décompression bitmap
 *  - gestures désactivées tant que le bitmap source n'est pas prêt
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

    var isCropping     by remember { mutableStateOf(false) }
    var isTransforming by remember { mutableStateOf(false) }

    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Chargement bitmap en arrière-plan — utilisé uniquement pour l'extraction finale
    val srcBitmap by produceState<android.graphics.Bitmap?>(null, sourceUri) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(sourceUri)
                ?.use { BitmapFactory.decodeStream(it) }
        }
    }

    // Grille rule-of-thirds : apparaît pendant le geste, s'estompe après
    val gridAlpha by animateFloatAsState(
        targetValue    = if (isTransforming) 0.38f else 0f,
        animationSpec  = tween(if (isTransforming) 80 else 700),
        label          = "grid_alpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false : rendu plein écran edge-to-edge ET les
        // WindowInsets (barres système) sont dispatchés aux composables, ce qui
        // permet à navigationBarsPadding() de remonter correctement les boutons.
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

            // ── Dimensions du cadre de recadrage ─────────────────────────────
            val cropBase = with(density) { 280.dp.toPx() }
            val cropFrameW: Float
            val cropFrameH: Float
            if (cropShape == CropShape.CIRCLE) {
                val sz = cropBase.coerceAtMost(minOf(cW, cH) * 0.80f)
                cropFrameW = sz; cropFrameH = sz
            } else if (cropAspectRatio >= 1f) {
                cropFrameW = cropBase.coerceAtMost(cW * 0.85f)
                cropFrameH = (cropFrameW / cropAspectRatio).coerceAtMost(cH * 0.65f)
            } else {
                cropFrameH = cropBase.coerceAtMost(cH * 0.65f)
                cropFrameW = cropFrameH * cropAspectRatio
            }

            // ── Échelles (disponibles uniquement si bitmap chargé) ────────────
            val fitScale: Float
            val minScale: Float
            if (srcBitmap != null) {
                fitScale = minOf(cW / srcBitmap!!.width.toFloat(),
                                 cH / srcBitmap!!.height.toFloat())
                minScale = maxOf(
                    cropFrameW / (srcBitmap!!.width  * fitScale),
                    cropFrameH / (srcBitmap!!.height * fitScale)
                ).coerceAtLeast(1f)
            } else {
                fitScale = 1f; minScale = 1f
            }

            // Initialise scale à minScale dès que le bitmap est disponible
            LaunchedEffect(srcBitmap, cW, cH) {
                if (srcBitmap != null) {
                    scale = minScale; offsetX = 0f; offsetY = 0f
                }
            }

            // ── État gestuel ──────────────────────────────────────────────────
            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                isTransforming = true
                scale = (scale * zoomChange).coerceIn(minScale, 8f)
                val bmp = srcBitmap ?: return@rememberTransformableState
                val hExX = ((bmp.width  * fitScale * scale) - cropFrameW).coerceAtLeast(0f) / 2f
                val hExY = ((bmp.height * fitScale * scale) - cropFrameH).coerceAtLeast(0f) / 2f
                offsetX = (offsetX + panChange.x).coerceIn(-hExX, hExX)
                offsetY = (offsetY + panChange.y).coerceIn(-hExY, hExY)
            }

            // Estompe la grille 700 ms après la fin du geste
            LaunchedEffect(transformState.isTransformInProgress) {
                if (!transformState.isTransformInProgress) {
                    delay(700); isTransforming = false
                }
            }

            // ── Image zoomable (gestures désactivées tant que bitmap non prêt) ─
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (srcBitmap != null)
                            Modifier.transformable(state = transformState)
                        else Modifier
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(sourceUri).crossfade(false).build(),
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

            // ── Overlay : fond sombre + découpe + grille + bordure + poignées ─
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                val cx    = size.width  / 2f
                val cy    = size.height / 2f
                val halfW = cropFrameW  / 2f
                val halfH = cropFrameH  / 2f

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
                    val fr = Size(cropFrameW, cropFrameH)

                    drawRect(Color.Transparent, tl, fr, blendMode = BlendMode.Clear)

                    // Grille rule-of-thirds (animée)
                    if (gridAlpha > 0f) {
                        repeat(2) { i ->
                            val gx = cx - halfW + cropFrameW * (i + 1) / 3f
                            val gy = cy - halfH + cropFrameH * (i + 1) / 3f
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

                    // Poignées de coin
                    val hLen = 18.dp.toPx()
                    val hSW  = 2.5f.dp.toPx()
                    val tr   = Offset(cx + halfW, cy - halfH)
                    val bl   = Offset(cx - halfW, cy + halfH)
                    val br   = Offset(cx + halfW, cy + halfH)
                    listOf(tl to Pair(+1f, +1f), tr to Pair(-1f, +1f),
                           bl to Pair(+1f, -1f), br to Pair(-1f, -1f))
                        .forEach { (o, d) ->
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
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
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
                        .padding(top = 60.dp, start = 64.dp, end = 16.dp)
                )
            }

            // ── Barre du bas : Annuler / Recadrer ─────────────────────────────
            // navigationBarsPadding() remonte la rangée juste au-dessus de la barre
            // de gestes / des touches système (le Dialog est plein écran, edge-to-edge).
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
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
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                executeCrop(context, bmp, cropFrameW, cropFrameH,
                                            fitScale, scale, offsetX, offsetY)
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
 * Extrait la région bitmap correspondant au cadre de recadrage.
 *
 * Mathématiques (espace centré sur le conteneur) :
 *   totalScale = fitScale × scale
 *   cropX_bmp  = bmpW/2 − cropFrameW / (2 × totalScale) − offsetX / totalScale
 *   cropW_bmp  = cropFrameW / totalScale
 *
 * Résultat sauvegardé dans filesDir/crops/ pour persistance.
 */
private fun executeCrop(
    context: Context,
    sourceBitmap: android.graphics.Bitmap,
    cropFrameW: Float,
    cropFrameH: Float,
    fitScale: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Uri? = try {
    val totalScale = fitScale * scale

    val cropX = (sourceBitmap.width  / 2f - cropFrameW / (2f * totalScale) - offsetX / totalScale)
        .roundToInt().coerceIn(0, sourceBitmap.width  - 1)
    val cropY = (sourceBitmap.height / 2f - cropFrameH / (2f * totalScale) - offsetY / totalScale)
        .roundToInt().coerceIn(0, sourceBitmap.height - 1)
    val cropW = (cropFrameW / totalScale).roundToInt()
        .coerceIn(1, sourceBitmap.width  - cropX)
    val cropH = (cropFrameH / totalScale).roundToInt()
        .coerceIn(1, sourceBitmap.height - cropY)

    val cropped = android.graphics.Bitmap.createBitmap(sourceBitmap, cropX, cropY, cropW, cropH)

    // filesDir/crops/ = stockage persistant (pas cacheDir)
    val dir  = File(context.filesDir, "crops").also { it.mkdirs() }
    val dest = File(dir, "crop_${UUID.randomUUID()}.jpg")
    dest.outputStream().use { cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
    cropped.recycle()

    Uri.fromFile(dest)
} catch (_: Exception) { null }
