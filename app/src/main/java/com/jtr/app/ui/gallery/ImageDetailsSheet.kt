package com.jtr.app.ui.gallery

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.GalleryImage
import com.jtr.app.ui.components.WheelPicker
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Plage d'années proposée autour de l'année en cours — assez large pour une vieille photo. */
private const val YEAR_SPAN_BACK = 80
private const val YEAR_SPAN_FORWARD = 5

/** Quelle ligne est en cours d'édition — une seule à la fois, le reste reste au repos. */
private enum class EditTarget { NONE, DATE_TIME, DESCRIPTION }

/**
 * Panneau de DÉTAILS d'une image de galerie (v7.1.94) — ouvert par le bouton ⓘ de la
 * visionneuse, PAR-DESSUS elle : le pager reste monté derrière, donc fermer le panneau rend
 * la visionneuse intacte, sur la même image.
 *
 * REFONTE ÉPURÉE (façon Samsung « Détails ») : le panneau AFFICHE les données, une par
 * ligne discrète « icône + valeur », et n'ouvre un éditeur QU'AU TAP sur la ligne concernée.
 * Plus de gros champ de saisie ni d'éditeur date/heure ouverts en permanence — au repos, on
 * lit ; on n'édite que ce qu'on touche. Une seule ligne éditable à la fois.
 *
 * Les deux colonnes écrites existent depuis la v23 ([GalleryImage.caption],
 * [GalleryImage.createdAt]) : ce lot n'a AUCUNE migration. La ligne LIEU viendra s'ajouter
 * ici en v7.1.95, sans toucher à cette structure.
 *
 * SAUVEGARDE de la description : à la fermeture de son éditeur ET à la fermeture du panneau,
 * et seulement si le texte a changé — une écriture par édition, jamais une par frappe, et
 * aucun écrasement si l'utilisateur n'a fait qu'ouvrir pour lire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageDetailsSheet(
    image: GalleryImage,
    onCaptionChange: (String?) -> Unit,
    onCreatedAtChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetStateCompat()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var editing by remember { mutableStateOf(EditTarget.NONE) }
    // Brouillon local : l'édition reste fluide même si la ligne est rediffusée par le Flow.
    var draft by remember(image.id) { mutableStateOf(image.caption.orEmpty()) }
    val focusRequester = remember { FocusRequester() }

    /** Enregistre la description SI elle a changé — jamais d'écriture inutile. */
    fun commitCaption() {
        val cleaned = draft.trim()
        if (cleaned != image.caption.orEmpty().trim()) onCaptionChange(cleaned)
    }

    fun commitAndDismiss() {
        commitCaption()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // Une seule valeur lisible d'un coup d'œil : « 21 octobre 2026 · 16:58 ». La date passe
    // par le format LONG de la locale (mois en toutes lettres), l'heure par celui de
    // l'APPAREIL (donc 12 h ou 24 h selon le réglage de l'utilisateur).
    val whenText = remember(image.createdAt, context) {
        val date = java.text.DateFormat
            .getDateInstance(java.text.DateFormat.LONG, Locale.getDefault())
            .format(Date(image.createdAt))
        val time = DateFormat.getTimeFormat(context).format(Date(image.createdAt))
        "$date · $time"
    }

    ModalBottomSheet(
        onDismissRequest = { commitAndDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.gallery_details_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )

            // ── DATE & HEURE ────────────────────────────────────────────────────────
            DetailRow(
                icon = Icons.Outlined.CalendarMonth,
                label = stringResource(R.string.gallery_details_date),
                value = whenText,
                isPlaceholder = false,
                onClick = {
                    editing = if (editing == EditTarget.DATE_TIME) EditTarget.NONE
                    else EditTarget.DATE_TIME
                }
            )
            AnimatedVisibility(visible = editing == EditTarget.DATE_TIME) {
                DateTimeEditor(
                    millis = image.createdAt,
                    onChange = onCreatedAtChange,
                    onDone = { editing = EditTarget.NONE }
                )
            }

            // ── DESCRIPTION ─────────────────────────────────────────────────────────
            val caption = image.caption.orEmpty()
            DetailRow(
                icon = Icons.AutoMirrored.Outlined.Notes,
                label = stringResource(R.string.gallery_details_description),
                value = caption.ifBlank { stringResource(R.string.gallery_details_description_hint) },
                isPlaceholder = caption.isBlank(),
                onClick = {
                    if (editing == EditTarget.DESCRIPTION) {
                        commitCaption()
                        editing = EditTarget.NONE
                    } else {
                        draft = caption
                        editing = EditTarget.DESCRIPTION
                    }
                }
            )
            AnimatedVisibility(visible = editing == EditTarget.DESCRIPTION) {
                Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = {
                            Text(stringResource(R.string.gallery_details_description_hint))
                        },
                        // Compact : 2 lignes au repos, borné — ce n'est plus le gros bloc
                        // permanent de la 1re version.
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .focusRequester(focusRequester)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            commitCaption()
                            editing = EditTarget.NONE
                        }) { Text(stringResource(R.string.common_done)) }
                    }
                }
                // Le clavier arrive sur le champ sans tap supplémentaire.
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        }
    }

    // Le brouillon suit la ligne si elle change sous nos pieds (p. ex. après l'écriture).
    LaunchedEffect(image.caption) {
        if (editing != EditTarget.DESCRIPTION) draft = image.caption.orEmpty()
    }
}

/**
 * Une ligne du panneau : petite icône discrète + valeur, cliquable sur toute sa largeur.
 * Hauteur minimale 56 dp → cible tactile largement au-dessus des 48 dp requis. La valeur
 * ABSENTE (p. ex. « Ajouter une description ») s'affiche en couleur atténuée, pour qu'un
 * champ vide se distingue d'un champ rempli sans ajouter de libellé mort.
 */
@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // Un seul nœud d'accessibilité porte le libellé ET la valeur : « Date, 21
            // octobre 2026 · 16:58 ». L'icône, décorative, reste muette.
            .semantics { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Éditeur date/heure révélé au tap sur la ligne : un segment « Date | Heure » puis les
 * MOLETTES (3 roues mois/jour/année, ou 2 roues heure/minute). Chaque arrêt de roue écrit
 * immédiatement — ce qu'on voit au centre EST la valeur, comme une molette d'alarme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeEditor(millis: Long, onChange: (Long) -> Unit, onDone: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(stringResource(R.string.gallery_details_date)) }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(stringResource(R.string.gallery_details_time)) }
        }
        if (tab == 0) DateWheels(millis = millis, onChange = onChange)
        else TimeWheels(millis = millis, onChange = onChange)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(stringResource(R.string.common_done)) }
        }
    }
}

/** Alias local : isole l'unique appel expérimental de Material3 pour ce fichier. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberModalBottomSheetStateCompat() =
    androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

/**
 * Trois molettes MOIS / JOUR / ANNÉE.
 *
 * ⚠️ Le nombre de jours s'ajuste au mois ET à l'année choisis (février bissextile, avril 30).
 * Si le jour courant dépasse la nouvelle longueur de mois, il est RABOTÉ au dernier jour
 * valide : passer du 31 mars à février donne le 28 (ou 29), jamais une date invalide.
 */
@Composable
private fun DateWheels(millis: Long, onChange: (Long) -> Unit) {
    val calendar = remember(millis) { Calendar.getInstance().apply { timeInMillis = millis } }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val monthNames = remember(Locale.getDefault()) {
        DateFormatSymbols.getInstance(Locale.getDefault()).months.filter { it.isNotBlank() }
    }
    val years = remember(Locale.getDefault()) {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        ((now - YEAR_SPAN_BACK)..(now + YEAR_SPAN_FORWARD)).toList()
    }
    val daysInMonth = remember(year, month) { daysIn(year, month) }
    val dayLabels = remember(daysInMonth, Locale.getDefault()) {
        (1..daysInMonth).map { String.format(Locale.getDefault(), "%d", it) }
    }
    val yearLabels = remember(years, Locale.getDefault()) {
        years.map { String.format(Locale.getDefault(), "%d", it) }
    }

    /** Recompose la date à partir des trois roues, en gardant l'heure d'origine. */
    fun apply(newYear: Int, newMonth: Int, newDay: Int) {
        val clamped = newDay.coerceAtMost(daysIn(newYear, newMonth))
        onChange(
            Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.YEAR, newYear)
                set(Calendar.MONTH, newMonth)
                set(Calendar.DAY_OF_MONTH, clamped)
            }.timeInMillis
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val monthLabel = stringResource(R.string.gallery_details_month)
        val dayLabel = stringResource(R.string.gallery_details_day)
        val yearLabel = stringResource(R.string.gallery_details_year)
        WheelPicker(
            items = monthNames,
            initialIndex = month,
            onIndexSettled = { apply(year, it, day) },
            modifier = Modifier.weight(1.4f).semantics { contentDescription = monthLabel }
        )
        WheelPicker(
            items = dayLabels,
            initialIndex = (day - 1).coerceIn(0, dayLabels.lastIndex),
            onIndexSettled = { apply(year, month, it + 1) },
            modifier = Modifier.weight(1f).semantics { contentDescription = dayLabel }
        )
        WheelPicker(
            items = yearLabels,
            initialIndex = years.indexOf(year).coerceAtLeast(0),
            onIndexSettled = { apply(years[it], month, day) },
            modifier = Modifier.weight(1f).semantics { contentDescription = yearLabel }
        )
    }
}

/** Deux molettes HEURE (00-23) / MINUTE (00-59) ; secondes remises à zéro à chaque arrêt. */
@Composable
private fun TimeWheels(millis: Long, onChange: (Long) -> Unit) {
    val calendar = remember(millis) { Calendar.getInstance().apply { timeInMillis = millis } }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    val hours = remember(Locale.getDefault()) {
        (0..23).map { String.format(Locale.getDefault(), "%02d", it) }
    }
    val minutes = remember(Locale.getDefault()) {
        (0..59).map { String.format(Locale.getDefault(), "%02d", it) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val hourLabel = stringResource(R.string.gallery_details_hour)
        val minuteLabel = stringResource(R.string.gallery_details_minute)
        WheelPicker(
            items = hours,
            initialIndex = hour,
            onIndexSettled = { onChange(mergeTimeKeepingDate(it, minute, millis)) },
            modifier = Modifier.weight(1f).semantics { contentDescription = hourLabel }
        )
        WheelPicker(
            items = minutes,
            initialIndex = minute,
            onIndexSettled = { onChange(mergeTimeKeepingDate(hour, it, millis)) },
            modifier = Modifier.weight(1f).semantics { contentDescription = minuteLabel }
        )
    }
}

/** Nombre de jours du mois donné (gère les années bissextiles). */
private fun daysIn(year: Int, month: Int): Int =
    Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

/**
 * Remplace l'HEURE en gardant la date d'origine ; secondes et millisecondes remises à zéro.
 *
 * ⚠️ Tout passe par un calendrier LOCAL, jamais par un instant UTC — c'est ce qui garantit
 * qu'éditer l'heure ne fait pas glisser le JOUR à l'ouest de Greenwich. Les molettes rendent
 * des champs de calendrier et non un instant, donc le piège « minuit UTC » de l'ancien
 * `DatePicker` ne peut plus se produire.
 */
internal fun mergeTimeKeepingDate(hour: Int, minute: Int, currentMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = currentMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
