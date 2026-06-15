package com.jtr.app.ui.person

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.ReminderPresets
import com.jtr.app.domain.model.ReminderUnit
import com.jtr.app.domain.model.decomposeReminderOffset

/**
 * Libellé localisé du délai de rappel courant (v7.0) : « Le jour J » pour `0`, le
 * libellé du préréglage exact s'il y en a un, sinon une valeur composée (« 3 j avant »).
 * Partagé par la ligne « Rappel » du formulaire et par la feuille de sélection.
 */
@Composable
fun reminderOffsetLabel(minutes: Int): String = when (minutes) {
    ReminderPresets.ON_DAY -> stringResource(R.string.reminder_preset_on_day)
    ReminderPresets.MIN_10 -> stringResource(R.string.reminder_preset_10min)
    ReminderPresets.HOUR_1 -> stringResource(R.string.reminder_preset_1hour)
    ReminderPresets.DAY_1 -> stringResource(R.string.reminder_preset_1day)
    ReminderPresets.WEEK_1 -> stringResource(R.string.reminder_preset_1week)
    else -> {
        val (count, unit) = decomposeReminderOffset(minutes)
        stringResource(reminderUnitValueRes(unit), count)
    }
}

private fun reminderUnitValueRes(unit: ReminderUnit): Int = when (unit) {
    ReminderUnit.MINUTES -> R.string.reminder_value_minutes
    ReminderUnit.HOURS -> R.string.reminder_value_hours
    ReminderUnit.DAYS -> R.string.reminder_value_days
    ReminderUnit.WEEKS -> R.string.reminder_value_weeks
}

private fun reminderUnitLabelRes(unit: ReminderUnit): Int = when (unit) {
    ReminderUnit.MINUTES -> R.string.reminder_unit_minutes
    ReminderUnit.HOURS -> R.string.reminder_unit_hours
    ReminderUnit.DAYS -> R.string.reminder_unit_days
    ReminderUnit.WEEKS -> R.string.reminder_unit_weeks
}

/** Préréglages affichés (libellé + valeur en minutes). */
private val presetRows = listOf(
    R.string.reminder_preset_on_day to ReminderPresets.ON_DAY,
    R.string.reminder_preset_10min to ReminderPresets.MIN_10,
    R.string.reminder_preset_1hour to ReminderPresets.HOUR_1,
    R.string.reminder_preset_1day to ReminderPresets.DAY_1,
    R.string.reminder_preset_1week to ReminderPresets.WEEK_1
)

/**
 * Ligne « Rappel » sous une date notifiable : icône + libellé + choix courant, cliquable
 * pour ouvrir la feuille de sélection [ReminderOffsetSheet].
 */
@Composable
fun ReminderRow(offsetMinutes: Int, onOffsetChange: (Int) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Schedule, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.reminder_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            reminderOffsetLabel(offsetMinutes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
    if (showSheet) {
        ReminderOffsetSheet(
            currentOffsetMinutes = offsetMinutes,
            onSelect = onOffsetChange,
            onDismiss = { showSheet = false }
        )
    }
}

/**
 * Feuille montante de sélection du délai de rappel (v7.0), au style des feuilles
 * « Tri / Affichage » : préréglages (un seul sélectionné, coche) + « Personnalisé »
 * qui déplie un pas-à-pas compact (nombre + unité). Tokens de thème uniquement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderOffsetSheet(
    currentOffsetMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val matchesPreset = ReminderPresets.VALUES.contains(currentOffsetMinutes)
    var customMode by remember { mutableStateOf(!matchesPreset) }

    // État du pas-à-pas personnalisé : décompose la valeur courante, ou (1, JOURS) si la
    // sélection courante est un préréglage (point de départ raisonnable).
    val initial = remember {
        if (matchesPreset) 1 to ReminderUnit.DAYS else decomposeReminderOffset(currentOffsetMinutes)
    }
    var count by remember { mutableIntStateOf(initial.first.coerceAtLeast(1)) }
    var unit by remember { mutableStateOf(initial.second) }

    fun applyCustom() {
        customMode = true
        onSelect((count * unit.minutes).coerceAtLeast(1))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.reminder_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        presetRows.forEach { (labelRes, value) ->
            SheetRow(
                label = stringResource(labelRes),
                selected = !customMode && currentOffsetMinutes == value,
                onClick = { onSelect(value); onDismiss() }
            )
        }

        SheetRow(
            label = stringResource(R.string.reminder_preset_custom),
            selected = customMode,
            onClick = { applyCustom() }
        )

        AnimatedVisibility(visible = customMode) {
            CustomStepper(
                count = count,
                unit = unit,
                onCountChange = { count = it; onSelect((count * unit.minutes).coerceAtLeast(1)) },
                onUnitChange = { unit = it; onSelect((count * unit.minutes).coerceAtLeast(1)) }
            )
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

/** Ligne d'option de la feuille (libellé + coche), au style des feuilles Tri/Affichage. */
@Composable
private fun SheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Pas-à-pas compact : [−] nombre [+] + sélecteur d'unité (chips). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomStepper(
    count: Int,
    unit: ReminderUnit,
    onCountChange: (Int) -> Unit,
    onUnitChange: (ReminderUnit) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onCountChange((count - 1).coerceAtLeast(1)) },
                enabled = count > 1
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.reminder_decrease))
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp)
            )
            IconButton(
                onClick = { onCountChange((count + 1).coerceAtMost(999)) },
                enabled = count < 999
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.reminder_increase))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                // Aperçu vivant du choix (« 3 j avant »).
                text = reminderOffsetLabel(count * unit.minutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReminderUnit.entries) { u ->
                FilterChip(
                    selected = unit == u,
                    onClick = { onUnitChange(u) },
                    label = { Text(stringResource(reminderUnitLabelRes(u))) }
                )
            }
        }
    }
}
