package com.jtr.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * SOCLE DE DIALOGUE PARTAGÉ (v7.1.128, lot L4).
 *
 * L'app comptait 30 [AlertDialog] écrits à la main, sans aucun composant commun : chacun
 * re-déclarait ses deux `TextButton`, et la mise en rouge d'une action destructive se
 * faisait de DEUX façons différentes selon le fichier (`textButtonColors(error)` ici,
 * `Text(color = error)` là). Ces deux fonctions fixent la grammaire une fois pour toutes.
 *
 * Ce que le socle N'IMPOSE PAS, volontairement : ni `shape`, ni `containerColor`, ni
 * élévation. Un dialogue Material 3 a déjà la forme et les couleurs du thème ; les forcer
 * ici ne ferait que figer un jour ce que le thème sait déjà faire — et c'est précisément
 * ce qui a produit les six rayons différents qu'on trouve ailleurs dans le module.
 */

/**
 * Dialogue à boutons : confirmation, information, action destructive.
 *
 * @param text corps SIMPLE. Pour un corps riche (listes, options, paires libellé/valeur),
 *   passer [content] : il remplace alors `text` dans le slot de corps.
 * @param dismissLabel `null` ⇒ dialogue à UN seul bouton (cas « information »).
 * @param onDismiss action du bouton d'annulation ; à défaut, [onDismissRequest] est rejoué
 *   (un dialogue dont « Annuler » et le tap-dehors divergent serait un piège).
 * @param destructive colore la confirmation en `error` — le SEUL idiome destructif retenu,
 *   via `textButtonColors` : il teint aussi l'état pressé et l'état désactivé, ce que le
 *   `Text(color = error)` de l'ancien code ne faisait pas.
 */
@Composable
fun JtrAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon?.let {
            {
                Icon(
                    it, contentDescription = null,
                    tint = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        title = { Text(title) },
        text = when {
            content != null -> { { Column(content = content) } }
            text != null -> { { Text(text) } }
            else -> null
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.textButtonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                TextButton(onClick = onDismiss ?: onDismissRequest) { Text(label) }
            }
        }
    )
}

/**
 * Dialogue à UN champ texte : renommer, saisir un libellé personnalisé…
 *
 * Le champ est le [FormTextField] du formulaire (coins 16 dp, `surfaceVariant`, bordure
 * `outlineVariant`) et non un [androidx.compose.material3.OutlinedTextField] nu : les deux
 * dialogues migrés ici portaient chacun leur propre champ à 12 dp, dernier reste du
 * langage d'avant le Groupe 3.
 *
 * La saisie est un [TextFieldValue] et non une simple `String` : c'est la surcharge qui
 * accepte un libellé NUL, et elle permet de poser le curseur EN FIN de valeur initiale —
 * sur un renommage, on continue le mot, on ne se retrouve pas avant la première lettre.
 *
 * @param validate la confirmation est désactivée tant qu'elle renvoie `false`.
 * @param supportingContent bloc affiché SOUS le champ (p. ex. l'aperçu d'une plateforme
 *   détectée à partir d'une URL).
 */
@Composable
fun JtrPromptDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    initialValue: String = "",
    placeholder: String? = null,
    validate: (String) -> Boolean = { it.isNotBlank() },
    singleLine: Boolean = true,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    var field by remember {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column {
                FormTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = label,
                    placeholder = placeholder,
                    singleLine = singleLine,
                    modifier = Modifier.fillMaxWidth()
                )
                supportingContent?.invoke(this)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(field.text) },
                enabled = validate(field.text)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(dismissLabel) }
        }
    )
}
