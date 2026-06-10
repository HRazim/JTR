package com.jtr.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Sélecteur d'image « style Instagram » (v5.3.4) : ouvre la GALERIE NATIVE de
 * l'appareil via une intention explicite `ACTION_PICK` sur
 * [MediaStore.Images.Media.EXTERNAL_CONTENT_URI] — l'interface constructeur
 * (Galerie Samsung, Google Photos…) expose directement son tiroir d'ALBUMS et
 * de dossiers (Récents, Caméra, WhatsApp, Téléchargements…), contrairement au
 * Photo Picker système qui présente un flux plat fastidieux.
 *
 * Repli automatique sur le gestionnaire de documents (`ACTION_GET_CONTENT`,
 * type image, CATEGORY_OPENABLE) si aucune galerie ne gère l'intention.
 *
 * Sécurité : l'URI retournée porte une permission de lecture TEMPORAIRE —
 * aucune permission globale de stockage n'est demandée. L'aller-retour passe
 * par un Activity Result : l'écran appelant n'est pas recréé, l'état du
 * formulaire (ViewModel) est intégralement préservé (esprit UDF v5.3.3).
 *
 * @return une lambda à invoquer pour ouvrir la galerie.
 */
@Composable
fun rememberGalleryImagePicker(onImagePicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let(onImagePicked)
    }
    return remember(launcher) {
        {
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            try {
                launcher.launch(gallery)
            } catch (_: ActivityNotFoundException) {
                val documents = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                launcher.launch(documents)
            }
        }
    }
}
