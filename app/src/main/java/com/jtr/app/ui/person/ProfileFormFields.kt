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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.DynamicLine
import java.util.Locale

/**
 * Formulaire de profil PARTAGÉ entre la création (AddPersonScreen) et l'édition
 * (PersonDetailScreen) — ergonomie « Contacts Google » épurée en v4.5.
 *
 * Ordre vertical (point 2 de la refonte) :
 *  1. Nom (champ unique épuré + sous-champs avancés repliables) ;
 *  2. Notes puis « Ce qu'il aime » (approche Note-First) ;
 *  3. section repliable « Ajouter d'autres informations » : Dates importantes →
 *     Relations → Téléphones → Emails → Pro → Origine → Ville & mini-carte.
 *
 * Les groupes répétables sont pilotés par des listes [DynamicLine] hoistées dans le
 * ViewModel ; le repli vers Room a lieu au submit. Le champ « Nom » fusionne
 * intelligemment prénom + nom de famille (1er mot = prénom, le reste = nom).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormFields(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    firstNameError: Boolean,
    nameDetails: NameDetails,
    onNameDetailsChange: (NameDetails) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    likes: String,
    onLikesChange: (String) -> Unit,
    phoneLines: List<DynamicLine>,
    onPhoneLinesChange: (List<DynamicLine>) -> Unit,
    emailLines: List<DynamicLine>,
    onEmailLinesChange: (List<DynamicLine>) -> Unit,
    dateLines: List<DynamicLine>,
    onDateLinesChange: (List<DynamicLine>) -> Unit,
    relationLines: List<DynamicLine>,
    onRelationLinesChange: (List<DynamicLine>) -> Unit,
    relationSuggestions: List<String>,
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
    jobTitle: String,
    onJobTitleChange: (String) -> Unit,
    department: String,
    onDepartmentChange: (String) -> Unit,
    company: String,
    onCompanyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Nom (champ unique + sous-champs avancés repliables) ─────────────
        NameSection(
            firstName = firstName,
            lastName = lastName,
            firstNameError = firstNameError,
            nameDetails = nameDetails,
            onFirstNameChange = onFirstNameChange,
            onLastNameChange = onLastNameChange,
            onNameDetailsChange = onNameDetailsChange
        )

        // ── 2. Notes (accessible instantanément — approche Note-First) ─────────
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

        // ── 3. Ce qu'il aime ──────────────────────────────────────────────────
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

        // ── 4. Bouton MASTER « + Ajouter d'autres informations » ──────────────
        // TOUJOURS replié par défaut (essence Note-First) : même en édition d'un
        // profil déjà rempli, l'utilisateur l'ouvre lui-même pour voir le reste.
        var showMore by rememberSaveable { mutableStateOf(false) }
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

        // ── 5. Bloc masqué : accordéons compacts déployés d'un coup ───────────
        AnimatedVisibility(visible = showMore) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 5.1 Dates importantes (accordéon)
                DateLinesSection(lines = dateLines, onLinesChange = onDateLinesChange)

                // 5.2 Relations (accordéon + autocomplétion des contacts JTR)
                TextLinesSection(
                    title = stringResource(R.string.section_relations),
                    leadingIcon = Icons.Default.Group,
                    addLabel = stringResource(R.string.add_relation),
                    valueLabel = stringResource(R.string.common_name_label),
                    keyboardType = KeyboardType.Text,
                    types = FieldTypes.RELATION,
                    lines = relationLines,
                    onLinesChange = onRelationLinesChange,
                    suggestions = relationSuggestions
                )

                // 5.3 Téléphones (accordéon)
                TextLinesSection(
                    title = stringResource(R.string.section_phones),
                    leadingIcon = Icons.Default.Phone,
                    addLabel = stringResource(R.string.add_phone),
                    valueLabel = stringResource(R.string.person_phone_label),
                    keyboardType = KeyboardType.Phone,
                    types = FieldTypes.PHONE,
                    lines = phoneLines,
                    onLinesChange = onPhoneLinesChange
                )

                // 5.4 Emails (accordéon + validation « @ »)
                val emailInvalidMsg = stringResource(R.string.person_email_invalid)
                TextLinesSection(
                    title = stringResource(R.string.section_emails),
                    leadingIcon = Icons.Default.Email,
                    addLabel = stringResource(R.string.add_email),
                    valueLabel = stringResource(R.string.person_email_label),
                    keyboardType = KeyboardType.Email,
                    types = FieldTypes.EMAIL,
                    lines = emailLines,
                    onLinesChange = onEmailLinesChange,
                    validator = { value -> if (isValidEmailValue(value)) null else emailInvalidMsg }
                )

                // 5.5 Informations professionnelles (accordéon, 0 dp si fermé)
                JobSection(
                    jobTitle = jobTitle,
                    onJobTitleChange = onJobTitleChange,
                    department = department,
                    onDepartmentChange = onDepartmentChange,
                    company = company,
                    onCompanyChange = onCompanyChange
                )

                // 5.6 Origine — juste AU-DESSUS de la ville (miroir du mode lecture)
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

                // 5.7 Ville & mini-carte (toute fin des blocs structurés)
                SectionLabel(stringResource(R.string.person_city_label))
                var localCity by remember(city) { mutableStateOf(city) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                NotifyToggleRow(
                    label = stringResource(R.string.person_city_notify),
                    checked = cityNotify,
                    enabled = proximityAllowed,
                    warning = stringResource(R.string.person_proximity_disabled_hint),
                    onCheckedChange = onCityNotifyChange,
                    onBlockedClick = onProximityBlocked
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// En-tête d'accordéon réutilisable (Dates, Relations, Téléphones, Emails)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Section repliable générique : ligne d'en-tête (icône + titre + flèche) cliquable
 * et contenu sous [AnimatedVisibility]. Replié ⇒ le contenu n'est PAS composé (0 dp).
 * Dépliée d'office via [initiallyExpanded] (édition d'un profil contenant déjà des
 * données valides) ; l'état est mémorisé entre recompositions.
 */
@Composable
private fun AccordionSection(
    title: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(leadingIcon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.common_hide)
                else stringResource(R.string.common_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section Nom : champ unique épuré + flèche d'extension vers les sous-champs
// ─────────────────────────────────────────────────────────────────────────────

/** Découpe « Jean Dupont » → ("Jean", "Dupont") : 1er mot = prénom, reste = nom. */
private fun splitDisplayName(text: String): Pair<String, String> {
    val idx = text.indexOf(' ')
    return if (idx < 0) text to "" else text.substring(0, idx) to text.substring(idx + 1)
}

/** Recompose l'affichage du champ unique à partir des sous-champs. */
private fun joinDisplayName(first: String, last: String): String =
    listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")

@Composable
private fun NameSection(
    firstName: String,
    lastName: String,
    firstNameError: Boolean,
    nameDetails: NameDetails,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onNameDetailsChange: (NameDetails) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    // État local du champ unique : capture la valeur INITIALE seulement (pas de clé
    // sur firstName/lastName) afin de préserver les espaces pendant la frappe.
    var displayName by rememberSaveable { mutableStateOf(joinDisplayName(firstName, lastName)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { input ->
                    displayName = input
                    val (f, l) = splitDisplayName(input)
                    onFirstNameChange(f)
                    onLastNameChange(l)
                },
                label = { Text(stringResource(R.string.person_full_name_label)) },
                leadingIcon = { Icon(Icons.Default.Badge, null) },
                isError = firstNameError,
                supportingText = if (firstNameError) ({
                    Text(stringResource(R.string.person_first_name_required))
                }) else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.name_details_hide)
                    else stringResource(R.string.name_details_show),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NameSubField(stringResource(R.string.name_prefix), nameDetails.prefix) {
                    onNameDetailsChange(nameDetails.copy(prefix = it))
                }
                NameSubField(stringResource(R.string.name_given), firstName) {
                    onFirstNameChange(it)
                    displayName = joinDisplayName(it, lastName)
                }
                NameSubField(stringResource(R.string.name_middle), nameDetails.middleName) {
                    onNameDetailsChange(nameDetails.copy(middleName = it))
                }
                NameSubField(stringResource(R.string.name_family), lastName) {
                    onLastNameChange(it)
                    displayName = joinDisplayName(firstName, it)
                }
                NameSubField(stringResource(R.string.name_suffix), nameDetails.suffix) {
                    onNameDetailsChange(nameDetails.copy(suffix = it))
                }
                NameSubField(stringResource(R.string.name_phonetic), nameDetails.phonetic) {
                    onNameDetailsChange(nameDetails.copy(phonetic = it))
                }
                NameSubField(stringResource(R.string.name_nickname), nameDetails.nickname) {
                    onNameDetailsChange(nameDetails.copy(nickname = it))
                }
            }
        }
    }
}

@Composable
private fun NameSubField(label: String, value: String, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Section Travail (poste, département, entreprise)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Section « Informations professionnelles » repliable (calquée sur la section Nom) :
 * une seule ligne-en-tête cliquable + flèche ; les 3 sous-champs ne sont composés
 * (donc n'occupent de l'espace) QUE si la section est dépliée — 0 dp sinon.
 */
@Composable
private fun JobSection(
    jobTitle: String,
    onJobTitleChange: (String) -> Unit,
    department: String,
    onDepartmentChange: (String) -> Unit,
    company: String,
    onCompanyChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val opts = KeyboardOptions(
        capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
    val actions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    // Déplié d'office si des données existent déjà (édition d'un contact avec job).
    var expanded by rememberSaveable {
        mutableStateOf(jobTitle.isNotBlank() || department.isNotBlank() || company.isNotBlank())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Work, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.section_work_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.job_details_hide)
                else stringResource(R.string.job_details_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = jobTitle,
                    onValueChange = onJobTitleChange,
                    label = { Text(stringResource(R.string.person_job_title_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = opts,
                    keyboardActions = actions,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = onDepartmentChange,
                    label = { Text(stringResource(R.string.person_department_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = opts,
                    keyboardActions = actions,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = onCompanyChange,
                    label = { Text(stringResource(R.string.person_company_label)) },
                    leadingIcon = { Icon(Icons.Default.Business, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = opts,
                    keyboardActions = actions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sections de lignes dynamiques
// ─────────────────────────────────────────────────────────────────────────────

/** Section répétable de texte simple (téléphones, emails, relations) en accordéon. */
@Composable
private fun TextLinesSection(
    title: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    addLabel: String,
    valueLabel: String,
    keyboardType: KeyboardType,
    types: List<TypeOption>,
    lines: List<DynamicLine>,
    onLinesChange: (List<DynamicLine>) -> Unit,
    suggestions: List<String>? = null,
    validator: ((String) -> String?)? = null
) {
    val focusManager = LocalFocusManager.current
    // Déplié d'office si au moins une ligne valide existe déjà (édition).
    AccordionSection(
        title = title,
        leadingIcon = leadingIcon,
        initiallyExpanded = lines.any { it.value.isNotBlank() }
    ) {
        lines.forEach { line ->
            val errorMsg = validator?.invoke(line.value)
            DynamicLineRow(
                line = line,
                types = types,
                valueLabel = valueLabel,
                value = line.value,
                onValueChange = { nv ->
                    onLinesChange(lines.map { if (it.id == line.id) it.copy(value = nv) else it })
                },
                keyboardType = keyboardType,
                visualTransformation = VisualTransformation.None,
                placeholder = null,
                isError = errorMsg != null,
                errorMessage = errorMsg,
                showDelete = lines.size > 1,
                onTypeChange = { k ->
                    onLinesChange(lines.map { if (it.id == line.id) it.copy(label = k) else it })
                },
                onDelete = { onLinesChange(lines.filter { it.id != line.id }) },
                onImeNext = { focusManager.moveFocus(FocusDirection.Down) },
                suggestions = suggestions
            )
        }
        AddLineButton(addLabel) { onLinesChange(lines + DynamicLine(label = types.first().key)) }
    }
}

/** Section répétable de dates importantes — saisie masquée + cloche de notification. */
@Composable
private fun DateLinesSection(
    lines: List<DynamicLine>,
    onLinesChange: (List<DynamicLine>) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val locale = Locale.getDefault()
    val spec = remember(locale) { resolveDateFormatSpec(locale) }
    val maxLen = remember(spec) { spec.segmentLengths.sum() }
    val transformation = remember(spec) {
        DateMaskVisualTransformation(spec.segmentLengths, spec.separator)
    }

    val dayTok = stringResource(R.string.birthday_token_day)
    val monthTok = stringResource(R.string.birthday_token_month)
    val yearTok = stringResource(R.string.birthday_token_year)
    val placeholder = remember(spec, dayTok, monthTok, yearTok) {
        spec.order.joinToString(spec.separator.toString()) {
            when (it) {
                DateField.DAY -> dayTok
                DateField.MONTH -> monthTok
                DateField.YEAR -> yearTok
            }
        }
    }
    val invalidMsg = stringResource(R.string.person_birthday_invalid)

    AccordionSection(
        title = stringResource(R.string.section_dates),
        leadingIcon = Icons.Default.Cake,
        initiallyExpanded = lines.any { it.value.isNotBlank() }
    ) {
        lines.forEach { line ->
            val complete = line.value.length == maxLen
            val isError = complete && rawDigitsToMillis(line.value, spec) == null
            DynamicLineRow(
                line = line,
                types = FieldTypes.DATE,
                valueLabel = stringResource(R.string.date_value_label),
                value = line.value,
                onValueChange = { input ->
                    val digits = input.filter { ch -> ch.isDigit() }.take(maxLen)
                    onLinesChange(lines.map { if (it.id == line.id) it.copy(value = digits) else it })
                },
                keyboardType = KeyboardType.Number,
                visualTransformation = transformation,
                placeholder = placeholder,
                isError = isError,
                errorMessage = invalidMsg,
                showDelete = lines.size > 1,
                onTypeChange = { k ->
                    onLinesChange(lines.map { if (it.id == line.id) it.copy(label = k) else it })
                },
                onDelete = { onLinesChange(lines.filter { it.id != line.id }) },
                onImeNext = { focusManager.moveFocus(FocusDirection.Down) },
                notify = line.notify,
                onNotifyToggle = {
                    onLinesChange(lines.map {
                        if (it.id == line.id) it.copy(notify = !it.notify) else it
                    })
                }
            )
        }
        AddLineButton(stringResource(R.string.add_date)) {
            onLinesChange(lines + DynamicLine(label = FieldTypes.DATE_BIRTHDAY))
        }
    }
}

/**
 * Une ligne : [type ▾] [champ texte] [🔔 si date] [(–) si liste > 1].
 *
 * - [suggestions] non nul → le champ de valeur devient une autocomplétion
 *   ([ExposedDropdownMenuBox]) sans jamais bloquer la saisie d'un nom inconnu.
 * - [onNotifyToggle] non nul → affiche une cloche cliquable (dates importantes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicLineRow(
    line: DynamicLine,
    types: List<TypeOption>,
    valueLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation,
    placeholder: String?,
    isError: Boolean,
    errorMessage: String?,
    showDelete: Boolean,
    onTypeChange: (String) -> Unit,
    onDelete: () -> Unit,
    onImeNext: () -> Unit,
    suggestions: List<String>? = null,
    notify: Boolean? = null,
    onNotifyToggle: (() -> Unit)? = null
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TypeDropdown(
            types = types,
            selectedKey = line.label,
            onSelect = onTypeChange,
            onCustomRequested = { showCustomDialog = true }
        )

        ValueField(
            value = value,
            onValueChange = onValueChange,
            valueLabel = valueLabel,
            placeholder = placeholder,
            isError = isError,
            errorMessage = errorMessage,
            keyboardType = keyboardType,
            visualTransformation = visualTransformation,
            onImeNext = onImeNext,
            suggestions = suggestions,
            modifier = Modifier.weight(1f)
        )

        if (notify != null && onNotifyToggle != null) {
            IconButton(onClick = onNotifyToggle) {
                Icon(
                    if (notify) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = stringResource(R.string.person_date_notify_cd),
                    tint = if (notify) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.RemoveCircleOutline,
                    contentDescription = stringResource(R.string.field_remove_cd),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showCustomDialog) {
        CustomLabelDialog(
            initial = types.firstOrNull { it.key == line.label }?.let { "" } ?: line.label,
            onConfirm = { onTypeChange(it); showCustomDialog = false },
            onDismiss = { showCustomDialog = false }
        )
    }
}

/** Champ de valeur, en autocomplétion si [suggestions] est fourni. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueField(
    value: String,
    onValueChange: (String) -> Unit,
    valueLabel: String,
    placeholder: String?,
    isError: Boolean,
    errorMessage: String?,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation,
    onImeNext: () -> Unit,
    suggestions: List<String>?,
    modifier: Modifier = Modifier
) {
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(valueLabel) },
            placeholder = placeholder?.let { ph -> { Text(ph) } },
            singleLine = true,
            isError = isError,
            supportingText = if (isError && errorMessage != null) {
                { Text(errorMessage) }
            } else null,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onImeNext() }),
            modifier = fieldModifier
        )
    }

    if (suggestions == null) {
        field(modifier)
        return
    }

    // Autocomplétion : filtre les contacts existants, sans bloquer une saisie libre.
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        if (value.isBlank()) emptyList()
        else suggestions.filter {
            it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true)
        }.take(5)
    }
    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(valueLabel) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onImeNext() }),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { onValueChange(suggestion); expanded = false },
                    leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) }
                )
            }
        }
    }
}

/** Bouton-menu de type à gauche d'une ligne dynamique. */
@Composable
private fun TypeDropdown(
    types: List<TypeOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onCustomRequested: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val known = types.firstOrNull { it.key == selectedKey }
    // Libellé affiché : type connu localisé, sinon le texte personnalisé saisi.
    val displayLabel = when {
        known != null -> stringResource(known.labelRes)
        selectedKey.isNotBlank() -> selectedKey
        else -> stringResource(types.first().labelRes)
    }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.widthIn(min = 84.dp, max = 132.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                displayLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            types.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(stringResource(opt.labelRes)) },
                    onClick = {
                        expanded = false
                        if (opt.key == FieldTypes.CUSTOM) onCustomRequested()
                        else onSelect(opt.key)
                    }
                )
            }
        }
    }
}

/** Petit dialogue de saisie d'un libellé personnalisé (Emails, Dates). */
@Composable
private fun CustomLabelDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_label_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.custom_label_dialog_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AddLineButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ligne de notification unifiée (proximité ville)
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Masque de saisie de date
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Insère automatiquement les séparateurs d'une date au fil de la frappe.
 *
 * Le texte d'entrée ne contient que des chiffres ; [segmentLengths] donne la taille
 * de chaque composant dans l'ordre courant (ex. [2,2,4] ou [4,2,2]). L'[OffsetMapping]
 * garde le curseur cohérent, y compris au backspace.
 */
private class DateMaskVisualTransformation(
    segmentLengths: List<Int>,
    private val separator: Char
) : VisualTransformation {

    // Frontières (en index original) après lesquelles insérer un séparateur.
    private val sepAfter: List<Int> =
        segmentLengths.runningReduce { acc, n -> acc + n }.dropLast(1)

    // Positions des séparateurs dans le texte transformé.
    private val sepTransformedPos: List<Int> =
        sepAfter.mapIndexed { i, s -> s + i }

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val sb = StringBuilder(digits.length + sepAfter.size)
        for (i in digits.indices) {
            sb.append(digits[i])
            if ((i + 1) in sepAfter) sb.append(separator)
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + sepAfter.count { offset >= it }

            override fun transformedToOriginal(offset: Int): Int =
                offset - sepTransformedPos.count { offset > it }
        }
        return TransformedText(AnnotatedString(sb.toString()), mapping)
    }
}
