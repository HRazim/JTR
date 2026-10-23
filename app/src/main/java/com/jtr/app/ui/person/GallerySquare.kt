package com.jtr.app.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jtr.app.R

/** Côté du carré « galerie » — partagé avec l'espaceur qui recentre l'avatar en face. */
internal val GALLERY_SQUARE_SIZE = 44.dp

/**
 * Carré d'entrée de la GALERIE (v7.1.89), posé à côté de l'avatar sur la fiche contact.
 *
 * Carré arrondi (et non rond) pour se distinguer nettement de l'avatar circulaire : c'est une
 * PORTE vers un autre écran, pas une seconde photo de profil.
 *
 * - **Aucune image** : icône « pile de photos » sur fond tonal, aucune pastille.
 * - **Au moins une** : [latestImagePath] (la plus récente) en vignette center-crop, et une
 *   pastille portant le NOMBRE exact.
 *
 * ⚠️ La pastille vit À L'INTÉRIEUR des bornes du carré, en retrait du coin. Première version :
 * elle débordait via un `offset` négatif et le `clip(RoundedCornerShape)` du carré la ROGNAIT —
 * le chiffre était coupé par l'arrondi. Fond plein + `onPrimary` pour rester lisible par-dessus
 * n'importe quelle vignette, claire comme sombre.
 *
 * a11y : un SEUL nœud cliquable annoncé « Galerie de <nom>, N photos », rôle bouton, cible d'au
 * moins 48 dp grâce au padding externe — la vignette n'introduit aucun nœud concurrent
 * (`contentDescription = null`), leçon du chip de v7.1.86.
 */
@Composable
internal fun GallerySquare(
    count: Int,
    latestImagePath: String?,
    personName: String,
    onClick: () -> Unit
) {
    val label = pluralStringResource(R.plurals.gallery_square_cd, count, personName, count)
    Box(
        modifier = Modifier
            // Porte la cible tactile à 48 dp+ sans grossir le carré visible.
            .padding(4.dp)
            .size(GALLERY_SQUARE_SIZE)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        if (latestImagePath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(latestImagePath).crossfade(200).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // En RETRAIT du coin : jamais au contact de l'arrondi, donc jamais rognée.
                    .padding(3.dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
