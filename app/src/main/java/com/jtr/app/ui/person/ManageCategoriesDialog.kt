package com.jtr.app.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.category.AddCategoryDialog
import com.jtr.app.ui.category.rememberCategoryColor
import com.jtr.app.ui.components.FavoriteStar
import com.jtr.app.utils.matchesAllTokens
import com.jtr.app.utils.searchTokens

/**
 * Une ligne du sélecteur de catégories, à plat avec son niveau d'indentation pour
 * refléter la hiérarchie récursive (dossiers → sous-dossiers → catégories).
 *
 * v7.1.80 — `internal` (ainsi que le moteur et les lignes ci-dessous) : le MÊME langage
 * sert désormais au sélecteur de cible du « Move » et au rattachement à un dossier
 * ([com.jtr.app.ui.category.CategoryPickerSheet]) — réutilisation, pas de fork.
 */
internal sealed interface CategoryPickerRow {
    val indent: Int

    /**
     * En-tête de dossier (NON cochable : une personne appartient à des catégories, pas à un
     * dossier) mais REPLIABLE depuis v7.1.54 : le tap bascule [expanded]. [hasChildren] est
     * faux pour un dossier vide → ni chevron ni clic (rien à déplier).
     */
    data class Folder(
        val group: CategoryGroup,
        override val indent: Int,
        val expanded: Boolean,
        val hasChildren: Boolean
    ) : CategoryPickerRow

    /** Catégorie cochable. */
    data class Item(val category: Category, override val indent: Int) : CategoryPickerRow
}

/**
 * Aplatit catégories + dossiers en lignes ordonnées et indentées, dans les MÊMES
 * sémantiques d'appartenance que l'écran Catégories : dossiers de premier niveau (avec
 * leurs catégories puis leurs sous-dossiers, récursivement), puis catégories libres.
 *
 * v7.1.50 — [query] non vide ⇒ **liste PLATE filtrée** (ni en-tête de dossier ni indentation),
 * comme l'écran Catégories : pendant une recherche, l'arbre n'apporte rien et fragmenterait
 * les résultats. La recherche PRIME donc sur le repli : [expandedFolders] est ignoré tant
 * qu'une requête est saisie (sinon un résultat se cacherait dans un dossier replié).
 *
 * v7.1.54 — requête vide ⇒ arbre REPLIABLE : les enfants d'un dossier (ses catégories ET ses
 * sous-dossiers) ne sont émis que si son id est dans [expandedFolders]. Le repli est
 * récursif PAR CONSTRUCTION : on s'arrête au dossier replié, donc un sous-dossier n'est
 * même pas atteint tant que son parent est fermé — tout le sous-arbre disparaît d'un coup.
 * Les catégories LIBRES (sans dossier) ne dépendent d'aucun dossier : toujours visibles.
 *
 * v7.1.80 — deux paramètres pour les NOUVEAUX usages (défauts = comportement historique) :
 * [excludeCategoryIds] retire des catégories de l'arbre ET de la recherche (le « Move »
 * exclut sa catégorie SOURCE des cibles) ; [pruneEmptyFolders] n'émet un dossier que si
 * son sous-arbre contient au moins une catégorie restante (un picker de rattachement
 * n'affiche pas de bandeaux morts — le sélecteur de la fiche, lui, montre tout).
 */
internal fun buildCategoryPickerRows(
    categories: List<Category>,
    groups: List<CategoryGroup>,
    query: String,
    expandedFolders: Set<Long>,
    excludeCategoryIds: Set<String> = emptySet(),
    pruneEmptyFolders: Boolean = false,
    // v7.1.83 — dossiers à retirer de l'arbre (sélecteur de dossier CIBLE : on ne propose
    // ni le dossier courant, ni ceux qu'on est en train de déplacer).
    excludeGroupIds: Set<Long> = emptySet()
): List<CategoryPickerRow> {
    val cats =
        if (excludeCategoryIds.isEmpty()) categories
        else categories.filter { it.id !in excludeCategoryIds }
    val grps =
        if (excludeGroupIds.isEmpty()) groups
        else groups.filter { it.id !in excludeGroupIds }
    // Moteur de recherche CENTRAL (multi-mots, insensible casse/accents, sûr en arabe).
    val tokens = query.searchTokens()
    if (tokens.isNotEmpty()) {
        return cats
            .filter { it.name.matchesAllTokens(tokens) }
            .map { CategoryPickerRow.Item(it, 0) }
    }

    val rows = mutableListOf<CategoryPickerRow>()
    val catsByGroup = cats.filter { it.parentGroupId != null }.groupBy { it.parentGroupId!! }
    val subByParent = grps.filter { it.parentGroupId != null }.groupBy { it.parentGroupId!! }

    // Un sous-arbre « vivant » contient au moins une catégorie (après exclusions).
    fun subtreeHasCategory(group: CategoryGroup): Boolean =
        catsByGroup[group.id].orEmpty().isNotEmpty() ||
            subByParent[group.id].orEmpty().any { subtreeHasCategory(it) }

    fun emitGroup(group: CategoryGroup, indent: Int) {
        if (pruneEmptyFolders && !subtreeHasCategory(group)) return
        val childCategories = catsByGroup[group.id].orEmpty()
        val childGroups = subByParent[group.id].orEmpty()
        val expanded = group.id in expandedFolders
        rows += CategoryPickerRow.Folder(
            group = group,
            indent = indent,
            expanded = expanded,
            hasChildren = childCategories.isNotEmpty() || childGroups.isNotEmpty()
        )
        if (!expanded) return
        childCategories.forEach { rows += CategoryPickerRow.Item(it, indent + 1) }
        childGroups.forEach { emitGroup(it, indent + 1) }
    }

    // Un dossier dont le parent a été exclu remonte à la RACINE plutôt que de disparaître
    // avec lui (sinon des cibles valides deviendraient inatteignables).
    val visibleIds = grps.map { it.id }.toSet()
    grps.filter { it.parentGroupId == null || it.parentGroupId !in visibleIds }
        .forEach { emitGroup(it, 0) }
    cats.filter { it.parentGroupId == null }.forEach { rows += CategoryPickerRow.Item(it, 0) }
    return rows
}

/**
 * Sélecteur « Gérer les catégories » d'une personne (v7.1.5, refondu en v7.1.50).
 *
 * - **Feuille montante** ([ModalBottomSheet], comme la galerie et les feuilles Tri/Affichage)
 *   plutôt qu'un `AlertDialog` centré : plus de hauteur pour une longue liste, et le contenu
 *   REMONTE au-dessus du clavier — un dialogue centré, lui, se fait recouvrir (et ses insets
 *   valent 0, cf. v7.1.49).
 * - **Recherche** (v7.1.50) : **loupe repliable** dans l'en-tête, façon [JtrSearchableTopAppBar]
 *   (la ligne de titre se transforme en champ, focus automatique ; le retour replie ET efface).
 *   Repliée par défaut : rien ne consomme de hauteur quand on a peu de catégories. État et
 *   requête PUREMENT LOCAUX — aucun repository, aucun ViewModel, aucune persistance touchés.
 * - Liste TOUTES les catégories existantes, cases à cocher multiples, cases pré-cochées
 *   pour celles déjà associées ; la hiérarchie dossiers/sous-groupes est présentée de
 *   façon cohérente avec l'écran Catégories (hors recherche).
 * - **Dossiers repliables** (v7.1.54) : avec beaucoup de dossiers, l'arbre tout déplié
 *   noyait la liste. Tout part donc REPLIÉ — on ne voit que les en-têtes et les catégories
 *   libres — et on déplie ce qu'on veut (ou on cherche, ce qui court-circuite le repli).
 *   État PUREMENT LOCAL à l'ouverture de la feuille (aucun repository, aucune persistance,
 *   aucune migration) : le repli est un confort de lecture, pas une donnée du contact.
 * - Cocher/décocher appelle [onToggle] → persistance immédiate (auto-save) ; la fiche
 *   se met à jour de façon réactive.
 * - « + » dans l'EN-TÊTE (v7.1.50 : auparavant dernier item de la liste, donc hors d'atteinte
 *   sans scroller) réutilise EXACTEMENT le formulaire de création de l'écran Catégories
 *   ([AddCategoryDialog]) ; à la confirmation, [onCreateCategory] crée la catégorie ET y
 *   rattache la personne (auto-sélection), et le sélecteur reste ouvert.
 *
 * Présentation pure : les données et callbacks sont fournis par l'appelant (UDF) ;
 * aucune logique métier ici. Le nom historique est conservé (un seul appelant).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesDialog(
    categories: List<Category>,
    groups: List<CategoryGroup>,
    memberIds: Set<String>,
    onToggle: (categoryId: String, inCategory: Boolean) -> Unit,
    onCreateCategory: (name: String, color: String, imagePath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddCategory by remember { mutableStateOf(false) }
    // Recherche REPLIÉE par défaut : une simple loupe dans l'en-tête, aucun champ ne
    // consomme de hauteur tant qu'on ne le demande pas.
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Dossiers DÉPLIÉS (v7.1.54). Vide au départ = tout replié. Session-locale : à chaque
    // réouverture on repart compact — l'utilisateur ne retrouve pas un arbre à moitié ouvert
    // dont il ne se souvient pas.
    // Set IMMUABLE dans un MutableState plutôt qu'un set observable : chaque bascule produit
    // une NOUVELLE valeur, donc comparable par `==` et directement utilisable comme clé de
    // `remember` ci-dessous (un set mutable garderait la même identité et ne l'invaliderait
    // jamais). `mutableStateSetOf` n'existe de toute façon pas dans ce Compose (BOM 2024.12).
    var expandedFolders by remember { mutableStateOf(emptySet<Long>()) }
    val rows = remember(categories, groups, query, expandedFolders) {
        buildCategoryPickerRows(categories, groups, query, expandedFolders)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // Le clavier pousse le contenu vers le haut : la feuille étant ancrée en
                // bas, le champ de recherche et les premières lignes restent visibles.
                .imePadding()
        ) {
            // ── En-tête COMMUTABLE (v7.1.50), même grammaire que JtrSearchableTopAppBar :
            // la ligne de titre se TRANSFORME en champ de saisie. Aucun champ permanent →
            // avec peu de catégories, la feuille reste minimale.
            if (searchActive) {
                val focusRequester = remember { FocusRequester() }
                val focusManager = LocalFocusManager.current
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Fermer la recherche EFFACE la query : jamais de filtre fantôme
                    // invisible au retour de l'arbre.
                    IconButton(onClick = { query = ""; searchActive = false }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.category_search_placeholder)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.common_clear)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.person_categories_dialog_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        // Combien de catégories pour ce contact, d'un coup d'œil. Le pluriel
                        // « N sélectionnée(s) » est déjà accordé au FÉMININ (catégories) dans
                        // les 13 langues → aucune nouvelle ressource.
                        if (memberIds.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    R.plurals.categories_selection_count,
                                    memberIds.size, memberIds.size
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { searchActive = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showAddCategory = true }) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.person_category_new),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    // `fill = false` : la liste prend la place RESTANTE (donc se réduit
                    // quand le clavier monte, sans jamais rogner l'en-tête ni « Terminé »),
                    // et pas plus que son contenu — la feuille reste courte si peu de
                    // catégories. Le plafond garde la feuille sous la moitié d'écran.
                    .weight(1f, fill = false)
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            stringResource(
                                // Catalogue vide vs recherche infructueuse : deux messages,
                                // « créez-en une » n'aurait aucun sens en filtrant.
                                if (query.isBlank()) R.string.person_categories_empty
                                else R.string.person_categories_no_results
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                itemsIndexed(rows, key = { _, row -> pickerRowKey(row) }) { index, row ->
                    when (row) {
                        is CategoryPickerRow.Folder -> FolderHeaderRow(
                            group = row.group,
                            indent = row.indent,
                            expanded = row.expanded,
                            hasChildren = row.hasChildren,
                            onToggleExpand = {
                                val id = row.group.id
                                expandedFolders =
                                    if (id in expandedFolders) expandedFolders - id
                                    else expandedFolders + id
                            }
                        )
                        is CategoryPickerRow.Item -> CategoryCheckRow(
                            category = row.category,
                            indent = row.indent,
                            checked = row.category.id in memberIds,
                            onCheckedChange = { onToggle(row.category.id, it) },
                            // Divider léger uniquement ENTRE deux catégories qui se suivent :
                            // il casse la « masse blanche » sans encadrer les bandeaux de
                            // dossier (eux se détachent déjà par leur fond + marge).
                            showDivider = rows.getOrNull(index + 1) is CategoryPickerRow.Item
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
            }
        }
    }

    if (showAddCategory) {
        AddCategoryDialog(
            onConfirm = { name, color, imagePath ->
                onCreateCategory(name, color, imagePath)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false }
        )
    }
}

internal fun pickerRowKey(row: CategoryPickerRow): String = when (row) {
    is CategoryPickerRow.Folder -> "g_${row.group.id}"
    is CategoryPickerRow.Item -> "c_${row.category.id}"
}

/**
 * v7.1.77 — vignette d'identité d'une ligne du sélecteur : l'IMAGE de la catégorie ou
 * du dossier devient l'identifiant visuel principal (HR personnalise ses catégories
 * avec des images, c'est ce qu'il reconnaît — 13/18 en ont une). À défaut, repli DOUX :
 * initiale sur teinte discrète de la couleur de la catégorie (fini la pastille saturée,
 * la couleur n'est plus qu'un accent), ou icône dossier neutre pour un groupe sans
 * image. Chargement Coil, même mécanisme que les tuiles (chemin absolu filesDir/photos).
 */
@Composable
internal fun PickerThumbnail(
    imagePath: String?,
    name: String,
    colorHex: String?,
    modifier: Modifier = Modifier,
    // v7.1.126 — diamètre paramétrable : 36 dp dans les feuilles de sélection (défaut
    // d'origine), 40/44 dp dans les listes de catégories. L'anneau et son liseré, eux, ne
    // changent PAS avec la taille : un liseré qui grossit se lirait comme un cadre.
    size: Dp = 36.dp,
    // v7.1.126 — forme paramétrable pour servir AUSSI les dossiers, que les listes
    // dessinent en carré arrondi (la forme est ce qui distingue un dossier d'une
    // catégorie ; l'anneau ne doit pas effacer cette distinction).
    shape: Shape = CircleShape,
    // v7.1.126 — habillage du repli DOSSIER (ni photo, ni couleur propre). Les défauts
    // sont le rendu NEUTRE de ce composant, celui des feuilles de sélection : elles ne
    // passent rien et ne changent donc pas. Seules les listes de l'écran Catégories les
    // surchargent, pour garder leur dossier TEINTÉ par la palette comme avant v7.1.126.
    folderContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    folderIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    folderIconSize: Dp = 18.dp
) {
    // Itération 2 — ANNEAU d'accent discret autour de la vignette : la couleur stockée de
    // la catégorie reste visible même quand une image occupe le disque (avant, l'image
    // effaçait toute identité couleur). Adoucie (alpha), jamais d'aplat saturé. Un dossier
    // n'a pas de couleur stockée (CategoryGroup) → liseré neutre, qui donne au passage un
    // bord net aux photos claires sur fond clair.
    val accent = if (colorHex != null) rememberCategoryColor(colorHex) else null
    val ring = accent?.copy(alpha = 0.55f) ?: MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .size(size)
            .border(1.5.dp, ring, shape)
            // Fin espace entre l'anneau et le disque : lu comme un LISERÉ, pas un cadre.
            .padding(3.dp)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            imagePath != null -> AsyncImage(
                model = imagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            accent != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    // codePointAt : une initiale emoji/arabe (surrogate pair) reste entière.
                    val initial = name.trim().let {
                        if (it.isEmpty()) "" else String(Character.toChars(it.codePointAt(0))).uppercase()
                    }
                    if (initial.isEmpty()) {
                        Icon(Icons.Default.Folder, null, Modifier.size(18.dp), tint = accent)
                    } else {
                        Text(
                            initial,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                }
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(folderContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder, null,
                    modifier = Modifier.size(folderIconSize),
                    tint = folderIconTint
                )
            }
        }
    }
}

/**
 * En-tête de dossier : repliable (v7.1.54) mais toujours PAS cochable — un contact
 * appartient à des catégories, jamais à un dossier. Le clic ne peut donc que déplier.
 * v7.1.77 : l'icône dossier générique cède la place à la vignette du groupe (son image
 * de couverture, sinon repli dossier neutre).
 *
 * Itération 2 — le dossier se lit comme un CONTENANT au premier regard (retour HR :
 * « trop plat », groupes et catégories indistincts) :
 * - BANDEAU à fond doux ([surfaceContainerHigh], rôle de thème → suit le mode sombre)
 *   au lieu d'une ligne nue ; nom un cran plus marqué (titleSmall/onSurface).
 * - L'indentation décale le BLOC entier (padding AVANT le fond) : un sous-dossier est
 *   un bandeau plus court en retrait, pas un bandeau pleine largeur au texte décalé.
 * - Marge haute HORS fond = respiration entre le bloc d'un groupe et le suivant.
 * - Chevron déporté en FIN de ligne (convention des listes de dossiers) : vignettes et
 *   noms restent alignés à l'ouverture, avec ou sans enfants.
 */
@Composable
internal fun FolderHeaderRow(
    group: CategoryGroup,
    indent: Int,
    expanded: Boolean,
    hasChildren: Boolean,
    onToggleExpand: () -> Unit,
    // v7.1.83 — non nul ⇒ le dossier est une CIBLE sélectionnable (déplacer une catégorie
    // dans un groupe) : le corps de la ligne CHOISIT, et le chevron devient une zone
    // cliquable à part pour déplier. Nul (usages historiques) ⇒ la ligne déplie, inchangé.
    onSelect: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 20).dp, top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Dossier vide : rien à déplier ⇒ ligne inerte (pas de ripple qui promette
            // une ouverture qui n'arrivera pas) — sauf s'il est sélectionnable.
            .then(
                when {
                    onSelect != null -> Modifier.clickable(role = Role.Button, onClick = onSelect)
                    hasChildren -> Modifier.clickable(role = Role.Button, onClick = onToggleExpand)
                    else -> Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PickerThumbnail(imagePath = group.imagePath, name = group.name, colorHex = null)
        Text(
            group.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (hasChildren) {
            val chevron = @Composable {
                Icon(
                    // AutoMirrored : en RTL (arabe) le chevron « fermé » doit pointer vers la
                    // GAUCHE, sens de lecture. Ouvert, il pointe vers le bas dans les deux sens.
                    if (expanded) Icons.Default.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.common_hide else R.string.common_show
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onSelect != null) {
                // Cible tactile propre pour déplier sans choisir le dossier.
                IconButton(onClick = onToggleExpand) { chevron() }
            } else {
                chevron()
            }
        }
    }
}

/**
 * v7.1.80 — [checked] devient NULLABLE : `null` = ligne de sélection SIMPLE (le tap
 * CHOISIT, rôle Button, pas de case — le sélecteur de cible du « Move ») ; non-null =
 * ligne cochable historique (fiche contact, rattachement multiple). Les appels
 * existants passent un Boolean : comportement strictement inchangé.
 */
@Composable
internal fun CategoryCheckRow(
    category: Category,
    indent: Int,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean
) {
    // Itération 2 — l'indentation enveloppe la ligne ENTIÈRE (divider compris) et sort de
    // la zone cliquable : le ripple s'arrête au bord indenté, l'arbre parent → enfants se
    // lit au décalage du bloc, cohérent avec les bandeaux de dossier.
    Column(modifier = Modifier.padding(start = (indent * 20).dp)) {
        // Toute la ligne est cochable/cliquable (a11y : rôle Checkbox ou Button + nom).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (checked != null) Modifier.toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange
                    ) else Modifier.clickable(role = Role.Button) { onCheckedChange(true) }
                )
                .padding(top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // v7.1.77 — l'image de la catégorie remplace la pastille de couleur saturée :
            // c'est elle que HR reconnaît. Sans image, initiale sur teinte douce de la
            // couleur (l'accent subsiste, discret).
            PickerThumbnail(
                imagePath = category.imagePath,
                name = category.name,
                colorHex = category.color
            )
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            // Étoile (v7.1.50) : les favoris remontent en tête (ORDER BY isFavorite DESC côté
            // DAO) — sans repère visuel, l'ordre paraissait arbitraire.
            if (category.isFavorite) {
                FavoriteStar(size = 16.dp)
            }
            // Le rôle/clic est porté par la Row → la case ne re-gère pas l'événement.
            // Sélection simple (checked == null) : pas de case, le tap suffit.
            if (checked != null) {
                Checkbox(checked = checked, onCheckedChange = null)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                // Retrait = vignette (36) + espace (12) : le trait court sous le NOM,
                // à la Material, et suit le sens de lecture (padding start) en RTL.
                modifier = Modifier.padding(start = 48.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}