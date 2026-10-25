package com.jtr.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * STYLE COMMUN des deux surcharges (v7.1.114) — le seul endroit où la forme et la bordure
 * des champs de TOUTE l'app se règlent :
 *  - [FormFieldShape] : coins à 16 dp — le rayon de l'ancien « champ carte souple »
 *    (v7.0.2) dont HR aimait la rondeur, appliqué au style outlined (le défaut M3 à ~4 dp
 *    rendait les champs « carrés/rigides ») ;
 *  - [formFieldColors] : bordure NON focalisée en `outlineVariant` — en sombre, `outline`
 *    (le défaut M3) est un gris si clair qu'il se lisait comme « un gros trait blanc »
 *    à côté des icônes leading pleines. `outlineVariant` reste un repère discret dans les
 *    deux modes ; l'état FOCALISÉ garde la bordure primaire 2 dp (le repère de focus).
 *    Couleurs du THÈME uniquement, jamais codées en dur.
 */
private val FormFieldShape = RoundedCornerShape(16.dp)

// REMPLISSAGE (correctif v7.1.114) : le container par défaut d'un OutlinedTextField est
// TRANSPARENT — l'intérieur du champ prenait le fond de l'écran et se lisait « blanc ».
// On remet la teinte de l'ancien champ souple que HR voulait : `surfaceVariant`, la même
// couleur de thème qu'avant v7.1.112, un ton à peine plus soutenu que le fond — seul le
// contour tranche, l'intérieur reste « couleur de l'appli ».
@Composable
private fun formFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
)

/**
 * CHAMP TEXTE de formulaire (v7.1.112, G3b-1) — le style v7.1.79 en un seul moteur, à côté
 * de [FormPhotoHeader] : [OutlinedTextField] à **label flottant**, couleurs **du thème**
 * (aucune surcharge : exactement le rendu des formulaires de catégorie/groupe, qui s'y
 * consolident en net-zéro), état d'erreur et texte d'appui M3.
 *
 * Il remplace le « champ carte souple » du formulaire de contact (`SoftTextField`, v7.0.2 —
 * TextField rempli sans soulignement, libellé en placeholder) : l'API est alignée pour une
 * substitution mécanique — [label] reprend l'ancien placeholder et devient flottant ;
 * [placeholder] reste disponible EN PLUS pour un indice de saisie distinct du libellé
 * (le masque de date des lignes dynamiques). Toute la logique (valeur, validation,
 * clavier, `bringIntoViewOnFocus` via [modifier]) appartient à l'appelant, inchangée.
 */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = FormFieldShape,
        colors = formFieldColors(),
        // v7.1.115 — TOUT champ simple remonte au-dessus du clavier à la prise de focus
        // (le Nom et ses sous-champs ne le faisaient pas : seuls quelques champs du bas
        // portaient le modifier à la main). Intégré ICI, c'est désormais une garantie du
        // composant. Dans un dialogue/feuille non défilant (catégorie/groupe), c'est un
        // no-op inoffensif — la feuille remonte déjà par son imePadding.
        modifier = modifier.bringIntoViewOnFocus()
    )
}

/**
 * Surcharge **`TextFieldValue`** (v7.1.113, G3b-2a) — pour les champs qui pilotent la
 * SÉLECTION en plus du texte : l'éditeur de notes fonde sa garde « curseur en fin »
 * (auto-scroll) dessus. Même rendu que la surcharge `String` par CONSTRUCTION : aucune
 * des deux ne personnalise quoi que ce soit, le style outlined EST le défaut M3 du thème —
 * un seul moteur, deux signatures.
 *
 * [label] est ici OPTIONNEL : `null` = pas de label flottant, le [placeholder] seul fait
 * l'invite (le champ de notes n'a pas de libellé propre — son titre est sa 1ʳᵉ ligne).
 * Le [modifier] est passé TEL QUEL : c'est lui qui porte l'auto-grow (`onSizeChanged`),
 * le `bringIntoViewRequester` et le focus de l'appelant ; rien n'est imposé ici (ni
 * hauteur, ni scroll interne) qui casserait cette mécanique.
 */
@Composable
internal fun FormTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = FormFieldShape,
        colors = formFieldColors(),
        modifier = modifier
    )
}

/**
 * Ramène le champ porteur dans la zone visible (au-dessus du clavier) à la prise de focus —
 * SOURCE UNIQUE du recentrage clavier (v7.1.11), DÉPLACÉE ici depuis `ProfileFormFields` en
 * v7.1.115 : la surcharge `String` de [FormTextField] l'applique d'office, plus aucun champ
 * simple ne peut rester caché derrière l'IME par oubli du modifier.
 *
 * Motif IME-SYNCHRONISÉ : tant que le champ est focalisé, `bringIntoView` est rejoué à
 * CHAQUE palier de l'inset clavier — l'émission initiale de `snapshotFlow` couvre « clavier
 * déjà ouvert » (focus déplacé via Suivant), le flux animé couvre « clavier qui monte » ;
 * `collectLatest` annule l'animation en cours à chaque palier, le champ « monte avec » le
 * clavier et se cale juste au-dessus (jamais de scroll one-shot périmé → pas de vide
 * fantôme). ⚠️ Amène TOUT le champ dans la vue → réservé aux champs MONO-LIGNE : la
 * surcharge `TextFieldValue` (notes multi-lignes) garde sa spécialisation bande-basse/
 * curseur dans `NoteContentField` et n'applique PAS ce modifier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    var isFocused by remember { mutableStateOf(false) }
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
