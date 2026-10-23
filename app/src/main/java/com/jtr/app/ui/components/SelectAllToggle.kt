package com.jtr.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtr.app.R

/**
 * BASCULE « tout / rien » des en-têtes de sélection (v7.1.98) — composant PARTAGÉ par
 * l'Accueil, Catégories, le détail de dossier et la galerie.
 *
 * Avant, chacun des quatre écrans posait sa propre icône « tout sélectionner », qui ne savait
 * que COCHER : il n'existait aucun moyen de tout décocher sans quitter la sélection, et rien
 * n'indiquait ce que l'icône faisait. Ici, un seul composant porte les deux : l'icône reflète
 * l'état (tout coché ou non) et le tap BASCULE — on coche tout, on retape, on décoche tout.
 *
 * Le libellé « All » accompagne l'icône parce qu'un pictogramme seul ne dit pas son action.
 * ACCESSIBILITÉ : le nom du nœud est ce libellé visible, et c'est `onClickLabel` qui annonce
 * ce que le tap va FAIRE (« tout sélectionner » / « tout désélectionner ») — l'idiome correct
 * pour une action qui change de sens. ⚠️ Pas de `semantics {}` ici : sur une Row cliquable il
 * créerait un nœud intérieur qui bloque la fusion et rendrait le bouton anonyme (v7.1.95).
 *
 * PÉRIMÈTRE : la bascule porte sur ce que l'écran AFFICHE. Quand une recherche filtre la
 * liste, cocher n'ajoute que le visible — et décocher ne retire que le visible, sinon un
 * « tout décocher » sous une requête emporterait les coches faites sous une autre. Les deux
 * callbacks doivent donc être symétriques ; c'est l'écran qui fournit ce calcul.
 *
 * @param allSelected vrai quand tout le VISIBLE est déjà coché — il donne le sens de la bascule.
 */
@Composable
internal fun SelectAllToggle(
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionLabel = stringResource(
        if (allSelected) R.string.select_none else R.string.select_all
    )
    Row(
        modifier = modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = actionLabel) {
                if (allSelected) onDeselectAll() else onSelectAll()
            }
            // Cible tactile confortable, bien au-delà des 48 dp requis une fois le libellé
            // compté ; la hauteur, elle, est posée explicitement.
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
            // Muette : le libellé visible ci-contre nomme déjà le bouton, et `onClickLabel`
            // dit l'action. Une description ici ne ferait que doubler l'annonce.
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = stringResource(R.string.select_all_short),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
