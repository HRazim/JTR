package com.jtr.app.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import com.jtr.app.R
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.home.PersonAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Feuille de partage contextuelle (TEXTE / PNG / PDF), générique pour les profils
 * ET les catégories. L'aperçu affiché est enregistré dans un [rememberGraphicsLayer]
 * À CHAQUE frame (drawWithContent) : le bouton PNG capture donc EXACTEMENT ce que
 * l'utilisateur voit, sans recomposition parallèle ni View hiérarchique.
 *
 * L'état « Exportation en cours… » est un état d'UI réactif qui désactive les
 * actions et affiche un indicateur — aucune ressource ne fuit (fichiers générés
 * dans cache/share, purgés à l'ouverture suivante).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFormatSheet(
    onDismiss: () -> Unit,
    buildText: () -> String,
    writePdf: () -> File,
    // Capture PNG verrouillée tant que les miniatures Coil de l'aperçu ne sont
    // pas TOUTES résolues (v5.5) : l'export reflète toujours les vraies images,
    // jamais les placeholders. Le PDF n'en dépend pas (décodage synchrone).
    previewReady: Boolean = true,
    preview: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var isExporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.share_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Aperçu capturable : le contenu est rejoué dans le GraphicsLayer puis
            // dessiné normalement (drawLayer) — zéro coût visuel supplémentaire.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
            ) { preview() }

            Spacer(Modifier.height(12.dp))

            if (isExporting || !previewReady) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            if (isExporting) R.string.share_exporting
                            else R.string.share_preview_loading
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (exportFailed) {
                Text(stringResource(R.string.share_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp))
            }

            // 1. TEXTE — chaîne stylisée via Intent ACTION_SEND immédiat.
            ShareOptionRow(
                icon = Icons.AutoMirrored.Filled.Notes,
                label = stringResource(R.string.share_as_text),
                enabled = !isExporting
            ) {
                ShareUtils.clearStaleExports(context)
                ShareUtils.shareText(context, buildText())
                onDismiss()
            }
            // 2. PNG — capture du GraphicsLayer → cache → FileProvider.
            ShareOptionRow(
                icon = Icons.Default.Image,
                label = stringResource(R.string.share_as_image),
                enabled = !isExporting && previewReady
            ) {
                scope.launch {
                    isExporting = true
                    exportFailed = false
                    val shared = runCatching {
                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                        val file = withContext(Dispatchers.IO) {
                            ShareUtils.clearStaleExports(context)
                            ShareUtils.saveBitmapPng(context, bitmap)
                        }
                        ShareUtils.shareFile(context, file, "image/png")
                    }.isSuccess
                    isExporting = false
                    if (shared) onDismiss() else exportFailed = true
                }
            }
            // 3. PDF — document natif PdfDocument généré sur Dispatchers.IO.
            ShareOptionRow(
                icon = Icons.Default.PictureAsPdf,
                label = stringResource(R.string.share_as_pdf),
                enabled = !isExporting
            ) {
                scope.launch {
                    isExporting = true
                    exportFailed = false
                    val shared = runCatching {
                        val file = withContext(Dispatchers.IO) {
                            ShareUtils.clearStaleExports(context)
                            writePdf()
                        }
                        ShareUtils.shareFile(context, file, "application/pdf")
                    }.isSuccess
                    isExporting = false
                    if (shared) onDismiss() else exportFailed = true
                }
            }
        }
    }
}

/** Rangée d'option de format (icône + libellé). */
@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/**
 * Aperçu capturable des profils sélectionnés (6 max + indicateur de surplus).
 * [onImagesReadyChange] notifie quand TOUTES les miniatures Coil affichées
 * sont résolues (Success OU Error — une URI morte ne bloque pas l'export) :
 * la feuille n'autorise la capture PNG qu'à ce moment-là.
 */
@Composable
fun PersonsSharePreview(
    persons: List<Person>,
    onImagesReadyChange: (Boolean) -> Unit = {}
) {
    val shown = persons.take(6)
    val resolvedIds = remember(shown) { mutableStateMapOf<String, Boolean>() }
    val expected = remember(shown) { shown.count { it.photoUri != null } }
    LaunchedEffect(resolvedIds.size, expected) {
        onImagesReadyChange(resolvedIds.size >= expected)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shown.forEach { person ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(
                        person = person,
                        size = 36.dp,
                        onImageState = { state ->
                            when (state) {
                                is AsyncImagePainter.State.Success,
                                is AsyncImagePainter.State.Error -> resolvedIds[person.id] = true
                                else -> {}
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(person.fullName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subtitle = listOfNotNull(person.city, person.origin)
                            .joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (persons.size > 6) {
                Text(
                    text = stringResource(R.string.share_more_count, persons.size - 6),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Aperçu capturable des catégories/dossiers sélectionnés. */
@Composable
fun CategoriesSharePreview(items: List<ShareCategoryItem>) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(6).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ShareUtils.itemSubtitle(context, item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (items.size > 6) {
                Text(
                    text = stringResource(R.string.share_more_count, items.size - 6),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
