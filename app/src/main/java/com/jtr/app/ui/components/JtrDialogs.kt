package com.jtr.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

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
 * Couleurs du bouton d'ANNULATION, communes aux deux dialogues.
 *
 * v7.1.130 — un `TextButton` nu prend `primary`, et sur une palette chaude (Corail) ce
 * primaire est un rouge brique que rien ne distingue du `error` de la confirmation : les
 * deux boutons paraissaient destructifs et le rouge ne voulait plus rien dire. Annuler est
 * l'action SÛRE : elle se tait, en `onSurfaceVariant`. Même raison qu'au « ⋮ » d'une fiche
 * (v7.1.129), appliquée ici une seule fois pour les trente dialogues.
 */
@Composable
private fun jtrDismissButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

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
 *   `Text(color = error)` de l'ancien code ne faisait pas. Il teint AUSSI l'icône, et
 *   c'est elle qui porte réellement la distinction : sur une palette chaude (Corail),
 *   `primary` et `error` sont deux rouges brique que rien ne sépare, donc un confirm
 *   « rouge » n'informe plus. Une icône NON destructive se tait donc en
 *   `onSurfaceVariant` (v7.1.131) : icône rouge = destructif, icône neutre = non.
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
                    else MaterialTheme.colorScheme.onSurfaceVariant
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
                TextButton(
                    onClick = onDismiss ?: onDismissRequest,
                    colors = jtrDismissButtonColors()
                ) { Text(label) }
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
 * @param leadingIcon icône de tête du champ, relayée au [FormTextField] (v7.1.133) : le
 *   dialogue « + lien » portait un maillon, et un socle qui l'avalerait ne serait plus un
 *   socle mais un rabot.
 * @param supportingContent bloc affiché SOUS le champ (p. ex. l'aperçu d'une plateforme
 *   détectée à partir d'une URL). Il REÇOIT la valeur courante (v7.1.133) : un aperçu
 *   dérivé de la saisie ne peut pas se contenter d'être rendu « quelque part sous » le
 *   champ, il doit lire ce qui vient d'être tapé.
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
    leadingIcon: ImageVector? = null,
    supportingContent: (@Composable ColumnScope.(value: String) -> Unit)? = null
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
                    leadingIcon = leadingIcon,
                    singleLine = singleLine,
                    modifier = Modifier.fillMaxWidth()
                )
                supportingContent?.invoke(this, field.text)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(field.text) },
                enabled = validate(field.text)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = jtrDismissButtonColors()
            ) { Text(dismissLabel) }
        }
    )
}


/**
 * Dialogue de PROGRESSION (v7.1.135) — un travail en cours, AUCUNE action offerte.
 *
 * Le dernier `AlertDialog` nu du module était celui-là : un dialogue à `confirmButton = {}`,
 * c'est-à-dire un dialogue dont le seul slot obligatoire du composant M3 est rempli par du
 * vide. Ce n'est pas une confirmation sans bouton, c'est un autre objet — d'où un composant
 * à lui, plutôt qu'un paramètre de plus sur [JtrAlertDialog].
 *
 * **Non annulable, et c'est le propos** : fermer en plein scan laisserait un plan fantôme et
 * rien n'est interruptible. L'ancien code s'en remettait à un `onDismissRequest` VIDE (le
 * rappel partait, ne faisait rien, le dialogue restait) ; on le dit désormais au conteneur
 * lui-même via [DialogProperties] — même résultat à l'écran, mais l'intention est lisible
 * au lieu d'être déduite d'une lambda vide.
 *
 * @param message texte affiché À CÔTÉ du cercle de progression (l'étape en cours) ; `null`
 *   laisse le cercle seul.
 */
@Composable
fun JtrProgressDialog(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier,
        properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = icon?.let {
            { Icon(it, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                if (message != null) {
                    Spacer(Modifier.width(16.dp))
                    Text(message)
                }
            }
        },
        confirmButton = {}
    )
}
