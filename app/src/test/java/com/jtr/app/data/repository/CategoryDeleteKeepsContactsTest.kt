package com.jtr.app.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.CategoryDao
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.data.local.PersonDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * v7.1.71 — supprimer une catégorie NE supprime JAMAIS ses contacts.
 *
 * La « base » en mémoire modélise : persons (id → deletedAt), categories (id → deletedAt),
 * joins (ensemble de (personId, categoryId)). On pilote le VRAI [CategoryRepository] contre des
 * DAO mockés backés par cet état, et on vérifie les états de fin + que `personDao.softDeleteMultiple`
 * / `hardDelete` / `restoreMultiple` ne sont JAMAIS appelés par la suppression d'une catégorie.
 *
 * L'exclusion des catégories en corbeille dans les requêtes ACTIVES est validée par une réplique
 * pure de la clause SQL (JOIN categories + `c.deletedAt IS NULL`) — Room n'est pas instancié (pas
 * de connectedAndroidTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDeleteKeepsContactsTest {

    private val persons = linkedMapOf<String, Long?>()      // id -> deletedAt (null = actif)
    private val categories = linkedMapOf<String, Long?>()   // id -> deletedAt
    private val joins = linkedSetOf<Pair<String, String>>() // (personId, categoryId)

    private lateinit var personDao: PersonDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var personCategoryDao: PersonCategoryDao

    @Before
    fun setUp() {
        personDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        personCategoryDao = mockk(relaxed = true)

        coEvery { categoryDao.softDelete(any(), any()) } answers {
            categories[firstArg()] = secondArg<Long>()
        }
        coEvery { categoryDao.restore(any()) } answers { categories[firstArg()] = null }
        coEvery { categoryDao.hardDelete(any()) } answers {
            val id = firstArg<String>(); categories.remove(id); joins.removeAll { it.second == id }
        }
        coEvery { personCategoryDao.removeAllFromCategory(any()) } answers {
            val id = firstArg<String>(); joins.removeAll { it.second == id }
        }
        // Les mutations de CONTACTS : backées pour détecter tout appel indu.
        coEvery { personDao.softDeleteMultiple(any(), any()) } answers {
            firstArg<List<String>>().forEach { persons[it] = secondArg() }
        }
        coEvery { personDao.restoreMultiple(any()) } answers {
            firstArg<List<String>>().forEach { persons[it] = null }
        }
        coEvery { personDao.hardDelete(any()) } answers { persons.remove(firstArg<String>()) }

        val db = mockk<AppDatabase>(relaxed = true)
        every { db.categoryDao() } returns categoryDao
        every { db.personDao() } returns personDao
        every { db.personCategoryDao() } returns personCategoryDao
        every { db.categoryGroupDao() } returns mockk(relaxed = true)
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db
    }

    @After
    fun tearDown() = unmockkObject(AppDatabase.Companion)

    private fun repo() = CategoryRepository(mockk<Context>(relaxed = true))

    private fun membersOf(cat: String) = joins.filter { it.second == cat }.map { it.first }.toSet()
    private fun activeContacts() = persons.filterValues { it == null }.keys

    /** Réplique de PersonCategoryDao.getActivePersonsInCategory : contact actif + catégorie active. */
    private fun activeMembersInCategory(cat: String) =
        joins.filter { it.second == cat }
            .map { it.first }
            .filter { persons[it] == null && categories[cat] == null }
            .toSet()

    // ── Suppression : catégorie seule ────────────────────────────────────────

    @Test
    fun softDelete_marksCategory_keepsContactsActive_keepsLinks() = runTest {
        categories["A"] = null; persons["p1"] = null; joins.add("p1" to "A")
        repo().softDeleteCategory("A")

        assertThat(categories["A"]).isNotNull()              // catégorie en corbeille
        assertThat(persons["p1"]).isNull()                   // contact ACTIF
        assertThat(membersOf("A")).containsExactly("p1")     // lien CONSERVÉ
        coVerify(exactly = 0) { personDao.softDeleteMultiple(any(), any()) }  // aucun contact supprimé
    }

    @Test
    fun softDelete_excludesLinkFromActiveQueries() = runTest {
        categories["A"] = null; persons["p1"] = null; joins.add("p1" to "A")
        assertThat(activeMembersInCategory("A")).containsExactly("p1")   // avant
        repo().softDeleteCategory("A")
        assertThat(activeMembersInCategory("A")).isEmpty()              // après : lien masqué en actif
    }

    @Test
    fun contactInAandB_deleteA_staysActiveAndInB() = runTest {
        categories["A"] = null; categories["B"] = null
        persons["p1"] = null; joins.add("p1" to "A"); joins.add("p1" to "B")
        repo().softDeleteCategory("A")

        assertThat(persons["p1"]).isNull()                     // toujours actif (footgun disparu)
        assertThat(activeMembersInCategory("B")).containsExactly("p1")  // toujours dans B
        assertThat(activeMembersInCategory("A")).isEmpty()
    }

    @Test
    fun contactOnlyInA_deleteA_staysActiveWithoutCategory() = runTest {
        categories["A"] = null; persons["p1"] = null; joins.add("p1" to "A")
        repo().softDeleteCategory("A")
        assertThat(persons["p1"]).isNull()                     // actif, visible accueil
        // Aucune catégorie active ne le contient.
        assertThat(categories.keys.filter { categories[it] == null }
            .flatMap { activeMembersInCategory(it) }).doesNotContain("p1")
    }

    // ── Restauration ─────────────────────────────────────────────────────────

    @Test
    fun restore_reattachesExistingMembers_doesNotResurrectSeparatelyDeleted() = runTest {
        categories["A"] = 123L                                  // en corbeille
        persons["p1"] = null                                    // membre encore présent
        persons["p2"] = 999L                                    // supprimé INDIVIDUELLEMENT, encore lié
        joins.add("p1" to "A"); joins.add("p2" to "A")

        repo().restoreCategory("A")

        assertThat(categories["A"]).isNull()                    // catégorie restaurée
        assertThat(activeMembersInCategory("A")).containsExactly("p1")  // p1 re-attaché
        assertThat(persons["p2"]).isEqualTo(999L)               // p2 PAS ressuscité
        coVerify(exactly = 0) { personDao.restoreMultiple(any()) }
    }

    // ── Purge : JAMAIS de hard-delete de contact (cas critique C) ────────────

    @Test
    fun hardDelete_removesCategoryAndLinks_neverDeletesContacts() = runTest {
        categories["A"] = 123L
        persons["p1"] = null                                    // actif
        persons["p2"] = 999L                                    // supprimé individuellement, encore lié
        joins.add("p1" to "A"); joins.add("p2" to "A")

        repo().hardDeleteCategory("A")

        assertThat(categories).doesNotContainKey("A")           // catégorie purgée
        assertThat(membersOf("A")).isEmpty()                    // liens retirés
        assertThat(persons.keys).containsExactly("p1", "p2")    // AUCUN contact supprimé
        assertThat(persons["p2"]).isEqualTo(999L)               // le contact supprimé-seul SURVIT
        coVerify(exactly = 0) { personDao.hardDelete(any()) }
    }

    @Test
    fun autoPurge_unchanged_removesExpiredCategoriesKeepsContacts() = runTest {
        val old = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        categories["old"] = old
        persons["p1"] = 999L; joins.add("p1" to "old")          // contact supprimé-seul lié
        coEvery { categoryDao.getDeletedIdsOlderThan(any()) } answers {
            val cutoff = firstArg<Long>()
            categories.filterValues { it != null && it < cutoff }.keys.toList()
        }
        repo().purgeOldDeletedCategories()

        assertThat(categories).doesNotContainKey("old")
        assertThat(membersOf("old")).isEmpty()
        assertThat(persons.keys).containsExactly("p1")          // contact intact
        coVerify(exactly = 0) { personDao.hardDelete(any()) }
    }
}
