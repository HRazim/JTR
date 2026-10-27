package com.jtr.app.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.components.FormPhotoHeader
import com.jtr.app.ui.components.FormTextField
import com.jtr.app.ui.person.CropShape
import com.jtr.app.ui.person.PickerThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v7.1.82 — création d'un DOSSIER (groupe) en FEUILLE MODERNE, calquée sur le formulaire
 * de catégorie v7.1.79 que le dialogue nu (`TextPromptDialog` : un champ, deux boutons)
 * ne permettait pas :
 *
 * - **Aperçu vivant** 96 dp : l'image de couverture choisie, sinon l'initiale du nom sur
 *   teinte douce + anneau d'accent (langage v7.1.77). Le dossier n'a pas de couleur
 *   stockée ([com.jtr.app.domain.model.CategoryGroup]) → l'accent vient du THÈME.
 * - **Image de couverture dès la création** : badge caméra + bouton explicite, via la
 *   chaîne existante (Photo Picker → recadrage → `filesDir/photos`). `imagePath` existe
 *   depuis la DB v15 : aucune migration.
 * - **Ce qu'on groupe est VISIBLE** : « N sélectionnée(s) » + rangée des vignettes des
 *   catégories concernées ([PickerThumbnail], réutilisée telle quelle).
 * - **Bouton « Créer » pleine largeur ÉPINGLÉ** au-dessus du clavier (même grammaire
 *   qu'en v7.1.79 : zone défilante `weight(1f, fill = false)` + bouton hors de celle-ci,
 *   l'`imePadding` étant porté par la colonne parente).
 *
 * Présentation pure (UDF) : l'appelant persiste via `createGroup`/`createSubGroup`.
 *
 * v7.1.127 — cette feuille sert MAINTENANT aussi l'ÉDITION, exactement comme
 * `AddCategoryDialog`/`EditCategoryDialog` partagent `CategoryFormDialog` : deux façades
 * ([CreateGroupSheet], [EditGroupSheet]) sur un seul corps paramétré, pour que renommer un
 * dossier et changer son image passent par le MÊME formulaire que sa création.
 */
@Composable
fun CreateGroupSheet(
    members: List<Category>,
    // v7.1.84 — dossiers existants + parent initial, pour choisir où naît le groupe.
    groups: List<CategoryGroup> = emptyList(),
    initialParentId: Long? = null,
    onConfirm: (name: String, imagePath: String?, parentGroupId: Long?) -> Unit,
    onDismiss: () -> Unit
) = GroupFormSheet(
    title = stringResource(R.string.categories_create_group),
    initialName = "",
    initialImagePath = null,
    members = members,
    groups = groups,
    initialParentId = initialParentId,
    showParentField = true,
    autoFocusName = true,
    confirmLabel = stringResource(R.string.common_create),
    onConfirm = onConfirm,
    onDismiss = onDismiss
)

/**
 * v7.1.127 — ÉDITION d'un dossier : nom + image, dans la feuille de création. Remplace le
 * couple « Renommer » (mini-dialogue d'un champ) + « Changer l'image » (sélecteur photo
 * direct), qui obligeait à deux chemins pour une seule intention.
 *
 * PAS de sélecteur de dossier parent ici : déplacer reste l'action « Grouper / Déplacer »
 * du footer, qui sait exclure les cibles créant un cycle — ce que ce formulaire ne saurait
 * pas faire. Et pas d'auto-focus : on ouvre souvent pour changer l'image, pas le nom.
 */
@Composable
fun EditGroupSheet(
    group: CategoryGroup,
    onConfirm: (name: String, imagePath: String?) -> Unit,
    onDismiss: () -> Unit
) = GroupFormSheet(
    title = stringResource(R.string.common_edit),
    initialName = group.name,
    initialImagePath = group.imagePath,
    members = emptyList(),
    groups = emptyList(),
    initialParentId = null,
    showParentField = false,
    autoFocusName = false,
    confirmLabel = stringResource(R.string.common_save),
    onConfirm = { name, imagePath, _ -> onConfirm(name, imagePath) },
    onDismiss = onDismiss
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFormSheet(
    title: String,
    initialName: String,
    initialImagePath: String?,
    members: List<Category>,
    groups: List<CategoryGroup>,
    initialParentId: Long?,
    showParentField: Boolean,
    autoFocusName: Boolean,
    confirmLabel: String,
    onConfirm: (name: String, imagePath: String?, parentGroupId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialName) }
    var imagePath by remember { mutableStateOf(initialImagePath) }
    // v7.1.84 — dossier PARENT : null = racine. Pré-rempli avec le dossier courant quand
    // on crée depuis l'intérieur d'un dossier (sous-groupe), modifiable dans les deux cas.
    var parentId by remember { mutableStateOf(initialParentId) }
    var showParentPicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Un dossier n'a pas de couleur stockée : l'accent de l'aperçu vient du thème.
    val accent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                // ── Aperçu vivant du dossier (v7.1.79, extrait en v7.1.111) : le composant
                // partagé porte l'aperçu, le badge, le zoom (Remplacer/Retirer) et le crop.
                FormPhotoHeader(
                    photo = imagePath,
                    accent = accent,
                    fallbackIcon = Icons.Default.Folder,
                    photoContentDescription = stringResource(R.string.categories_image_cd),
                    cropShape = CropShape.RECTANGLE,
                    onPhotoCropped = { croppedUri ->
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                copyCategoryPhotoToStorage(context, croppedUri)
                            }
                            if (path != null) imagePath = path
                        }
                    },
                    onPhotoRemoved = { imagePath = null },
                    fallbackInitialFrom = name
                )
                Spacer(Modifier.height(8.dp))

                val focusRequester = remember { FocusRequester() }
                // v7.1.112 — champ consolidé sur FormTextField (même rendu : il EST la
                // version factorisée de cet OutlinedTextField).
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.categories_group_name_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                // v7.1.127 — auto-focus en CRÉATION seulement (règle du Groupe 3) : en
                // édition, le champ est déjà rempli et l'intention est souvent l'image.
                if (autoFocusName) LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Spacer(Modifier.height(16.dp))

                // ── Dossier PARENT : on crée directement AU BON ENDROIT (plus besoin de
                // créer à la racine puis de déplacer). « Racine » = premier niveau.
                if (showParentField) {
                    val parentName =
                        parentId?.let { id -> groups.firstOrNull { it.id == id }?.name }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showParentPicker = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder, contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.categories_parent_group),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                parentName ?: stringResource(R.string.categories_parent_root),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── CE QU'ON GROUPE : compte + vignettes (on voit ce qui entre dans le
                // dossier avant de valider — le dialogue historique ne le montrait pas).
                // Rien à montrer en ÉDITION, où la feuille ne groupe personne.
                if (members.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            R.plurals.categories_selection_count, members.size, members.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { category ->
                            PickerThumbnail(
                                imagePath = category.imagePath,
                                name = category.name,
                                colorHex = category.color
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Barre d'action FIXE : reste tappable clavier ouvert (focus auto sur le nom).
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), imagePath, parentId) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) { Text(confirmLabel) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showParentPicker) {
        // Réutilise le sélecteur de dossiers de v7.1.83 (vignettes, hiérarchie, recherche)
        // + l'option « Racine ». Le groupe n'existant pas encore, aucune cible à exclure
        // (pas de cycle possible à la création).
        CategoryPickerSheet(
            title = stringResource(R.string.categories_parent_group),
            categories = emptyList(),
            groups = groups,
            folderTargets = true,
            rootOption = true,
            onPickFolder = { id -> parentId = id; showParentPicker = false },
            onPickRoot = { parentId = null; showParentPicker = false },
            onDismiss = { showParentPicker = false }
        )
    }
}
