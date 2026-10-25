package com.jtr.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

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
        modifier = modifier
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
        modifier = modifier
    )
}
