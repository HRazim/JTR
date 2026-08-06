package com.jtr.app.ui.person

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.jtr.app.domain.model.NoteSection
import com.jtr.app.utils.DateCanonical
import com.jtr.app.utils.matchesAllTokens
import com.jtr.app.utils.searchTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

/**
 * Formulaire de profil PARTAGÉ entre la création (AddPersonScreen) et l'édition
 * (PersonDetailScreen) — ergonomie « Contacts Google » épurée en v4.5.
 *
 * v7.0.2 — refonte de STYLE uniquement (structure, ordre et comportement INCHANGÉS) :
 * les champs adoptent un habillage « carte souple » léger (conteneur [ColorScheme.surfaceVariant]
 * discret, coins arrondis généreux, sans soulignement), une icône en tête, et le libellé
 * passe en placeholder. Les affordances d'ajout deviennent un « + » discret.
 *
 * Ordre vertical (inchangé) :
 *  1. Nom (champ unique épuré + sous-champs avancés repliables) ;
 *  2. Notes puis « Ce qu'il aime » (approche Note-First) ;
 *  3. section repliable « Ajouter d'autres informations » : Dates importantes →
 *     Relations → Téléphones → Emails → Pro → Origine → Ville & mini-carte.
 *
 * Les groupes répétables sont pilotés par des listes [DynamicLine] hoistées dans le
 * ViewModel ; le repli vers Room a lieu au submit. Le champ « Nom » fusionne
 * intelligemment prénom + nom de famille (1er mot = prénom, le reste = nom).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileFormFields(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    firstNameError: Boolean,
    nameDetails: NameDetails,
    onNameDetailsChange: (NameDetails) -> Unit,
    noteSections: List<NoteSection>,
    onNoteSectionsChange: (List<NoteSection>) -> Unit,
    phoneLines: List<DynamicLine>,
    onPhoneLinesChange: (List<DynamicLine>) -> Unit,
    emailLines: List<DynamicLine>,
    onEmailLinesChange: (List<DynamicLine>) -> Unit,
    dateLines: List<DynamicLine>,
    onDateLinesChange: (List<DynamicLine>) -> Unit,
    relationLines: List<DynamicLine>,
    onRelationLinesChange: (List<DynamicLine>) -> Unit,
    relationSuggestions: List<PersonRef>,
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
    noteReorderState: NoteReorderState,
    // `modifier` reste le PREMIER paramètre optionnel (convention Compose, vérifiée par lint).
    modifier: Modifier = Modifier,
    // v7.1.58 — DÉFILEMENT AUTO du bloc « plus / moins d'informations ». L'état `showMore`
    // reste LOCAL (cf. plus bas) : l'écran hôte est seulement NOTIFIÉ du basculement et de la
    // position du bouton. Les deux paramètres ont une valeur par défaut → un appelant qui ne
    // veut pas de défilement auto n'a rien à faire.
    onExpandedChange: (Boolean) -> Unit = {},
    /** Position verticale du bouton bascule dans la fenêtre, à chaque passe de layout. */
    onToggleTopInRoot: (Float) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        // ── 2. Sections de notes personnalisables (v7.0.3, cœur de JTR) ────────
        // Remplacent les anciens champs fixes « Misc notes » / « What they like ».
        NoteSectionsEditor(
            sections = noteSections,
            onSectionsChange = onNoteSectionsChange,
            reorderState = noteReorderState
        )

        // ── 4. Bouton MASTER « + Ajouter d'autres informations » ──────────────
        // TOUJOURS replié par défaut (essence Note-First) : même en édition d'un
        // profil déjà rempli, l'utilisateur l'ouvre lui-même pour voir le reste.
        var showMore by rememberSaveable { mutableStateOf(false) }
        OutlinedButton(
            onClick = {
                showMore = !showMore
                // v7.1.58 — notifie l'écran hôte APRÈS la bascule (nouvelle valeur), pour qu'il
                // pilote le défilement. L'état reste la propriété de ce composable.
                onExpandedChange(showMore)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onToggleTopInRoot(it.positionInRoot().y) },
            shape = RoundedCornerShape(16.dp)
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
        // v7.1.59 — DÉPLOIEMENT VERS LE BAS. Le défaut d'AnimatedVisibility est
        // `expandVertically(expandFrom = Alignment.Bottom)` : le bloc est aligné en BAS de sa
        // boîte qui grandit, donc le HAUT est rogné et les champs apparaissent dans l'ordre
        // INVERSE (« Ville » d'abord, « Dates importantes » en dernier), en remontant — pile à
        // contresens du défilement qui, lui, descend. En ancrant sur `Top`, le bloc se déroule
        // vers le bas depuis le bouton, DANS LE MÊME SENS que le défilement : une seule motion.
        AnimatedVisibility(
            visible = showMore,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 5.1 Dates importantes (accordéon)
                DateLinesSection(lines = dateLines, onLinesChange = onDateLinesChange)

                // 5.2 Relations (accordéon + autocomplétion des contacts JTR)
                // v7.1.6 : sélectionner une suggestion STOCKE l'id (linkedPersonId), pas le nom.
                RelationLinesSection(
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
                SoftTextField(
                    value = localOrigin,
                    onValueChange = { localOrigin = it; onOriginChange(it) },
                    placeholder = stringResource(R.string.person_origin_label),
                    leadingIcon = Icons.Default.Public,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )

                // 5.7 Ville & mini-carte (toute fin des blocs structurés)
                var localCity by remember(city) { mutableStateOf(city) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    SoftTextField(
                        value = localCity,
                        onValueChange = { localCity = it; onCityChange(it) },
                        placeholder = stringResource(R.string.person_city_label),
                        leadingIcon = Icons.Default.LocationOn,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        trailingIcon = if (cityHasCoords) {
                            {
                                Icon(Icons.Default.MyLocation, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                        } else null,
                        // Ville saisie SANS coordonnées → guide très visible vers
                        // l'icône carte (validation de l'adresse précise).
                        supportingText = if (localCity.isNotBlank() && !cityHasCoords) ({
                            Text(
                                text = stringResource(R.string.city_map_hint),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }) else null,
                        modifier = Modifier.weight(1f).bringIntoViewOnFocus()
                    )
                    FilledTonalIconButton(
                        onClick = onNavigateToMap,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
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
// Champ « carte souple » réutilisable (v7.0.2) — TextField rempli léger
// ─────────────────────────────────────────────────────────────────────────────

/** Couleurs du champ souple : conteneur teinté discret (surfaceVariant), sans soulignement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun softFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent
)

/**
 * Champ de texte « carte souple » : conteneur léger arrondi, icône en tête, libellé
 * en placeholder. Habillage commun à tous les champs simples du formulaire (v7.0.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector?,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText,
        shape = RoundedCornerShape(16.dp),
        colors = softFieldColors(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
    )
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
                tint = MaterialTheme.colorScheme.primary)
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

@OptIn(ExperimentalMaterial3Api::class)
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
        // Chevron de dépliage des sous-champs DANS la carte du nom (bord de fin), façon
        // pilule Google (icône + champ + chevron) — plus de bouton circulaire externe.
        // RTL : le trailingIcon est placé du bon côté automatiquement par le TextField.
        SoftTextField(
            value = displayName,
            onValueChange = { input ->
                displayName = input
                val (f, l) = splitDisplayName(input)
                onFirstNameChange(f)
                onLastNameChange(l)
            },
            placeholder = stringResource(R.string.person_full_name_label),
            leadingIcon = Icons.Default.Badge,
            isError = firstNameError,
            supportingText = if (firstNameError) ({
                Text(stringResource(R.string.person_first_name_required))
            }) else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.name_details_hide)
                        else stringResource(R.string.name_details_show),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

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
    SoftTextField(
        value = value,
        onValueChange = onChange,
        placeholder = label,
        leadingIcon = null,
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
                tint = MaterialTheme.colorScheme.primary)
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
                SoftTextField(
                    value = jobTitle,
                    onValueChange = onJobTitleChange,
                    placeholder = stringResource(R.string.person_job_title_label),
                    leadingIcon = Icons.Default.Work,
                    keyboardOptions = opts,
                    keyboardActions = actions,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )
                SoftTextField(
                    value = department,
                    onValueChange = onDepartmentChange,
                    placeholder = stringResource(R.string.person_department_label),
                    leadingIcon = Icons.Default.Apartment,
                    keyboardOptions = opts,
                    keyboardActions = actions,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                )
                SoftTextField(
                    value = company,
                    onValueChange = onCompanyChange,
                    placeholder = stringResource(R.string.person_company_label),
                    leadingIcon = Icons.Default.Business,
                    keyboardOptions = opts,
                    keyboardActions = actions,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
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
                onImeNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        }
        AddLineButton(addLabel) { onLinesChange(lines + DynamicLine(label = types.first().key)) }
    }
}

/**
 * Section RELATIONS (v7.1.6) — autocomplétion sur les CONTACTS (id + nom) : choisir une
 * suggestion stocke l'identifiant STABLE dans [DynamicLine.linkedPersonId] (jamais le nom),
 * tandis qu'une saisie manuelle défait le lien (texte libre). Un nom héritage AMBIGU
 * (plusieurs homonymes, sans id) est signalé « à vérifier » pour invitation à re-sélectionner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationLinesSection(
    lines: List<DynamicLine>,
    onLinesChange: (List<DynamicLine>) -> Unit,
    suggestions: List<PersonRef>
) {
    val focusManager = LocalFocusManager.current
    AccordionSection(
        title = stringResource(R.string.section_relations),
        leadingIcon = Icons.Default.Group,
        initiallyExpanded = lines.any { it.value.isNotBlank() }
    ) {
        lines.forEach { line ->
            var showCustomDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TypeDropdown(
                    types = FieldTypes.RELATION,
                    selectedKey = line.label,
                    onSelect = { k ->
                        onLinesChange(lines.map { if (it.id == line.id) it.copy(label = k) else it })
                    },
                    onCustomRequested = { showCustomDialog = true }
                )
                RelationValueField(
                    value = line.value,
                    linkedPersonId = line.linkedPersonId,
                    suggestions = suggestions,
                    onPick = { ref ->
                        onLinesChange(lines.map {
                            if (it.id == line.id) it.copy(value = ref.name, linkedPersonId = ref.id) else it
                        })
                    },
                    onTextChange = { nv ->
                        // Saisie manuelle : la valeur n'identifie plus un contact → on défait le lien.
                        onLinesChange(lines.map {
                            if (it.id == line.id) it.copy(value = nv, linkedPersonId = null) else it
                        })
                    },
                    onImeNext = { focusManager.moveFocus(FocusDirection.Down) },
                    modifier = Modifier.weight(1f)
                )
                if (lines.size > 1) {
                    IconButton(onClick = { onLinesChange(lines.filter { it.id != line.id }) }) {
                        Icon(Icons.Default.RemoveCircleOutline,
                            contentDescription = stringResource(R.string.field_remove_cd),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // « À vérifier » : relation héritée non liée dont le nom désigne PLUSIEURS contacts.
            val ambiguous = line.linkedPersonId == null && line.value.isNotBlank() &&
                suggestions.count { it.name.equals(line.value.trim(), ignoreCase = true) } > 1
            if (ambiguous) {
                Text(
                    stringResource(R.string.person_relation_ambiguous),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
            if (showCustomDialog) {
                CustomLabelDialog(
                    initial = FieldTypes.RELATION.firstOrNull { it.key == line.label }?.let { "" } ?: line.label,
                    onConfirm = { newLabel ->
                        onLinesChange(lines.map { if (it.id == line.id) it.copy(label = newLabel) else it })
                        showCustomDialog = false
                    },
                    onDismiss = { showCustomDialog = false }
                )
            }
        }
        AddLineButton(stringResource(R.string.add_relation)) {
            onLinesChange(lines + DynamicLine(label = FieldTypes.RELATION.first().key))
        }
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
    val monthDayLen = remember(spec) { spec.monthDaySegmentLengths.sum() } // = 4
    // Masque complet (adaptatif 4 ↔ longueur totale) ; masque dédié jour/mois pour le mode
    // « sans année » des locales YMD (année en tête → l'année vide ne se déduit pas du préfixe).
    val transformation = remember(spec) {
        DateMaskVisualTransformation(spec.segmentLengths, spec.separator)
    }
    val monthDayTransformation = remember(spec) {
        DateMaskVisualTransformation(spec.monthDaySegmentLengths, spec.separator)
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
    val monthDayPlaceholder = remember(spec, dayTok, monthTok) {
        spec.monthDayOrder.joinToString(spec.separator.toString()) {
            when (it) {
                DateField.DAY -> dayTok
                DateField.MONTH -> monthTok
                DateField.YEAR -> ""
            }
        }
    }
    val invalidMsg = stringResource(R.string.person_birthday_invalid)
    val yearInvalidMsg = stringResource(R.string.person_date_year_invalid)
    val birthdayFutureMsg = stringResource(R.string.person_birthday_future)

    // v7.1.38 — mode « sans année » EXPLICITE par ligne (locales YMD UNIQUEMENT) ; à défaut,
    // dérivé de la valeur chargée (`--MM-dd` ⇒ sans année). Permet d'entrer en year-less même
    // champ vide. Inutilisé en locale année-en-dernier (l'année laissée vide suffit, zéro bouton).
    val yearLessModes = remember { mutableStateMapOf<String, Boolean>() }

    AccordionSection(
        title = stringResource(R.string.section_dates),
        leadingIcon = Icons.Default.Cake,
        initiallyExpanded = lines.any { it.value.isNotBlank() }
    ) {
        lines.forEach { line ->
            // En YMD (année en tête), le year-less doit être SIGNALÉ : par l'affordance, ou par une
            // valeur déjà `--MM-dd`. En locale année-en-dernier, jamais besoin (auto via 4 chiffres).
            val ymdYearLess = !spec.isYearLast &&
                (yearLessModes[line.id] ?: DateCanonical.isMonthDay(line.value))
            // Valeur AFFICHÉE = chiffres bruts (jamais les tirets ISO) : un `--MM-dd` stocké est
            // relu en jour/mois → le masque montre « 15/03 » proprement (fin du « --/03/-15 »).
            val fieldValue = if (DateCanonical.isMonthDay(line.value))
                monthDayToRawDigits(line.value, spec.monthDayOrder) else line.value
            val fieldMaxLen = if (ymdYearLess) monthDayLen else maxLen

            // v7.0.5 — validée DÈS qu'elle est non vide ; v7.1.38 — un year-less valide ne bloque pas.
            val isError = line.value.isNotBlank() && !isDateLineValid(line.value, spec, line.label)
            // Contexte year-less = mode YMD sans année, OU (année-en-dernier ET ≤ 4 chiffres) :
            // l'erreur éventuelle parle alors de « date invalide », pas d'« année à 4 chiffres ».
            val yearLessContext = ymdYearLess ||
                (spec.isYearLast && line.value.length <= monthDayLen)
            val errorMessage = when {
                yearLessContext -> invalidMsg
                line.value.length != maxLen -> yearInvalidMsg
                line.label == FieldTypes.DATE_BIRTHDAY -> birthdayFutureMsg
                else -> invalidMsg
            }
            DynamicLineRow(
                line = line,
                types = FieldTypes.DATE,
                valueLabel = stringResource(R.string.date_value_label),
                value = fieldValue,
                onValueChange = { input ->
                    val digits = input.filter { ch -> ch.isDigit() }.take(fieldMaxLen)
                    // YMD sans année : canonicalise en `--MM-dd` dès 4 chiffres valides (sinon
                    // chiffres bruts partiels) → la valeur PORTE l'intention « sans année » jusqu'au
                    // VM. Ailleurs : chiffres bruts (l'état terminal `--MM-dd`/ISO se fait au save).
                    val stored = if (ymdYearLess)
                        rawDigitsToMonthDay(digits, spec.monthDayOrder) ?: digits else digits
                    onLinesChange(lines.map { if (it.id == line.id) it.copy(value = stored) else it })
                },
                keyboardType = KeyboardType.Number,
                visualTransformation = if (ymdYearLess) monthDayTransformation else transformation,
                placeholder = if (ymdYearLess) monthDayPlaceholder else placeholder,
                isError = isError,
                errorMessage = errorMessage,
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
            // Affordance « sans année » — UNIQUEMENT en locale YMD (ja/zh/ko, année en tête) où
            // l'année vide ne se déduit pas du préfixe. Masquée dans les 10 autres langues.
            if (!spec.isYearLast) {
                YearLessChip(
                    checked = ymdYearLess,
                    onToggle = {
                        yearLessModes[line.id] = !ymdYearLess
                        // Ordres jour/mois ↔ complet incompatibles en YMD → repart d'un champ vide.
                        onLinesChange(lines.map { if (it.id == line.id) it.copy(value = "") else it })
                    }
                )
            }
            // Délai de rappel (v7.0) — visible UNIQUEMENT quand la cloche est active :
            // un rappel n'a de sens que pour une date qui notifie.
            if (line.notify) {
                ReminderRow(
                    offsetMinutes = line.reminderOffsetMinutes,
                    onOffsetChange = { newOffset ->
                        onLinesChange(lines.map {
                            if (it.id == line.id) it.copy(reminderOffsetMinutes = newOffset) else it
                        })
                    }
                )
            }
        }
        AddLineButton(stringResource(R.string.add_date)) {
            onLinesChange(lines + DynamicLine(label = FieldTypes.DATE_BIRTHDAY))
        }
    }
}

/**
 * v7.1.38 — petite affordance « sans année » affichée SOUS le champ date, UNIQUEMENT pour les
 * locales YMD (ja/zh/ko) où l'année est en tête : activée, elle bascule le champ en mode jour/mois
 * (date `--MM-dd`). Dans les 10 autres langues, « année laissée vide ⇒ sans année » suffit (pas de
 * chip). Une coche discrète indique l'état actif.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearLessChip(checked: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = checked,
        onClick = onToggle,
        label = { Text(stringResource(R.string.date_no_year)) },
        leadingIcon = if (checked) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
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

/** Champ de valeur simple (carte souple) — téléphones, emails, dates. */
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
    modifier: Modifier = Modifier
) {
    // Placeholder visible quand le champ est vide : masque de date si fourni, sinon
    // le libellé du champ (le libellé flottant est supprimé — habillage « carte souple »).
    val hint = placeholder ?: valueLabel

    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint) },
        singleLine = true,
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            { Text(errorMessage) }
        } else null,
        shape = RoundedCornerShape(16.dp),
        colors = softFieldColors(),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onImeNext() }),
        // Auto-scroll IME-synchronisé PARTAGÉ (v7.1.11) — source unique, plus de vide fantôme.
        modifier = modifier.bringIntoViewOnFocus()
    )
}

/**
 * Champ de valeur d'une RELATION (v7.1.6) : autocomplétion sur les CONTACTS — choisir une
 * suggestion ([onPick]) stocke l'id stable ; toute saisie manuelle ([onTextChange]) défait
 * le lien. Une coche discrète indique qu'un contact est bien identifié (linkedPersonId).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationValueField(
    value: String,
    linkedPersonId: String?,
    suggestions: List<PersonRef>,
    onPick: (PersonRef) -> Unit,
    onTextChange: (String) -> Unit,
    onImeNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hint = stringResource(R.string.common_name_label)
    var expanded by remember { mutableStateOf(false) }
    // Autocomplétion via le moteur de recherche CENTRAL (multi-mots, accent/casse-
    // insensible) : « Nathan Jamel », « jamel nathan » ou « nat » proposent le contact.
    val matches = remember(value, suggestions) {
        val tokens = value.searchTokens()
        if (tokens.isEmpty()) emptyList()
        else suggestions.filter {
            it.name.matchesAllTokens(tokens) && !it.name.equals(value.trim(), ignoreCase = true)
        }.take(5)
    }
    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = value,
            onValueChange = { onTextChange(it); expanded = true },
            placeholder = { Text(hint) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = softFieldColors(),
            trailingIcon = if (linkedPersonId != null) {
                {
                    Icon(Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.person_relation_linked_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onImeNext() }),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                // Auto-scroll IME-synchronisé PARTAGÉ (v7.1.11) — même source unique que les
                // autres champs mono-ligne ; plus de copie locale du bringIntoView one-shot.
                .bringIntoViewOnFocus()
        )
        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            matches.forEach { ref ->
                DropdownMenuItem(
                    text = { Text(ref.name) },
                    onClick = { onPick(ref); expanded = false },
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
        // v7.1.44 — hauteur PLAFONNÉE : à 26 entrées, le menu occupait toute la fenêtre et sa
        // dernière option (« Personnalisé ») finissait SOUS la barre de navigation, donc
        // intouchable. Plafonné, le popup est repositionné dans la zone sûre et défile.
        // Sans effet sur PHONE/EMAIL/DATE (listes plus courtes que le plafond).
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            // v7.1.44 — en-têtes de section : le catalogue des relations compte 26 entrées, une
            // liste plate n'y est plus lisible. L'en-tête n'est PAS un item (non cliquable) et
            // n'apparaît que si [TypeOption.groupRes] change ⇒ PHONE/EMAIL/DATE (groupRes null)
            // rendent exactement la même liste qu'avant.
            var lastGroup: Int? = null
            types.forEach { opt ->
                if (opt.groupRes != null && opt.groupRes != lastGroup) {
                    Text(
                        stringResource(opt.groupRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                }
                lastGroup = opt.groupRes
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

// ── Défilement auto de « plus / moins d'informations » (v7.1.58) ──────────────────────

/**
 * Plafond de la boucle de suivi du déploiement (v7.1.59), en NANOSECONDES réelles.
 *
 * Ce N'EST PAS une attente : le défilement commence à la PREMIÈRE image et la boucle sort dès
 * que la cible est atteinte (typiquement ~300 ms). Le plafond n'est qu'un garde-fou si le
 * contenu ne pousse jamais assez pour amener le bouton en haut.
 *
 * ⚠️ Borne en TEMPS et non en nombre d'images : le S21 monte à 120 Hz, un plafond en images y
 * vaudrait deux fois moins longtemps que sur un 60 Hz et pourrait couper le suivi avant la fin
 * du déploiement. Le temps, lui, ne dépend pas du taux de rafraîchissement.
 */
private const val FOLLOW_TIMEOUT_NANOS = 900_000_000L

/** Durée de la remontée à la fermeture. Assez court pour rester vif, assez long pour être doux. */
private const val CLOSE_DURATION_MS = 380

/**
 * Pilote le défilement du formulaire au basculement du bouton « plus / moins
 * d'informations » (v7.1.58). Obtenu par [rememberMoreInfoScroll], partagé tel quel par
 * [AddPersonScreen] et le mode édition de [PersonDetailScreen] — une seule logique.
 *
 * Trois points de branchement, tous passifs :
 *  - [viewportModifier] sur le conteneur défilant, **AVANT** `verticalScroll` (le nœud est
 *    alors HORS du défilement → il mesure le VIEWPORT, pas le contenu qui glisse) ;
 *  - [onToggleTopInRoot] et [onExpandedChange] passés à [ProfileFormFields] — le bouton,
 *    lui, défile, d'où la mesure à chaque passe de layout.
 *
 * La cible d'ouverture est **le bouton amené en haut du viewport** : le bloc déplié occupe
 * alors tout l'espace en dessous. Cible STABLE — elle ne dépend pas de la hauteur dépliée,
 * contrairement à `maxValue` qui est encore périmé à l'instant du clic.
 */
@Stable
internal class MoreInfoScrollState(private val scrollState: ScrollState) {

    /** Vrai quand le bloc supplémentaire est déplié. Miroir de `showMore`, jamais sa source. */
    var expanded by mutableStateOf(false)
        private set

    // Sans ce drapeau, le LaunchedEffect s'exécuterait à la PREMIÈRE composition (expanded =
    // false) et défilerait en haut à l'ouverture de l'écran, ou en entrant en édition.
    private var userToggled = false

    private var viewportTopInRoot by mutableFloatStateOf(0f)
    private var toggleTopInRoot by mutableFloatStateOf(0f)

    val viewportModifier: Modifier =
        Modifier.onGloballyPositioned { viewportTopInRoot = it.positionInRoot().y }

    val onToggleTopInRoot: (Float) -> Unit = { toggleTopInRoot = it }

    val onExpandedChange: (Boolean) -> Unit = { userToggled = true; expanded = it }

    /**
     * Remet le pilote au repos — à appeler quand [ProfileFormFields] QUITTE la composition
     * (sortie du mode édition) : son `showMore` local repart à false, l'écran doit suivre,
     * sinon le prochain basculement serait interprété à l'envers.
     */
    fun reset() {
        userToggled = false
        expanded = false
    }

    /** Offset de contenu qui amène le bouton bascule en haut du viewport. */
    private fun toggleOffsetInContent(): Int =
        (scrollState.value + (toggleTopInRoot - viewportTopInRoot))
            .roundToInt()
            .coerceAtLeast(0)

    /**
     * Attend que `AnimatedVisibility` ait fini de poser la hauteur du bloc déplié, en
     * observant la stabilisation de `maxValue` image par image.
     *
     * ⚠️ Le déclencheur reste le BASCULEMENT SEUL. La boucle ne vit que le temps du déploiement
     * (sortie dès la cible atteinte, plafond [FOLLOW_TIMEOUT_NANOS]) : passé ce court instant,
     * plus rien n'observe la hauteur, donc éditer un champ ou ouvrir un accordéon ne défile
     * jamais.
     */
    private suspend fun followExpansion() {
        var startNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (startNanos == 0L) startNanos = frameNanos
            val target = toggleOffsetInContent()
            // scrollTo INSTANTANÉ, mais rejoué à chaque image : la douceur ne vient pas d'une
            // courbe d'animation, elle vient du DÉPLOIEMENT lui-même. `scrollTo` est borné par
            // `maxValue` ; tant que le bloc grandit, on avance d'exactement ce que la nouvelle
            // hauteur autorise → le bouton glisse vers le haut au rythme des champs qui
            // apparaissent. Synchronisation EXACTE par construction : une seule motion.
            scrollState.scrollTo(target)
            // Cible atteinte (plus rien ne bride le défilement) → le contenu a fini de pousser.
            if (scrollState.value >= target) return
            if (frameNanos - startNanos > FOLLOW_TIMEOUT_NANOS) return
        }
    }

    internal suspend fun animateOnToggle() {
        if (!userToggled) return
        if (!expanded) {
            // Fermeture : retour au profil principal. Cible sans ambiguïté — un
            // BringIntoViewRequester ne pourrait rien viser, le bloc replié a une hauteur nulle.
            // Spec explicite : le ressort par défaut d'animateScrollTo part trop sec sur une
            // longue remontée. Un tween court en FastOutSlowInEasing démarre franchement puis
            // décélère — la fiche « se repose » en haut au lieu de s'y cogner.
            scrollState.animateScrollTo(
                0,
                animationSpec = tween(CLOSE_DURATION_MS, easing = FastOutSlowInEasing)
            )
            return
        }
        followExpansion()
    }
}

/**
 * Crée le pilote et branche l'unique effet de défilement, keyé sur le seul état déplié —
 * donc déclenché au BASCULEMENT et à rien d'autre.
 */
@Composable
internal fun rememberMoreInfoScroll(scrollState: ScrollState): MoreInfoScrollState {
    val state = remember(scrollState) { MoreInfoScrollState(scrollState) }
    LaunchedEffect(state.expanded) { state.animateOnToggle() }
    return state
}

/**
 * Ramène le champ porteur dans la zone visible (au-dessus du clavier) à la prise
 * de focus. Appliqué aux champs du bas du formulaire (Origine, Ville, Pro) ET, depuis
 * v7.1.1, aux sections de notes ([NoteSectionsEditor], même package) qui, sinon,
 * restaient masqués par l'IME selon l'ordre d'ouverture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    var isFocused by remember { mutableStateOf(false) }
    // SOURCE UNIQUE du recentrage clavier (v7.1.11) — motif IME-SYNCHRONISÉ (généralisé depuis
    // la note v7.1.3). Tant que le champ est focalisé, on rejoue bringIntoView à CHAQUE palier de
    // l'inset clavier : l'émission INITIALE de snapshotFlow couvre « clavier déjà ouvert » (focus
    // déplacé via Suivant), le flux ANIMÉ couvre « clavier qui monte ». collectLatest annule le
    // bringIntoView animé en cours à chaque nouvelle valeur d'inset → le champ « monte avec » le
    // clavier et se cale juste au-dessus. Plus de scroll one-shot PÉRIMÉ tiré au focus avant la fin
    // de l'animation (cause du vide fantôme position-dépendant). Spec ANIMÉ par défaut (jamais snap).
    // ⚠️ Amène TOUT le champ dans la vue → réservé aux champs MONO-LIGNE ; le contenu multi-ligne
    // d'une note garde sa spécialisation (bande basse/curseur) dans NoteContentField.
    LaunchedEffect(isFocused) {
        if (!isFocused) return@LaunchedEffect
        snapshotFlow { imeInsets.getBottom(density) }
            .distinctUntilChanged()
            .collectLatest { requester.bringIntoView() }
    }
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { isFocused = it.isFocused }
}

/** Affordance d'ajout discrète : « + » + court libellé (v7.0.2). */
@Composable
private fun AddLineButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
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
 * de chaque composant dans l'ordre courant (ex. [2,2,4], [4,2,2] ou [2,2] sans année).
 * L'[OffsetMapping] garde le curseur cohérent, y compris au backspace.
 *
 * v7.1.38 — masque ADAPTATIF à la longueur saisie : un séparateur n'est inséré à une
 * frontière que si un chiffre la suit RÉELLEMENT → aucun séparateur TRAÎNANT (« 15/03 »
 * et non « 15/03/ »). Le même masque rend donc proprement une date sans année (4 chiffres)
 * comme une date complète, sans cas particulier.
 */
private class DateMaskVisualTransformation(
    segmentLengths: List<Int>,
    private val separator: Char
) : VisualTransformation {

    // Frontières (en index original) après lesquelles un séparateur PEUT être inséré.
    private val sepAfter: List<Int> =
        segmentLengths.runningReduce { acc, n -> acc + n }.dropLast(1)

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        // Séparateurs RÉELLEMENT insérés = frontières strictement suivies d'un chiffre.
        val activeSep = sepAfter.filter { it < digits.length }
        val sb = StringBuilder(digits.length + activeSep.size)
        for (i in digits.indices) {
            sb.append(digits[i])
            if ((i + 1) in activeSep) sb.append(separator)
        }

        // Positions des séparateurs actifs dans le texte transformé (k-ième sep ⇒ +k).
        val sepTransformedPos = activeSep.mapIndexed { i, s -> s + i }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + activeSep.count { it <= offset }

            override fun transformedToOriginal(offset: Int): Int =
                offset - sepTransformedPos.count { offset > it }
        }
        return TransformedText(AnnotatedString(sb.toString()), mapping)
    }
}
