package com.jtr.app.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jtr.app.R
import com.jtr.app.domain.model.NOTE_ICON_NOTES
import com.jtr.app.domain.model.NoteSection

/**
 * Jeu d'icônes CURÉ (outline Material) des sections de notes (v7.0.3). Chaque entrée lie
 * une CLÉ TEXTE stable (persistée dans [NoteSection.iconKey]) à un [ImageVector] — robuste
 * aux versions (jamais d'id de ressource stocké).
 */
object NoteIcons {
    val curated: List<Pair<String, ImageVector>> = listOf(
        NOTE_ICON_NOTES to Icons.AutoMirrored.Outlined.Notes,
        "heart" to Icons.Outlined.FavoriteBorder,
        "star" to Icons.Outlined.StarOutline,
        "gift" to Icons.Outlined.CardGiftcard,
        "work" to Icons.Outlined.WorkOutline,
        "school" to Icons.Outlined.School,
        "home" to Icons.Outlined.Home,
        "place" to Icons.Outlined.Place,
        "event" to Icons.Outlined.Event,
        "music" to Icons.Outlined.MusicNote,
        "book" to Icons.AutoMirrored.Outlined.MenuBook,
        "food" to Icons.Outlined.Restaurant,
        "sport" to Icons.Outlined.FitnessCenter,
        "pets" to Icons.Outlined.Pets,
        "travel" to Icons.Outlined.Flight,
        "idea" to Icons.Outlined.Lightbulb
    )

    private val byKey = curated.toMap()

    /** Icône d'une clé ; repli sur « notes » si la clé est inconnue (robustesse). */
    fun icon(key: String): ImageVector = byKey[key] ?: Icons.AutoMirrored.Outlined.Notes
}

/**
 * Éditeur des sections de notes personnalisables (v7.0.3) — remplace les deux champs
 * fixes « Misc notes » / « What they like ». Liste verticale de cartes souples : chaque
 * section a une icône (sélecteur), un titre éditable et un contenu multiligne. Ajout,
 * renommage, réordonnancement par glisser (poignée, après appui long) et suppression
 * avec confirmation. Tokens de thème uniquement ; glisser vertical → compatible RTL.
 */
@Composable
fun NoteSectionsEditor(
    sections: List<NoteSection>,
    onSectionsChange: (List<NoteSection>) -> Unit,
    modifier: Modifier = Modifier
) {
    // rememberUpdatedState : la lambda de glisser (capturée par pointerInput, non relancée
    // à chaque réordonnancement) lit TOUJOURS la liste/à-jour la plus récente (anti-périmé).
    val latestSections by rememberUpdatedState(sections)
    val latestOnChange by rememberUpdatedState(onSectionsChange)

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var iconPickerForId by remember { mutableStateOf<String?>(null) }

    fun reindex(list: List<NoteSection>) = list.mapIndexed { i, s -> s.copy(order = i) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        sections.forEach { section ->
            val isDragging = draggingId == section.id
            NoteSectionCard(
                section = section,
                isDragging = isDragging,
                dragOffsetY = if (isDragging) dragOffsetY else 0f,
                onHeightMeasured = { heights[section.id] = it },
                dragHandleModifier = Modifier.pointerInput(section.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingId = section.id; dragOffsetY = 0f },
                        onDragEnd = { draggingId = null; dragOffsetY = 0f },
                        onDragCancel = { draggingId = null; dragOffsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            val list = latestSections
                            val cur = list.indexOfFirst { it.id == draggingId }
                            if (cur < 0) return@detectDragGesturesAfterLongPress
                            if (dragOffsetY > 0 && cur < list.lastIndex) {
                                val nextH = heights[list[cur + 1].id] ?: 0
                                if (nextH > 0 && dragOffsetY > nextH / 2f) {
                                    latestOnChange(reindex(list.moved(cur, cur + 1)))
                                    dragOffsetY -= nextH
                                }
                            } else if (dragOffsetY < 0 && cur > 0) {
                                val prevH = heights[list[cur - 1].id] ?: 0
                                if (prevH > 0 && -dragOffsetY > prevH / 2f) {
                                    latestOnChange(reindex(list.moved(cur, cur - 1)))
                                    dragOffsetY += prevH
                                }
                            }
                        }
                    )
                },
                onIconClick = { iconPickerForId = section.id },
                onTitleChange = { newTitle ->
                    onSectionsChange(sections.map {
                        if (it.id == section.id) it.copy(title = newTitle) else it
                    })
                },
                onContentChange = { newContent ->
                    onSectionsChange(sections.map {
                        if (it.id == section.id) it.copy(content = newContent) else it
                    })
                },
                onDeleteClick = { pendingDeleteId = section.id }
            )
        }

        // Affordance d'ajout discrète « + Ajouter une section ».
        TextButton(
            onClick = {
                onSectionsChange(
                    sections + NoteSection(iconKey = NOTE_ICON_NOTES, order = sections.size)
                )
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.note_section_add),
                style = MaterialTheme.typography.labelLarge)
        }
    }

    // Sélecteur d'icône (grille du jeu curé).
    iconPickerForId?.let { id ->
        val current = sections.firstOrNull { it.id == id }?.iconKey ?: NOTE_ICON_NOTES
        IconPickerDialog(
            currentKey = current,
            onPick = { key ->
                onSectionsChange(sections.map { if (it.id == id) it.copy(iconKey = key) else it })
                iconPickerForId = null
            },
            onDismiss = { iconPickerForId = null }
        )
    }

    // Confirmation de suppression (anti-perte accidentelle).
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            icon = { Icon(Icons.Default.DeleteOutline, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.note_section_delete_confirm_title)) },
            text = { Text(stringResource(R.string.note_section_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onSectionsChange(reindex(sections.filter { it.id != id }))
                    pendingDeleteId = null
                }) {
                    Text(stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** Déplace l'élément [from] vers [to] dans une nouvelle liste (immuable). */
private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Carte d'une section : poignée + icône + titre + suppression, puis contenu multiligne. */
@Composable
private fun NoteSectionCard(
    section: NoteSection,
    isDragging: Boolean,
    dragOffsetY: Float,
    onHeightMeasured: (Int) -> Unit,
    dragHandleModifier: Modifier,
    onIconClick: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                shadowElevation = if (isDragging) 8.dp.toPx() else 0f
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Poignée de réordonnancement (appui long puis glisser ; vertical → RTL OK).
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.note_section_reorder_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.size(24.dp)
                )
                Spacer(Modifier.width(4.dp))
                // Icône de la section → ouvre le sélecteur.
                IconButton(onClick = onIconClick) {
                    Icon(NoteIcons.icon(section.iconKey),
                        contentDescription = stringResource(R.string.note_section_icon_picker_title),
                        tint = MaterialTheme.colorScheme.primary)
                }
                SoftTextField(
                    value = section.title,
                    onValueChange = onTitleChange,
                    placeholder = stringResource(R.string.note_section_title_hint),
                    leadingIcon = null,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.note_section_delete),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            SoftTextField(
                value = section.content,
                onValueChange = onContentChange,
                placeholder = stringResource(R.string.note_section_content_hint),
                leadingIcon = null,
                singleLine = false,
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Grille de sélection d'icône depuis le jeu curé [NoteIcons]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPickerDialog(
    currentKey: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_section_icon_picker_title)) },
        text = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteIcons.curated.forEach { (key, icon) ->
                    val selected = key == currentKey
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onPick(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon, contentDescription = key,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        }
    )
}

/** Sections « propres » avant persistance : retire les sections vides + réindexe l'ordre. */
fun sanitizeNoteSections(sections: List<NoteSection>): List<NoteSection> =
    sections.filter { it.title.isNotBlank() || it.content.isNotBlank() }
        .mapIndexed { i, s -> s.copy(order = i) }
