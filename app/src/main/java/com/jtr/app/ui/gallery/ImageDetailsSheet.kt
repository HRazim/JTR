package com.jtr.app.ui.gallery

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow




import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jtr.app.R
import com.jtr.app.domain.model.GalleryImage
import com.jtr.app.ui.components.FormTextField
import com.jtr.app.ui.components.WheelPicker
import com.jtr.app.ui.components.rememberDialogBottomInsetDp
import com.jtr.app.ui.map.MapScreen
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Plage d'années proposée autour de l'année en cours — assez large pour une vieille photo. */
private const val YEAR_SPAN_BACK = 80
private const val YEAR_SPAN_FORWARD = 5

/** Lignes d'aperçu de la description au repos : au-delà, « … » (v7.1.97). */
private const val DESCRIPTION_PREVIEW_LINES = 4

/** Hauteur de DÉPART de l'éditeur de description : petit (~2 lignes), il grandit en tapant. */
private const val EDITOR_MIN_LINES = 2

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
    onLocationChange: (Double?, Double?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetStateCompat()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var editing by remember { mutableStateOf(EditTarget.NONE) }
    var showPicker by remember { mutableStateOf(false) }
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
                // v7.1.110 — l'éditeur n'écrit plus au cran : une seule écriture au « Done »
                // (si changement), rien du tout si on ferme autrement.
                DateTimeEditor(
                    millis = image.createdAt,
                    onCommit = onCreatedAtChange,
                    onDone = { editing = EditTarget.NONE }
                )
            }

            // ── DESCRIPTION ─────────────────────────────────────────────────────────
            // v7.1.104 — l'édition se fait EN PLACE : la ligne devient un champ compact qui
            // grandit avec la frappe (plus de takeover plein panneau, jugé énorme dès
            // l'ouverture d'un champ vide). Même grammaire que la date : le reste du panneau
            // reste visible, « Done » referme.
            val caption = image.caption.orEmpty()
            if (editing == EditTarget.DESCRIPTION) {
                DescriptionEditor(
                    draft = draft,
                    onDraftChange = { draft = it },
                    focusRequester = focusRequester,
                    onDone = {
                        commitCaption()
                        editing = EditTarget.NONE
                    }
                )
            } else {
                DetailRow(
                    icon = Icons.AutoMirrored.Outlined.Notes,
                    label = stringResource(R.string.gallery_details_description),
                    value = caption.ifBlank {
                        stringResource(R.string.gallery_details_description_hint)
                    },
                    isPlaceholder = caption.isBlank(),
                    // TRONCATURE : une note de mille mots ne doit pas étirer le panneau. Au
                    // repos on en montre l'amorce, « … » signale la suite, et la hauteur du
                    // panneau reste la même quelle que soit la longueur du texte.
                    maxLines = DESCRIPTION_PREVIEW_LINES,
                    onClick = {
                        draft = caption
                        editing = EditTarget.DESCRIPTION
                    }
                )
            }

            // ── LIEU ────────────────────────────────────────────────────────────────
            val hasLocation = image.latitude != null && image.longitude != null
            val placeText = image.placeLabel?.takeIf { it.isNotBlank() }
            DetailRow(
                icon = Icons.Outlined.LocationOn,
                label = stringResource(R.string.gallery_details_location),
                value = placeText
                    ?: stringResource(R.string.gallery_details_location_add),
                isPlaceholder = !hasLocation,
                onClick = { showPicker = true },
                // Un lieu posé se retire d'ici même : pas de sous-menu pour une action
                // aussi simple, et l'action n'existe pas quand il n'y a rien à retirer.
                trailing = if (hasLocation) {
                    {
                        IconButton(onClick = { onLocationChange(null, null, null) }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription =
                                    stringResource(R.string.gallery_details_location_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else null
            )
        }
    }

    // SÉLECTEUR DE CARTE posé en FENÊTRE PAR-DESSUS : surtout pas une navigation vers
    // `Routes.MAP_PICKER`, qui démonterait l'écran galerie et donc la visionneuse
    // (`viewerIndex` est un `remember` non-Saveable). Ici, visionneuse et panneau restent
    // montés derrière, et le sélecteur rend son résultat par simple callback.
    if (showPicker) {
        Dialog(
            onDismissRequest = { showPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MapScreen(
                onLocationSelected = { label, lat, lng ->
                    onLocationChange(lat, lng, label)
                    showPicker = false
                },
                onNavigateBack = { showPicker = false },
                // Déjà localisée ⇒ le sélecteur s'ouvre CENTRÉ sur le point enregistré.
                initialLabel = image.placeLabel,
                initialLat = image.latitude,
                initialLng = image.longitude,
                // Dans un Dialog, les insets de Compose valent 0 ET la fenêtre déborde sous
                // l'écran : sans cette marge, le bandeau « Enregistrer » passe sous les
                // touches Android (mesuré sur S21).
                bottomInset = rememberDialogBottomInsetDp()
            )
        }
    }

    // Le brouillon suit la ligne si elle change sous nos pieds (p. ex. après l'écriture).
    LaunchedEffect(image.caption) {
        if (editing != EditTarget.DESCRIPTION) draft = image.caption.orEmpty()
    }
}

/**
 * ÉDITEUR DE DESCRIPTION compact auto-grandissant (v7.1.104) — il remplace le takeover
 * plein panneau de v7.1.97, énorme dès l'ouverture d'un champ vide (220-380 dp).
 *
 * Le champ démarre PETIT ([EDITOR_MIN_LINES] lignes), GRANDIT ligne à ligne au fil de la
 * frappe, PLAFONNE à ~la moitié de la hauteur d'écran, puis **défile en interne** : le
 * curseur reste visible et on peut remonter/descendre dans le texte — le champ ne prend
 * JAMAIS toute la page. Il s'édite EN PLACE dans le panneau, même grammaire que l'éditeur
 * de date (le reste des lignes reste visible, « Done » en dessous à droite).
 */
@Composable
private fun DescriptionEditor(
    draft: String,
    onDraftChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    // Plafond : ~la moitié de l'écran, quel que soit l'appareil — au-delà, scroll interne.
    val maxFieldHeight = LocalConfiguration.current.screenHeightDp.dp / 2
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        // v7.1.135 — le champ du FORMULAIRE (16 dp, `surfaceVariant`) et non un
        // `OutlinedTextField` nu : c'était le dernier champ du module resté au défaut M3
        // (coins 4 dp, intérieur transparent), à côté d'un panneau qui, lui, suit le
        // langage des formulaires depuis le Groupe 3. `textStyle` disparaît : le socle
        // n'en impose aucun, donc le champ hérite de `LocalTextStyle`, qui EST
        // `bodyLarge` ici — même rendu, un paramètre de moins.
        FormTextField(
            value = draft,
            onValueChange = onDraftChange,
            // Le libellé flottant porte le nom du champ (a11y) — l'icône de la ligne au
            // repos n'est plus là pour le faire.
            label = stringResource(R.string.gallery_details_description),
            placeholder = stringResource(R.string.gallery_details_description_hint),
            // v7.1.126 — même traitement que les notes : une description est de la prose,
            // majuscule en début de champ et après un point.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            // L'auto-grandissement tient à ces deux-là : plancher de lignes + plafond de
            // hauteur. `singleLine = false` est EXPLICITE — le socle, lui, part de `true`.
            singleLine = false,
            minLines = EDITOR_MIN_LINES,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxFieldHeight)
                .focusRequester(focusRequester)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.common_done))
            }
        }
    }
    // Le clavier arrive sur le champ sans tap supplémentaire.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
    onClick: () -> Unit,
    maxLines: Int = Int.MAX_VALUE,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                // ⚠️ AUCUN `semantics {}` ICI, volontairement. `clickable` fusionne DÉJÀ
                // ses descendants et impose la cible de 48 dp sur un nœud ENGLOBANT ; un
                // `semantics {}` posé sur la Row crée un nœud intérieur qui BLOQUE cette
                // fusion — l'`uiautomator dump` montrait alors un cliquable SANS libellé
                // (144 px) doublé d'un libellé non cliquable (138 px), soit un bouton
                // anonyme pour TalkBack (défaut de v7.1.86, reproduit puis mesuré ici, et
                // que ni `mergeDescendants` ni le réordonnancement ne corrigeaient).
                // Le libellé du champ est donc porté par l'ICÔNE : le nœud cliquable
                // annonce « Date, April 28, 2026 · 11:40 » d'un seul tenant.
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                // Porte le NOM du champ (« Date », « Description », « Lieu ») : fusionné au
                // texte de valeur par le `clickable`, cela donne une annonce complète.
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Action secondaire éventuelle, HORS de la zone cliquable principale pour qu'elle
        // ait son propre nœud d'accessibilité et sa propre cible tactile.
        trailing?.let { it(); Box(Modifier.size(4.dp)) }
    }
}

/**
 * Éditeur date/heure révélé au tap sur la ligne : un segment « Date | Heure » puis les
 * MOLETTES (3 roues mois/jour/année, ou 2 roues heure/minute).
 *
 * v7.1.110 — les crans ne touchent qu'un ÉTAT LOCAL ([pending]) : plus aucune écriture
 * pendant le défilement (l'ancien comportement écrivait `createdAt` + stampait `modifiedAt`
 * À CHAQUE CRAN, et sous un tri actif l'image se réordonnait PENDANT le réglage). La seule
 * écriture part au « Done », et seulement si la valeur a changé ([commitDateIfChanged]) ;
 * fermer l'éditeur ou le panneau sans « Done » jette [pending] — annulation naturelle.
 * Les deux onglets lisent/écrivent le même [pending] : régler la date puis l'heure compose
 * bien UNE valeur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeEditor(millis: Long, onCommit: (Long) -> Unit, onDone: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    // État local de l'édition — la clé `millis` le réinitialise si la ligne change sous
    // nos pieds entre deux ouvertures (jamais PENDANT : l'édition n'écrit plus rien).
    var pending by remember(millis) { mutableLongStateOf(millis) }
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
        if (tab == 0) DateWheels(millis = pending, onChange = { pending = it })
        else TimeWheels(millis = pending, onChange = { pending = it })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                commitDateIfChanged(original = millis, pending = pending, commit = onCommit)
                onDone()
            }) { Text(stringResource(R.string.common_done)) }
        }
    }
}

/**
 * Décision d'écriture du « Done » (v7.1.110), extraite pour le test JVM : [commit] est
 * appelé UNE fois avec [pending] si la valeur a changé, JAMAIS sinon (no-op silencieux —
 * ni écriture ni stamp `modifiedAt` quand on rouvre l'éditeur juste pour regarder).
 * Renvoie `true` si l'écriture est partie.
 */
internal fun commitDateIfChanged(original: Long, pending: Long, commit: (Long) -> Unit): Boolean {
    if (pending == original) return false
    commit(pending)
    return true
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
