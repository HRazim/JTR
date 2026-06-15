package com.jtr.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.jtr.app.ui.components.FavoriteStar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.ui.components.JtrSelectionCheck
import com.jtr.app.ui.components.JtrViewMode

/**
 * Rendu PARTAGÉ d'une liste de contacts selon le mode d'affichage choisi
 * (Accueil, détail de catégorie, sélection de contacts) :
 *
 * - [JtrViewMode.LIST]   : ligne compacte — avatar + nom uniquement ;
 * - [JtrViewMode.GRID]   : grands carrés tactiles (photo plein fond) ;
 * - [JtrViewMode.DETAIL] : carte large « Notion » — anniversaire, ville,
 *   origine et réseaux sous le nom ([PersonCard]).
 */
@Composable
fun PersonListContent(
    viewMode: JtrViewMode,
    persons: List<Person>,
    socialLinksMap: Map<String, List<SocialLinkEntity>> = emptyMap(),
    selectedIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onClick: (Person) -> Unit,
    onLongClick: (Person) -> Unit = {},
    onFavoriteClick: (Person) -> Unit = {},
    /**
     * En-tête optionnel (hub des événements à venir…) rendu À L'INTÉRIEUR de la
     * liste/grille : il défile avec le contenu et, en mode Grille, s'étale sur
     * TOUTE la largeur via [GridItemSpan] (harmonie avec les tuiles carrées).
     */
    header: (@Composable () -> Unit)? = null
) {
    when (viewMode) {
        JtrViewMode.LIST -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            header?.let { item(key = "jtr_header") { it() } }
            items(items = persons, key = { it.id }) { person ->
                PersonCompactRow(
                    person = person,
                    isSelected = person.id in selectedIds,
                    isSelectionMode = isSelectionMode,
                    onClick = { onClick(person) },
                    onLongClick = { onLongClick(person) }
                )
            }
        }

        JtrViewMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pleine largeur : l'en-tête occupe la ligne entière de la grille.
            header?.let {
                item(key = "jtr_header", span = { GridItemSpan(maxLineSpan) }) { it() }
            }
            items(items = persons, key = { it.id }) { person ->
                PersonGridTile(
                    person = person,
                    isSelected = person.id in selectedIds,
                    isSelectionMode = isSelectionMode,
                    onClick = { onClick(person) },
                    onLongClick = { onLongClick(person) }
                )
            }
        }

        JtrViewMode.DETAIL -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            header?.let { item(key = "jtr_header") { it() } }
            items(items = persons, key = { it.id }) { person ->
                PersonCard(
                    person = person,
                    socialLinks = socialLinksMap[person.id] ?: emptyList(),
                    isSelected = person.id in selectedIds,
                    isSelectionMode = isSelectionMode,
                    onClick = { onClick(person) },
                    onLongClick = { onLongClick(person) },
                    onFavoriteClick = { onFavoriteClick(person) }
                )
            }
        }
    }
}

/** Mode LIST : ligne compacte — avatar + prénom/nom uniquement. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonCompactRow(
    person: Person,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
            PersonAvatar(person = person, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = person.fullName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Mode GRID : grand carré tactile — photo plein fond (ou initiales), nom superposé. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonGridTile(
    person: Person,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (person.photoUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(person.photoUri).crossfade(300).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = person.initials,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Dégradé sombre en bas pour la lisibilité du nom superposé.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        startY = 200f
                    )
                )
        )

        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)))
        }
        if (isSelectionMode) {
            // Disque protecteur contrastant : lisible sur toute photo (v5.5).
            JtrSelectionCheck(
                selected = isSelected,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            )
        }
        if (person.isFavorite) {
            FavoriteStar(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), size = 22.dp)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = person.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (person.city != null) {
                Text(
                    text = person.city,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Avatar circulaire réutilisable : photo, sinon initiales sur dégradé.
 * [onImageState] (optionnel) relaie l'état Coil — utilisé par l'aperçu de
 * partage pour attendre le chargement complet avant la capture PNG (v5.5).
 */
@Composable
internal fun PersonAvatar(
    person: Person,
    size: androidx.compose.ui.unit.Dp,
    onImageState: ((coil.compose.AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (person.photoUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(person.photoUri).crossfade(300).build(),
                onState = onImageState,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = person.initials,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
