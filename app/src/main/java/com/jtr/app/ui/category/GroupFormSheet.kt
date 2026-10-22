package com.jtr.app.ui.category

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.components.PhotoZoomDialog
import com.jtr.app.ui.components.rememberSystemPhotoPicker
import com.jtr.app.ui.person.CropShape
import com.jtr.app.ui.person.ImageCropDialog
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
 * Le renommage et le nommage d'un groupe né du geste de FUSION gardent le dialogue
 * historique (hors périmètre de cette V1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupSheet(
    members: List<Category>,
    // v7.1.84 — dossiers existants + parent initial, pour choisir où naît le groupe.
    groups: List<CategoryGroup> = emptyList(),
    initialParentId: Long? = null,
    onConfirm: (name: String, imagePath: String?, parentGroupId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf<String?>(null) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showImageZoom by remember { mutableStateOf(false) }
    // v7.1.84 — dossier PARENT : null = racine. Pré-rempli avec le dossier courant quand
    // on crée depuis l'intérieur d'un dossier (sous-groupe), modifiable dans les deux cas.
    var parentId by remember { mutableStateOf(initialParentId) }
    var showParentPicker by remember { mutableStateOf(false) }

    // Photo Picker système (v7.1.66) — aucune permission requise.
    val photoPicker = rememberSystemPhotoPicker { uri -> pendingCropUri = uri }

    // Visionneuse (Remplacer / Retirer) empilée au-dessus de la feuille : les `remember`
    // ci-dessus appartiennent à CE composable, le brouillon survit donc à sa fermeture.
    if (showImageZoom) {
        imagePath?.let { current ->
            PhotoZoomDialog(
                photoUri = current,
                onDismiss = { showImageZoom = false },
                // On ferme AVANT d'ouvrir le picker : sinon le recadrage s'empilerait
                // en 3e dialogue au-dessus d'elle (v7.1.49).
                onReplace = { showImageZoom = false; photoPicker() },
                onRemove = { showImageZoom = false; imagePath = null }
            )
        }
    }

    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropShape = CropShape.RECTANGLE,
            onCropComplete = { croppedUri ->
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        copyCategoryPhotoToStorage(context, croppedUri)
                    }
                    if (path != null) imagePath = path
                }
                pendingCropUri = null
            },
            onDismiss = { pendingCropUri = null }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Un dossier n'a pas de couleur stockée : l'accent de l'aperçu vient du thème.
    val accent = MaterialTheme.colorScheme.primary
    val openPhoto = { if (imagePath != null) showImageZoom = true else photoPicker() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    stringResource(R.string.categories_create_group),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))

                // ── Aperçu vivant du dossier (image, sinon initiale + anneau).
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .border(2.dp, accent.copy(alpha = 0.55f), CircleShape)
                            .background(accent.copy(alpha = 0.16f))
                            .clickable { openPhoto() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imagePath != null) {
                            AsyncImage(
                                model = imagePath,
                                contentDescription = stringResource(R.string.categories_image_cd),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // codePointAt : initiale emoji/arabe (surrogate pair) entière.
                            val initial = name.trim().let {
                                if (it.isEmpty()) ""
                                else String(Character.toChars(it.codePointAt(0))).uppercase()
                            }
                            if (initial.isEmpty()) {
                                Icon(
                                    Icons.Default.Folder, contentDescription = null,
                                    modifier = Modifier.size(36.dp), tint = accent
                                )
                            } else {
                                Text(
                                    initial,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { openPhoto() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (imagePath != null) Icons.Default.Edit else Icons.Default.AddAPhoto,
                            contentDescription = stringResource(R.string.common_photo),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                TextButton(
                    onClick = { openPhoto() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(stringResource(R.string.common_photo)) }
                Spacer(Modifier.height(8.dp))

                val focusRequester = remember { FocusRequester() }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_group_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Spacer(Modifier.height(16.dp))

                // ── Dossier PARENT : on crée directement AU BON ENDROIT (plus besoin de
                // créer à la racine puis de déplacer). « Racine » = premier niveau.
                val parentName = parentId?.let { id -> groups.firstOrNull { it.id == id }?.name }
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

                // ── CE QU'ON GROUPE : compte + vignettes (on voit ce qui entre dans le
                // dossier avant de valider — le dialogue historique ne le montrait pas).
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
                Spacer(Modifier.height(16.dp))
            }

            // Barre d'action FIXE : reste tappable clavier ouvert (focus auto sur le nom).
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), imagePath, parentId) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.common_create)) }
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
