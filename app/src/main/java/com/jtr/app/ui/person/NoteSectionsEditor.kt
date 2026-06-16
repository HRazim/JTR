package com.jtr.app.ui.person

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jtr.app.R
import com.jtr.app.domain.model.NOTE_ICON_NOTES
import com.jtr.app.domain.model.NoteSection
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableScope

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

/** Section supprimée mémorisée pour l'annulation (undo) : contenu + position d'origine. */
private data class DeletedNoteSection(val section: NoteSection, val index: Int)

/**
 * Éditeur des sections de notes personnalisables — remplace les deux champs fixes
 * « Misc notes » / « What they like ». Liste verticale de cartes souples : chaque section
 * a une icône (sélecteur), un titre éditable et un contenu multiligne.
 *
 * Interaction (v7.0.4) — réordonnancement via la bibliothèque **sh.calvin.reorderable**
 * ([ReorderableColumn] + `longPressDraggableHandle`), drag-and-drop fiable (la carte suit
 * le doigt, les autres s'animent, dépose en douceur) :
 *  - **Appui long n'importe où** sur une carte → glisser pour réordonner, TANT QU'aucun
 *    champ de la section n'est en focus (`longPressDraggableHandle(enabled = !editing)`).
 *  - **Dès qu'un champ est focalisé** (on écrit) → la saisie est désactivée → sélection de
 *    texte native (Copier/Coller) ; le footer et l'undo disparaissent ; un tap sur le fond
 *    de la carte rend le focus (réactive le glisser).
 *  - **Tap icône** → sélecteur d'icône ; **tap titre** → édition.
 *  - **Footer** (en bas, au-dessus de la barre de navigation) : **Supprimer** (confirmation
 *    + undo) / **Terminé** ; sortie via Terminé, Retour système, ou édition d'un champ.
 *
 * Tokens de thème uniquement ; le glisser est purement vertical → compatible RTL.
 */
@Composable
fun NoteSectionsEditor(
    sections: List<NoteSection>,
    onSectionsChange: (List<NoteSection>) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // Section « active » (saisie/glissée) pour laquelle le footer s'affiche. Posée à
    // l'amorce du glisser, elle persiste après le dépose pour permettre Supprimer / Terminé.
    var grabbedId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var iconPickerForId by remember { mutableStateOf<String?>(null) }
    var pendingUndo by remember { mutableStateOf<DeletedNoteSection?>(null) }

    fun reindex(list: List<NoteSection>) = list.mapIndexed { i, s -> s.copy(order = i) }

    // Si la section active disparaît (suppression, rechargement), on quitte le mode footer.
    LaunchedEffect(sections) {
        if (grabbedId != null && sections.none { it.id == grabbedId }) grabbedId = null
    }

    // Retour système : referme le footer avant de quitter l'écran.
    BackHandler(enabled = grabbedId != null && pendingDeleteId == null && iconPickerForId == null) {
        grabbedId = null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReorderableColumn(
            list = sections,
            onSettle = { fromIndex, toIndex ->
                onSectionsChange(reindex(sections.moved(fromIndex, toIndex)))
            },
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { _, section, isDragging ->
            // Scope de réordonnancement (porteur du handle) capturé pour l'appliquer à
            // l'icône de la carte, et NON aux champs de texte → la frappe/sélection reste
            // 100 % native (aucun gating sur le focus, donc aucune perte de focus/clavier).
            val reorderScope = this
            key(section.id) {
                NoteSectionCard(
                    section = section,
                    isDragging = isDragging,
                    reorderScope = reorderScope,
                    onIconClick = { iconPickerForId = section.id },
                    onGrabStarted = {
                        grabbedId = section.id
                        pendingUndo = null
                        focusManager.clearFocus()
                    },
                    onFieldFocused = {
                        // Dès qu'on écrit dans un champ : footer/undo masqués (pas de
                        // superposition sur le texte ni la barre de sélection système).
                        grabbedId = null
                        pendingUndo = null
                    },
                    onTitleChange = { newTitle ->
                        onSectionsChange(sections.map {
                            if (it.id == section.id) it.copy(title = newTitle) else it
                        })
                    },
                    onContentChange = { newContent ->
                        onSectionsChange(sections.map {
                            if (it.id == section.id) it.copy(content = newContent) else it
                        })
                    }
                )
            }
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

    // Footer contextuel (ancré en bas de l'écran via Popup).
    if (grabbedId != null) {
        Popup(
            alignment = Alignment.BottomCenter,
            properties = PopupProperties(focusable = false)
        ) {
            // PAS de navigationBarsPadding ici : le Popup positionne DÉJÀ son contenu
            // au-dessus de la barre de boutons système ; l'ajouter compterait l'inset
            // deux fois (bande résiduelle ≈ hauteur de la barre). On ne garde qu'une
            // petite marge interne → la ligne Supprimer/Terminé est au ras des touches.
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.note_section_reorder_cd),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { pendingDeleteId = grabbedId }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.common_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { grabbedId = null }) {
                            Text(stringResource(R.string.common_done))
                        }
                    }
                }
            }
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
                    val idx = sections.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        // Mémorise contenu + position pour l'annulation, puis retire.
                        pendingUndo = DeletedNoteSection(sections[idx], idx)
                        onSectionsChange(reindex(sections.filter { it.id != id }))
                    }
                    pendingDeleteId = null
                    if (grabbedId == id) grabbedId = null
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

    // Bandeau d'annulation (undo) après suppression — restaure la section à sa place.
    if (pendingUndo != null && grabbedId == null) {
        Popup(
            alignment = Alignment.BottomCenter,
            properties = PopupProperties(focusable = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Snackbar(
                    action = {
                        TextButton(onClick = {
                            pendingUndo?.let { u ->
                                val restored = sections.toMutableList()
                                restored.add(u.index.coerceIn(0, restored.size), u.section)
                                onSectionsChange(reindex(restored))
                            }
                            pendingUndo = null
                        }) {
                            Text(
                                stringResource(R.string.common_undo),
                                color = MaterialTheme.colorScheme.inversePrimary
                            )
                        }
                    }
                ) { Text(stringResource(R.string.note_section_deleted)) }
            }
        }
    }

    // Auto-disparition du bandeau après quelques secondes.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo != null) {
            delay(4000)
            pendingUndo = null
        }
    }
}

/** Déplace l'élément [from] vers [to] dans une nouvelle liste (immuable). */
private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Carte d'une section : icône + titre éditable, puis contenu multiligne.
 *
 * Le handle de réordonnancement ([reorderScope] `longPressDraggableHandle`) est posé
 * UNIQUEMENT sur l'icône (le « grip » de la carte) : appui long sur l'icône → glisser
 * (la bibliothèque suit le pointeur sur toute la liste une fois la prise faite), tap sur
 * l'icône → sélecteur. Les `TextField` ne portent AUCUN geste de glisser ni gating de
 * focus → frappe clavier + sélection (Copier/Coller) 100 % natives, sans perte de focus.
 * [onFieldFocused] sert seulement à masquer le footer/undo pendant l'édition.
 */
@Composable
private fun NoteSectionCard(
    section: NoteSection,
    isDragging: Boolean,
    reorderScope: ReorderableScope,
    onIconClick: () -> Unit,
    onGrabStarted: () -> Unit,
    onFieldFocused: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "noteSectionScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "noteSectionElevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
                shape = RoundedCornerShape(16.dp)
                clip = false
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icône = grip de glisser (appui long) + sélecteur d'icône (tap).
                IconButton(
                    onClick = onIconClick,
                    modifier = with(reorderScope) {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = { onGrabStarted() }
                        )
                    }
                ) {
                    Icon(NoteIcons.icon(section.iconKey),
                        contentDescription = stringResource(R.string.note_section_icon_picker_title),
                        tint = MaterialTheme.colorScheme.primary)
                }
                SoftTextField(
                    value = section.title,
                    onValueChange = onTitleChange,
                    placeholder = stringResource(R.string.note_section_title_hint),
                    leadingIcon = null,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) onFieldFocused() }
                )
            }
            SoftTextField(
                value = section.content,
                onValueChange = onContentChange,
                placeholder = stringResource(R.string.note_section_content_hint),
                leadingIcon = null,
                singleLine = false,
                minLines = 3,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFieldFocused() }
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
