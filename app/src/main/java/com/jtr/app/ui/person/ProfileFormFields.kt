package com.jtr.app.ui.person

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formulaire de profil PARTAGÉ entre la création (AddPersonScreen) et l'édition
 * (PersonDetailScreen) — garantit une ergonomie 100 % identique.
 *
 * Hiérarchie « Note-First » :
 *  1. Notes (« Notas varias ») et « Ce qu'il aime » immédiatement visibles ;
 *  2. tout le reste (genre, anniversaire, ville, origine, téléphone, email) est
 *     rangé dans une section repliable « Ajouter d'autres informations ».
 *
 * Le focus passe d'un champ à l'autre via ImeAction.Next ; le conteneur parent
 * (verticalScroll + imePadding) garde le champ actif visible au-dessus du clavier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormFields(
    notes: String,
    onNotesChange: (String) -> Unit,
    likes: String,
    onLikesChange: (String) -> Unit,
    gender: String?,
    onGenderChange: (String?) -> Unit,
    birthdate: Long?,
    onPickBirthday: () -> Unit,
    onClearBirthday: () -> Unit,
    birthdateNotify: Boolean,
    onBirthdateNotifyChange: (Boolean) -> Unit,
    city: String,
    cityHasCoords: Boolean,
    onCityChange: (String) -> Unit,
    onNavigateToMap: () -> Unit,
    cityNotify: Boolean,
    onCityNotifyChange: (Boolean) -> Unit,
    proximityAllowed: Boolean,
    onProximityBlocked: () -> Unit,
    origin: String,
    onOriginChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── PRIORITÉ : Notes ──────────────────────────────────────────────────
        var localNotes by remember(notes) { mutableStateOf(notes) }
        OutlinedTextField(
            value = localNotes,
            onValueChange = { localNotes = it; onNotesChange(it) },
            label = { Text(stringResource(R.string.person_notes_label)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4, maxLines = 10,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = sentences
        )

        // ── PRIORITÉ : Ce qu'il aime ──────────────────────────────────────────
        var localLikes by remember(likes) { mutableStateOf(likes) }
        OutlinedTextField(
            value = localLikes,
            onValueChange = { localLikes = it; onLikesChange(it) },
            label = { Text(stringResource(R.string.person_likes_label)) },
            leadingIcon = { Icon(Icons.Default.Favorite, null) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2, maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = sentences
        )

        // ── SECONDAIRE : section repliable ────────────────────────────────────
        var showMore by rememberSaveable { mutableStateOf(initiallyExpanded) }
        OutlinedButton(
            onClick = { showMore = !showMore },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                if (showMore) Icons.Default.ExpandLess else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (showMore) stringResource(R.string.person_more_info_hide)
                else stringResource(R.string.person_more_info_show)
            )
        }

        AnimatedVisibility(visible = showMore) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Genre
                Text(stringResource(R.string.person_gender_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "male"       to stringResource(R.string.person_gender_male),
                        "female"     to stringResource(R.string.person_gender_female),
                        "non-binary" to stringResource(R.string.person_gender_nonbinary_short)
                    ).forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = gender == value,
                            onClick = { onGenderChange(if (gender == value) null else value) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)
                        ) { Text(label) }
                    }
                }

                // Anniversaire
                OutlinedTextField(
                    value = birthdate?.let {
                        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(it))
                    } ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.person_birthday_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true, enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPickBirthday) {
                        Text(
                            if (birthdate == null) stringResource(R.string.person_birthday_select)
                            else stringResource(R.string.person_birthday_modify)
                        )
                    }
                    if (birthdate != null) {
                        TextButton(onClick = onClearBirthday) {
                            Text(stringResource(R.string.person_birthday_clear),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                NotifyToggleRow(
                    label = stringResource(R.string.person_birthday_notify),
                    checked = birthdateNotify,
                    enabled = true,
                    warning = null,
                    onCheckedChange = onBirthdateNotifyChange
                )

                // Ville
                var localCity by remember(city) { mutableStateOf(city) }
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = localCity,
                        onValueChange = { localCity = it; onCityChange(it) },
                        label = { Text(stringResource(R.string.person_city_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        trailingIcon = {
                            if (cityHasCoords) {
                                Icon(Icons.Default.MyLocation, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                    IconButton(onClick = onNavigateToMap, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Map,
                            contentDescription = stringResource(R.string.person_city_map_cd),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                // Notification de proximité — verrouillée si désactivée globalement.
                NotifyToggleRow(
                    label = stringResource(R.string.person_city_notify),
                    checked = cityNotify,
                    enabled = proximityAllowed,
                    warning = stringResource(R.string.person_proximity_disabled_hint),
                    onCheckedChange = onCityNotifyChange,
                    onBlockedClick = onProximityBlocked
                )

                // Origine
                var localOrigin by remember(origin) { mutableStateOf(origin) }
                OutlinedTextField(
                    value = localOrigin,
                    onValueChange = { localOrigin = it; onOriginChange(it) },
                    label = { Text(stringResource(R.string.person_origin_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Public, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                // Téléphone
                var localPhone by remember(phone) { mutableStateOf(phone) }
                OutlinedTextField(
                    value = localPhone,
                    onValueChange = { localPhone = it; onPhoneChange(it) },
                    label = { Text(stringResource(R.string.person_phone_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                // Email
                var localEmail by remember(email) { mutableStateOf(email) }
                OutlinedTextField(
                    value = localEmail,
                    onValueChange = { localEmail = it; onEmailChange(it) },
                    label = { Text(stringResource(R.string.person_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }
        }
    }
}

/**
 * Ligne de notification unifiée : un [Switch] Material 3 (avec coche interne) +
 * libellé. Quand [enabled] est faux, le switch est grisé, un [warning] discret
 * s'affiche, et taper la ligne déclenche [onBlockedClick].
 */
@Composable
private fun NotifyToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    warning: String?,
    onCheckedChange: (Boolean) -> Unit,
    onBlockedClick: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!enabled && onBlockedClick != null)
                        Modifier.clickable { onBlockedClick() }
                    else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked && enabled,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent = if (checked && enabled) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (!enabled && warning != null) {
            Text(
                warning,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 52.dp, top = 2.dp)
            )
        }
    }
}

/**
 * Dialogue de sélection d'anniversaire PARTAGÉ.
 *
 * Correctif state-sync : l'état est créé frais à chaque ouverture (scopé au
 * dialogue) et [DatePickerState.selectedDateMillis] est lu uniquement à la
 * validation. Changer l'année conserve donc instantanément le jour/mois.
 *
 * Les dates sont stockées en « midi local » ; on convertit en minuit-UTC pour
 * le picker (qui raisonne en UTC) et inversement à la validation — évite tout
 * décalage de jour lié au fuseau horaire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayPickerDialog(
    initialMillis: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initUtc = remember(initialMillis) { initialMillis?.let { localNoonToUtcMidnight(it) } }
    val displayMillis = initUtc ?: remember { todayUtcMidnight() }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initUtc,
        initialDisplayedMonthMillis = displayMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(utcMidnightToLocalNoon(it)) }
                onDismiss()
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun localNoonToUtcMidnight(localMillis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcMidnightToLocalNoon(utcMillis: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

private fun todayUtcMidnight(): Long {
    val now = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
