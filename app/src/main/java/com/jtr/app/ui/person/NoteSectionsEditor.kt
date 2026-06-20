package com.jtr.app.ui.person

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.NOTE_ICON_NOTES
import com.jtr.app.domain.model.NoteSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
internal data class DeletedNoteSection(val section: NoteSection, val index: Int)

/**
 * État hissé du mode réordonnancement des sections (v7.0.7) — permet de RENDRE le footer
 * (« Glisser… » / Supprimer / Terminé) et le bandeau undo au niveau de l'ÉCRAN (bottom bar)
 * plutôt qu'en Popup, pour un calage FIABLE au ras de la barre système (inset appliqué une
 * seule fois). Partagé entre [NoteSectionsEditor] (qui pilote l'état au fil de l'interaction)
 * et [NoteReorderFooter] (qui le rend, ancré en bas de l'écran). L'INTERACTION est inchangée.
 */
@Stable
class NoteReorderState {
    /** Section « active » (saisie/glissée) ; le footer est visible tant qu'elle est non nulle. */
    internal var grabbedId by mutableStateOf<String?>(null)

    /** Section supprimée en attente d'annulation (undo). */
    internal var pendingUndo by mutableStateOf<DeletedNoteSection?>(null)

    /** Réinitialise le mode (ex. sortie du mode édition d'une fiche). */
    fun reset() {
        grabbedId = null
        pendingUndo = null
    }
}

/** Crée et mémorise l'état de réordonnancement à hisser dans l'écran hôte. */
@Composable
fun rememberNoteReorderState(): NoteReorderState = remember { NoteReorderState() }

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
    reorderState: NoteReorderState,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var iconPickerForId by remember { mutableStateOf<String?>(null) }

    fun reindex(list: List<NoteSection>) = list.mapIndexed { i, s -> s.copy(order = i) }

    // Si la section active disparaît (suppression, rechargement), on quitte le mode footer.
    // Le footer/undo sont désormais rendus au niveau ÉCRAN (cf. [NoteReorderFooter]).
    LaunchedEffect(sections) {
        if (reorderState.grabbedId != null && sections.none { it.id == reorderState.grabbedId }) {
            reorderState.grabbedId = null
        }
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
                        reorderState.grabbedId = section.id
                        reorderState.pendingUndo = null
                        focusManager.clearFocus()
                    },
                    onFieldFocused = {
                        // Éditer un champ masque le footer/undo (rendus au niveau écran).
                        reorderState.grabbedId = null
                        reorderState.pendingUndo = null
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

}

/**
 * Footer du mode réordonnancement (« Glisser… » / Supprimer / Terminé) + bandeau undo,
 * RENDUS AU NIVEAU ÉCRAN (v7.0.7) — à placer dans un conteneur ancré en bas (ex.
 * `Box(Modifier.fillMaxSize())` + `Modifier.align(Alignment.BottomCenter)`). L'inset de
 * barre de navigation est appliqué UNE SEULE FOIS ici → calage FIABLE au ras des touches
 * système (plus de `Popup`, plus de bande résiduelle). Se masque de lui-même hors mode
 * réordonnancement / hors undo. [state] est partagé avec [NoteSectionsEditor] ;
 * [sections]/[onSectionsChange] = la même liste hissée par l'écran (suppression / undo).
 * L'interaction est identique — seuls le LIEU et la MÉTHODE de rendu changent.
 */
@Composable
fun NoteReorderFooter(
    state: NoteReorderState,
    sections: List<NoteSection>,
    onSectionsChange: (List<NoteSection>) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    fun reindex(list: List<NoteSection>) = list.mapIndexed { i, s -> s.copy(order = i) }

    // Retour système : referme le mode réordonnancement (la confirmation a son propre retour).
    BackHandler(enabled = state.grabbedId != null && pendingDeleteId == null) {
        state.grabbedId = null
    }

    when {
        // Footer contextuel.
        state.grabbedId != null -> Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
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
                    TextButton(onClick = { pendingDeleteId = state.grabbedId }) {
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
                    TextButton(onClick = { state.grabbedId = null }) {
                        Text(stringResource(R.string.common_done))
                    }
                }
            }
        }
        // Bandeau d'annulation après suppression.
        state.pendingUndo != null -> Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Snackbar(
                action = {
                    TextButton(onClick = {
                        state.pendingUndo?.let { u ->
                            val restored = sections.toMutableList()
                            restored.add(u.index.coerceIn(0, restored.size), u.section)
                            onSectionsChange(reindex(restored))
                        }
                        state.pendingUndo = null
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
                        state.pendingUndo = DeletedNoteSection(sections[idx], idx)
                        onSectionsChange(reindex(sections.filter { it.id != id }))
                    }
                    pendingDeleteId = null
                    if (state.grabbedId == id) state.grabbedId = null
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

    // Auto-disparition du bandeau après quelques secondes.
    LaunchedEffect(state.pendingUndo) {
        if (state.pendingUndo != null) {
            delay(4000)
            state.pendingUndo = null
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
                        .bringIntoViewOnFocus()
                )
            }
            NoteContentField(
                value = section.content,
                onValueChange = onContentChange,
                onFocused = onFieldFocused,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Champ de CONTENU d'une note (v7.1.3) — habillage « carte souple » + suivi du curseur au ras du
 * clavier pendant la frappe.
 *
 * FOCUS : 100 % NATIF. Le champ remonte au-dessus du clavier via le suivi des « focused bounds »
 * du `verticalScroll` — sans aucune machinerie, donc **sans flash**. Le flash de v7.1.1 venait du
 * pilotage manuel image par image de l'inset IME (`snapshotFlow { imeBottom }`) couplé à un
 * `BringIntoViewSpec` instantané (`snap()`), qui « tiraient » la page à chaque frame de la montée
 * du clavier : ces deux éléments sont SUPPRIMÉS en v7.1.3.
 *
 * FRAPPE : le `TextField` value-based en `maxLines = Int.MAX_VALUE` GRANDIT mais ne propage pas le
 * suivi du curseur au défilement de la page (vérifié sur appareil). On amène donc explicitement la
 * **bande basse** du champ (= ligne du curseur quand on écrit en fin de note) dans la vue, via un
 * `bringIntoView` ANIMÉ (spec par défaut) déclenché sur une modification EN FIN de texte (détectée
 * par `startsWith`) et après chaque agrandissement. Une édition AU MILIEU ne déclenche aucun saut.
 *
 * FOCUS (v7.1.3 reprise) : le suivi de frappe ne se déclenchant qu'au CHANGEMENT de texte, ouvrir
 * le clavier sur une note (surtout VIDE) laissait le champ en haut avec un grand vide jusqu'au
 * clavier (résorbé seulement à la 1re frappe). On rejoue donc le MÊME `bringIntoView` de bande
 * basse À LA PRISE DE FOCUS, SYNCHRONISÉ avec la montée du clavier : `snapshotFlow { imeBottom }` +
 * `collectLatest` → à chaque palier de l'inset IME, le bringIntoView animé précédent est ANNULÉ et
 * un nouveau est relancé vers la bande basse dans le viewport courant. Spec ANIMÉ par défaut (jamais
 * `snap()`) + re-ciblage continu → le champ ACCOMPAGNE le clavier (pas de snap final = pas de flash ;
 * pas de suivi instantané = pas de jank) ; la DERNIÈRE valeur (clavier posé) laisse la position
 * exacte. Garde « curseur en fin » (via la sélection d'un [TextFieldValue] local) → un focus AU
 * MILIEU d'une note ne provoque AUCUNE remontée. Le viewport étant réduit d'EXACTEMENT la hauteur du
 * clavier (Scaffold.contentWindowInsets ∪ ime), la bande se cale juste au-dessus de lui.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteContentField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableIntStateOf(0) }
    // Vrai si la dernière modif portait sur la FIN du texte (écriture/ajout) → on suit alors.
    var lastEditAtEnd by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    // Valeur locale AVEC sélection (curseur), synchronisée sur la valeur hissée (chargement, undo…).
    // Sert UNIQUEMENT à la garde « curseur en fin » du recentrage au focus ; la frappe reste pilotée
    // par `startsWith` sur le texte (comportement inchangé).
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != tfv.text) tfv = TextFieldValue(value, TextRange(value.length))
    }

    fun cursorAtEnd() = tfv.selection.collapsed && tfv.selection.end >= tfv.text.length

    fun bringBottomIntoView() {
        if (heightPx <= 0) return
        scope.launch { bring.bringIntoView(Rect(0f, heightPx - 1f, 1f, heightPx.toFloat())) }
    }

    // Recentrage AU FOCUS, synchronisé avec la montée du clavier (cf. KDoc). collectLatest annule le
    // bringIntoView animé en cours à chaque nouvelle valeur d'inset → le champ « monte avec » le
    // clavier. Uniquement si le curseur est en fin (cas « on va écrire » / champ vide).
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(isFocused) {
        if (!isFocused) return@LaunchedEffect
        snapshotFlow { imeInsets.getBottom(density) }
            .distinctUntilChanged()
            .collectLatest { ime ->
                if (ime <= 0) return@collectLatest
                if (heightPx > 0 && cursorAtEnd()) {
                    bring.bringIntoView(Rect(0f, heightPx - 1f, 1f, heightPx.toFloat()))
                }
            }
    }

    TextField(
        value = tfv,
        onValueChange = { newValue ->
            // Modif en fin de texte = ajout au bout (new commence par old) ou suppression au bout
            // (old commence par new). Une insertion AU MILIEU casse les deux → pas de suivi.
            lastEditAtEnd = newValue.text.startsWith(tfv.text) || tfv.text.startsWith(newValue.text)
            val textChanged = newValue.text != tfv.text
            tfv = newValue
            if (textChanged) {
                onValueChange(newValue.text)
                if (lastEditAtEnd) bringBottomIntoView()
            }
        },
        placeholder = { Text(stringResource(R.string.note_section_content_hint)) },
        singleLine = false,
        minLines = 3,
        maxLines = Int.MAX_VALUE,
        shape = RoundedCornerShape(16.dp),
        colors = softFieldColors(),
        modifier = modifier
            // Champ qui GRANDIT (saut de ligne) ET dernière modif en fin → ré-amène le bas dans
            // la vue après la re-mesure → la nouvelle ligne reste au ras du clavier.
            .onSizeChanged { size ->
                val grew = size.height > heightPx
                heightPx = size.height
                if (grew && lastEditAtEnd) bringBottomIntoView()
            }
            .bringIntoViewRequester(bring)
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
    )
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
