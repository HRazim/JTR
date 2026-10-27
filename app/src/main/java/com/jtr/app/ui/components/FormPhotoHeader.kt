package com.jtr.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.ui.person.CropShape
import com.jtr.app.ui.person.ImageCropDialog

/**
 * EN-TÊTE PHOTO de formulaire (v7.1.111, G3a) — le langage v7.1.79, extrait en UN SEUL
 * moteur (façon [WheelPicker]/[PhotoZoomDialog]) : il vivait inline dans le formulaire de
 * catégorie et était déjà DUPLIQUÉ dans celui de groupe ; le formulaire de contact aurait
 * été la troisième copie.
 *
 *  - **aperçu vivant 96 dp** : la photo si présente, sinon l'initiale de [fallbackInitialFrom]
 *    (premier code point — emoji/arabe entiers) ou, à défaut, [fallbackIcon], sur teinte
 *    [accent] α.16 cerclée d'un anneau α.55 — toutes les couleurs viennent du THÈME, jamais
 *    de blanc codé en dur (contraste garanti sur chaque palette) ;
 *  - **badge caméra 32 dp** en coin ([Icons.Filled.AddAPhoto] → [Icons.Filled.Edit] dès
 *    qu'une photo existe) + **bouton texte « Photo »** ;
 *  - **tap (aperçu, badge ou bouton)** : photo présente → [PhotoZoomDialog]
 *    (Remplacer / Retirer) ; absente → Photo Picker système directement ;
 *  - toute sélection passe par le **recadrage** ([ImageCropDialog], forme [cropShape] —
 *    CIRCLE pour un contact, RECTANGLE pour catégorie/dossier) avant [onPhotoCropped] ;
 *    la COPIE du fichier reste chez l'appelant (préfixes `profile_`/`category_` distincts).
 *
 * Le badge est INSCRIT dans le coin du disque (pas d'offset négatif : un `offset` négatif
 * serait rogné par le clip d'un parent, v7.1.89) et se miroite naturellement en RTL.
 */
@Composable
internal fun FormPhotoHeader(
    photo: String?,
    accent: Color,
    fallbackIcon: ImageVector,
    photoContentDescription: String,
    cropShape: CropShape,
    onPhotoCropped: (Uri) -> Unit,
    onPhotoRemoved: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackInitialFrom: String? = null
) {
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showZoom by remember { mutableStateOf(false) }
    // Photo Picker système (v7.1.66) — aucune permission requise. `pendingCropUri` est
    // réarmé à CHAQUE sélection (présence, pas égalité) : re-choisir la même image
    // rouvre bien le recadrage.
    val photoPicker = rememberSystemPhotoPicker { uri -> pendingCropUri = uri }
    // Routage UNIQUE des trois affordances : zoom (Remplacer/Retirer) si une photo
    // existe, sinon droit au sélecteur — rien à zoomer.
    val openPhoto = { if (photo != null) showZoom = true else photoPicker() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(2.dp, accent.copy(alpha = 0.55f), CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .clickable { openPhoto() },
                contentAlignment = Alignment.Center
            ) {
                val initial = photoHeaderInitial(fallbackInitialFrom)
                when {
                    photo != null -> AsyncImage(
                        model = photo,
                        contentDescription = photoContentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    initial != null -> Text(
                        initial,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    else -> Icon(
                        fallbackIcon, contentDescription = null,
                        modifier = Modifier.size(36.dp), tint = accent
                    )
                }
            }
            // Badge caméra en coin : l'ajout de photo est l'action CENTRALE de l'en-tête.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { openPhoto() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (photo != null) Icons.Default.Edit else Icons.Default.AddAPhoto,
                    contentDescription = stringResource(R.string.common_photo),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // Bouton photo EXPLICITE (en plus du tap sur l'aperçu) — même routage.
        TextButton(onClick = { openPhoto() }) {
            Text(stringResource(R.string.common_photo))
        }
    }

    // Visionneuse (Remplacer / Retirer) EMPILÉE au-dessus du formulaire hôte : ses états
    // `remember` appartiennent à l'appelant, le brouillon survit à sa fermeture (v7.1.49).
    if (showZoom) {
        photo?.let { current ->
            PhotoZoomDialog(
                photoUri = current,
                onDismiss = { showZoom = false },
                // ⚠️ On ferme la visionneuse AVANT d'ouvrir le sélecteur : sans cela le
                // recadrage s'empilerait en 3e dialogue au-dessus d'elle.
                onReplace = { showZoom = false; photoPicker() },
                onRemove = { showZoom = false; onPhotoRemoved() }
            )
        }
    }

    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropShape = cropShape,
            onCropComplete = { croppedUri ->
                onPhotoCropped(croppedUri)
                pendingCropUri = null
            },
            onDismiss = { pendingCropUri = null }
        )
    }
}

/**
 * Initiale du repli : PREMIER code point de [name] (une paire de substitution — emoji,
 * certains idéogrammes — reste entière), en capitale ; `null` si le nom est vide/blanc —
 * l'appelant retombe alors sur l'icône. Extraite (elle était dupliquée catégorie/groupe)
 * et pure pour le test JVM.
 */
internal fun photoHeaderInitial(name: String?): String? {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}
