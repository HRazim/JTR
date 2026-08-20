package com.jtr.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Sélecteur de photo unique s'appuyant sur le **Photo Picker Android**
 * ([ActivityResultContracts.PickVisualMedia]).
 *
 * v7.1.66 — REMPLACE la galerie in-app maison (v5.5) qui lisait directement le
 * MediaStore. Google Play refuse `READ_MEDIA_IMAGES` lorsque le sélecteur système
 * couvre le cas d'usage : l'ancienne galerie reconstruisait un écran que l'OS
 * fournit déjà, au prix d'une permission d'accès à TOUTE la photothèque.
 * Le Photo Picker n'exige **aucune permission** — l'utilisateur choisit une image
 * et l'app ne reçoit qu'un accès en lecture à celle-là.
 *
 * Le contrat AndroidX gère seul le repli selon la version d'Android :
 * sélecteur natif (Android 13+, ou 11-12 via les extensions du SDK), puis
 * sélecteur rétro-porté des services Google Play, puis `ACTION_OPEN_DOCUMENT`.
 * Aucune dépendance supplémentaire n'est requise pour minSdk 26.
 *
 * L'URI retournée alimente le workflow de recadrage existant (ImageCropDialog)
 * exactement comme avant — la signature publique est celle de l'ancien helper,
 * donc les points d'appel n'ont pas changé de forme.
 *
 * Le grant de lecture est éphémère, mais il suffit : l'appelant recadre puis
 * COPIE les octets dans `filesDir` immédiatement. Aucune URI du MediaStore n'est
 * conservée en base ⇒ pas de `takePersistableUriPermission` à demander.
 *
 * @return une lambda à invoquer pour ouvrir le sélecteur système.
 */
@Composable
fun rememberSystemPhotoPicker(onImagePicked: (Uri) -> Unit): () -> Unit {
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // `null` = l'utilisateur est revenu sans choisir : on ne fait rien.
        if (uri != null) currentOnImagePicked(uri)
    }

    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}
