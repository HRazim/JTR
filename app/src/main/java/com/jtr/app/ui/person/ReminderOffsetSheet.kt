package com.jtr.app.ui.person

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.ReminderPresets
import com.jtr.app.domain.model.ReminderUnit
import com.jtr.app.domain.model.decomposeReminderOffset
import kotlin.math.abs

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

/** Étape de navigation interne de la feuille : préréglages (Footer 1) ou molette (Footer 2). */
private enum class ReminderStep { PRESETS, CUSTOM }

/**
 * Feuille montante de sélection du délai de rappel (style des feuilles « Tri / Affichage »).
 *
 * v7.0.6 — navigation à DEUX FOOTERS dans le `ModalBottomSheet` (transition douce) :
 *  - **Footer 1** : préréglages (un seul sélectionné, coche) + « Personnalisé » ; un
 *    préréglage s'applique et ferme, « Personnalisé » ouvre le Footer 2.
 *  - **Footer 2** : double molette dédiée + barre d'actions (Retour → revient au Footer 1
 *    SANS valider ; OK → applique la valeur personnalisée et ferme).
 *
 * Rouvrir affiche TOUJOURS le Footer 1 ; le retour SYSTÈME ferme la feuille (par défaut du
 * ModalBottomSheet) depuis l'un ou l'autre footer. Les changements de molette ne sont
 * appliqués qu'à « OK » (stockage minutes-avant inchangé). Tokens de thème uniquement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderOffsetSheet(
    currentOffsetMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val matchesPreset = ReminderPresets.VALUES.contains(currentOffsetMinutes)
    // Rouvrir affiche TOUJOURS le Footer 1 (préréglages).
    var step by remember { mutableStateOf(ReminderStep.PRESETS) }

    // État LOCAL de la molette : valeur courante décomposée (bornée), ou (1, JOURS) si la
    // sélection est un préréglage. Appliqué uniquement à « OK » (Footer 2).
    val initial = remember {
        if (matchesPreset) 1 to ReminderUnit.DAYS else decomposeReminderOffset(currentOffsetMinutes)
    }
    var unit by remember { mutableStateOf(initial.second) }
    var count by remember { mutableIntStateOf(initial.first.coerceIn(1, initial.second.max)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "reminderStep"
        ) { current ->
            when (current) {
                ReminderStep.PRESETS -> PresetsFooter(
                    currentOffsetMinutes = currentOffsetMinutes,
                    customSelected = !matchesPreset,
                    onPreset = { onSelect(it); onDismiss() },
                    onCustom = { step = ReminderStep.CUSTOM }
                )
                ReminderStep.CUSTOM -> CustomFooter(
                    count = count,
                    unit = unit,
                    onCountChange = { count = it.coerceIn(1, unit.max) },
                    onUnitChange = { newUnit ->
                        unit = newUnit
                        // Borne la valeur au nouveau maximum (ex. 45 min → Heures → 24).
                        count = count.coerceIn(1, newUnit.max)
                    },
                    onBack = { step = ReminderStep.PRESETS },
                    onConfirm = {
                        onSelect((count * unit.minutes).coerceAtLeast(1))
                        onDismiss()
                    }
                )
            }
        }
    }
}

/** Footer 1 — préréglages + « Personnalisé » (un seul sélectionné, coche). */
@Composable
private fun PresetsFooter(
    currentOffsetMinutes: Int,
    customSelected: Boolean,
    onPreset: (Int) -> Unit,
    onCustom: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                selected = !customSelected && currentOffsetMinutes == value,
                onClick = { onPreset(value) }
            )
        }
        SheetRow(
            label = stringResource(R.string.reminder_preset_custom),
            selected = customSelected,
            onClick = onCustom
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** Footer 2 — molette personnalisée dédiée + barre d'actions (Retour ← / OK ✓). */
@Composable
private fun CustomFooter(
    count: Int,
    unit: ReminderUnit,
    onCountChange: (Int) -> Unit,
    onUnitChange: (ReminderUnit) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // Barre d'actions : Retour (revient au Footer 1) · titre · OK (valide + ferme).
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.reminder_preset_custom),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            TextButton(onClick = onConfirm) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.common_ok))
            }
        }
        CustomSelector(
            count = count,
            unit = unit,
            onCountChange = onCountChange,
            onUnitChange = onUnitChange
        )
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

/**
 * Sélecteur personnalisé v7.0.6 : une **double molette** (spinner) — roue NOMBRE à gauche
 * (1..[ReminderUnit.max]) et roue UNITÉ à droite (Minutes/Heures/Jours/Semaines), la valeur
 * CENTRÉE étant la sélection (accrochage `snap`, surbrillance centrale). Aperçu vivant du
 * choix au-dessus. Au changement d'unité, la roue nombre est ré-instanciée (`key(unit)`)
 * sur la nouvelle plage et la valeur est bornée au nouveau max.
 */
@Composable
private fun CustomSelector(
    count: Int,
    unit: ReminderUnit,
    onCountChange: (Int) -> Unit,
    onUnitChange: (ReminderUnit) -> Unit
) {
    val unitLabels = ReminderUnit.entries.map { stringResource(reminderUnitLabelRes(it)) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Aperçu vivant du choix (« 3 jours avant »), centré au-dessus des molettes.
        Text(
            text = reminderOffsetLabel(count * unit.minutes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Roue NOMBRE — ré-instanciée à chaque changement d'unité (nouvelle plage +
            // valeur bornée au nouveau max).
            key(unit) {
                WheelPicker(
                    items = (1..unit.max).map { it.toString() },
                    initialIndex = (count - 1).coerceIn(0, unit.max - 1),
                    onIndexSettled = { onCountChange(it + 1) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Roue UNITÉ.
            WheelPicker(
                items = unitLabels,
                initialIndex = unit.ordinal,
                onIndexSettled = { onUnitChange(ReminderUnit.entries[it]) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Molette (spinner) générique fiable : une [LazyColumn] avec accrochage
 * ([rememberSnapFlingBehavior]) et un rembourrage vertical qui CENTRE l'élément accroché.
 * L'élément le plus proche du centre est mis en évidence (tokens de thème) ; une bande
 * centrale matérialise la sélection. [onIndexSettled] est appelé à l'arrêt du défilement,
 * uniquement après une interaction utilisateur (pas de report parasite à l'init).
 *
 * Défilement vertical → indépendant du sens RTL. Hauteur FIXE (pas de géométrie fragile),
 * compatible avec le contenu défilant de la feuille (constraintes bornées).
 */
@Composable
private fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onIndexSettled: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = 5
    val itemHeight = 40.dp
    // initialFirstVisibleItemIndex = valeur initiale : avec le rembourrage vertical
    // symétrique, l'élément est CENTRÉ dès le 1er rendu (pas de flash à l'index 0, y
    // compris à la ré-instanciation de la roue nombre au changement d'unité).
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)

    // Index de l'élément dont le centre est le plus proche du centre du viewport (live).
    val centerIndex by remember {
        derivedStateOf {
            val info = state.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) initialIndex
            else {
                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2f) - mid) }!!.index
            }
        }
    }

    // Report à l'arrêt du défilement, seulement après une vraie interaction (évite un
    // report parasite au premier rendu / à l'ouverture).
    var userInteracted by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) userInteracted = true
        else if (userInteracted) onIndexSettled(centerIndex)
    }

    // Isole la molette de la fermeture-par-glisser du ModalBottomSheet : on CONSOMME le
    // résidu vertical (overscroll en butée, ex. 1 minute) après que la roue a défilé, pour
    // qu'il ne remonte JAMAIS jusqu'au sheet. La roue défile normalement ; seul le surplus
    // en bout de course est absorbé (la feuille reste fermable par poignée/scrim/retour).
    val isolateOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset = Offset(0f, available.y)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .nestedScroll(isolateOverscroll),
        contentAlignment = Alignment.Center
    ) {
        // Bande de sélection centrale (surbrillance via tokens de thème).
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(itemHeight)
        ) {}
        LazyColumn(
            state = state,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, label ->
                val selected = index == centerIndex
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = if (selected) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}
