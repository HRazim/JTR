package com.jtr.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
