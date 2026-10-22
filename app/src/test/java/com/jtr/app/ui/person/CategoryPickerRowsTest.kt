package com.jtr.app.ui.person

import com.google.common.truth.Truth.assertThat
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import org.junit.Test

/**
 * v7.1.80 — [buildCategoryPickerRows] gagne deux paramètres pour les sélecteurs
 * harmonisés (cible du « Move », rattachement à un dossier) :
 *
 *  - `excludeCategoryIds` retire des catégories de l'arbre ET de la recherche (le
 *    « Move » exclut sa catégorie SOURCE des cibles) ;
 *  - `pruneEmptyFolders` n'émet un dossier que si son sous-arbre contient au moins une
 *    catégorie restante (pas de bandeau mort dans un picker de cibles).
 *
 * Et SURTOUT : les DÉFAUTS laissent le sélecteur de la fiche (v7.1.77, validé)
 * strictement inchangé — un dossier vide y reste émis, inerte (hasChildren = false).
 */
class CategoryPickerRowsTest {

    private val folderA = CategoryGroup(id = 1L, name = "DossierA")
    private val subFolder = CategoryGroup(id = 2L, name = "SousDossier", parentGroupId = 1L)
    private val emptyFolder = CategoryGroup(id = 3L, name = "DossierVide")

    private val catSource = Category(id = "src", name = "Source")
    private val catFree = Category(id = "free", name = "Libre")
    private val catInA = Category(id = "inA", name = "DansA", parentGroupId = 1L)
    private val catInSub = Category(id = "inSub", name = "DansSous", parentGroupId = 2L)

    private fun names(rows: List<CategoryPickerRow>) = rows.map {
        when (it) {
            is CategoryPickerRow.Folder -> "g:${it.group.name}"
            is CategoryPickerRow.Item -> "c:${it.category.name}"
        }
    }

    @Test
    fun exclusion_removesCategory_fromRootAndExpandedFolder() {
        val rows = buildCategoryPickerRows(
            categories = listOf(catSource, catFree, catInA),
            groups = listOf(folderA),
            query = "",
            expandedFolders = setOf(1L),
            excludeCategoryIds = setOf("src", "inA")
        )
        // « Source » (racine) et « DansA » (dans le dossier déplié) ont disparu.
        assertThat(names(rows)).containsExactly("g:DossierA", "c:Libre").inOrder()
    }

    @Test
    fun exclusion_appliesToSearchToo() {
        val rows = buildCategoryPickerRows(
            categories = listOf(catSource, catFree),
            groups = emptyList(),
            query = "e", // matche « Source » ET « Libre »
            expandedFolders = emptySet(),
            excludeCategoryIds = setOf("src")
        )
        assertThat(names(rows)).containsExactly("c:Libre")
    }

    @Test
    fun prune_dropsFolderWhoseOnlyCategoryIsExcluded_keepsFolderWithCandidateInSubfolder() {
        val rows = buildCategoryPickerRows(
            categories = listOf(catInA, catInSub),
            groups = listOf(folderA, subFolder, emptyFolder),
            query = "",
            expandedFolders = emptySet(),
            // La seule catégorie DIRECTE de DossierA est exclue, mais son SOUS-dossier
            // garde une candidate → DossierA survit ; DossierVide est élagué.
            excludeCategoryIds = setOf("inA"),
            pruneEmptyFolders = true
        )
        assertThat(names(rows)).containsExactly("g:DossierA")
    }

    @Test
    fun defaults_unchanged_emptyFolderStillEmittedInert() {
        // Garde anti-régression du sélecteur v7.1.77 : sans exclusion ni élagage, un
        // dossier vide reste émis, non dépliable.
        val rows = buildCategoryPickerRows(
            categories = listOf(catFree),
            groups = listOf(emptyFolder),
            query = "",
            expandedFolders = emptySet()
        )
        assertThat(names(rows)).containsExactly("g:DossierVide", "c:Libre").inOrder()
        val folder = rows.first() as CategoryPickerRow.Folder
        assertThat(folder.hasChildren).isFalse()
    }
}
