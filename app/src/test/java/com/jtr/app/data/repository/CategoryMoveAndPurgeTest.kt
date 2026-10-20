package com.jtr.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.CategoryDao
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.domain.model.PersonCategoryJoin
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests de la Vague 1 catégories (v7.1.70), sans Room (pas de connectedAndroidTest) :
 *  - [PersonRepository.moveToCategory] : vrai déplacement transactionnel (retrait source + ajout cible) ;
 *  - [CategoryRepository.purgeOldDeletedCategories] : purge des catégories > 30 j + nettoyage des
 *    liens, contacts JAMAIS supprimés.
 *
 * On pilote les VRAIS repositories contre des DAO en mémoire (mockk + answers backés par des
 * ensembles). `db.withTransaction { }` est stubbé pour exécuter le bloc inline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryMoveAndPurgeTest {

    // "Base" en mémoire partagée par les deux repos.
    private val joins = linkedSetOf<Pair<String, String>>()            // person_category_join
    private val persons = linkedSetOf<String>()                         // ids de contacts (jamais supprimés par la purge)
    private val categories = linkedMapOf<String, Long?>()               // categoryId -> deletedAt (null = actif)

    private lateinit var personCategoryDao: PersonCategoryDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        personCategoryDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)

        coEvery { personCategoryDao.insertAll(any()) } answers {
            firstArg<List<PersonCategoryJoin>>().forEach { joins.add(it.personId to it.categoryId) }
        }
        coEvery { personCategoryDao.removePersonsFromCategory(any(), any()) } answers {
            val ids = firstArg<List<String>>(); val cat = secondArg<String>()
            joins.removeAll { it.first in ids && it.second == cat }
        }
        coEvery { personCategoryDao.removeAllFromCategory(any()) } answers {
            val cat = firstArg<String>(); joins.removeAll { it.second == cat }
        }
        coEvery { categoryDao.getDeletedIdsOlderThan(any()) } answers {
            val cutoff = firstArg<Long>()
            categories.filterValues { it != null && it < cutoff }.keys.toList()
        }
        coEvery { categoryDao.hardDelete(any()) } answers {
            val id = firstArg<String>(); categories.remove(id); joins.removeAll { it.second == id } // CASCADE FK
        }

        db = mockk(relaxed = true)
        every { db.personDao() } returns mockk(relaxed = true)
        every { db.categoryDao() } returns categoryDao
        every { db.personCategoryDao() } returns personCategoryDao
        every { db.categoryGroupDao() } returns mockk(relaxed = true)
        every { db.socialLinkDao() } returns mockk(relaxed = true)
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db

        // db.withTransaction { block } → exécute le bloc inline (sinon exige un vrai RoomDatabase).
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(captureLambda<suspend () -> Any>()) } coAnswers {
            lambda<suspend () -> Any>().captured.invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun personRepo() = PersonRepository(mockk<Context>(relaxed = true))
    private fun categoryRepo() = CategoryRepository(mockk<Context>(relaxed = true))

    private fun membersOf(cat: String) = joins.filter { it.second == cat }.map { it.first }.toSet()

    // ── A. Move ─────────────────────────────────────────────────────────────────

    @Test
    fun move_fromAtoB_removesFromA_addsToB() = runTest {
        joins.add("p1" to "A")
        personRepo().moveToCategory(listOf("p1"), "A", "B")
        assertThat(membersOf("A")).isEmpty()
        assertThat(membersOf("B")).containsExactly("p1")
    }

    @Test
    fun move_doesNotTouchOtherMemberships() = runTest {
        joins.add("p1" to "A"); joins.add("p1" to "C")   // p1 dans A et C
        personRepo().moveToCategory(listOf("p1"), "A", "B")
        assertThat(membersOf("A")).isEmpty()
        assertThat(membersOf("B")).containsExactly("p1")
        assertThat(membersOf("C")).containsExactly("p1")  // C intacte
    }

    @Test
    fun move_sameSourceAndTarget_isNoOp() = runTest {
        joins.add("p1" to "A")
        personRepo().moveToCategory(listOf("p1"), "A", "A")
        assertThat(membersOf("A")).containsExactly("p1")
    }

    @Test
    fun move_multiSelection() = runTest {
        joins.add("p1" to "A"); joins.add("p2" to "A"); joins.add("p3" to "A")
        personRepo().moveToCategory(listOf("p1", "p2", "p3"), "A", "B")
        assertThat(membersOf("A")).isEmpty()
        assertThat(membersOf("B")).containsExactly("p1", "p2", "p3")
    }

    @Test
    fun move_alreadyInTarget_noDuplicate_removedFromSource() = runTest {
        joins.add("p1" to "A"); joins.add("p1" to "B")   // déjà dans B
        personRepo().moveToCategory(listOf("p1"), "A", "B")
        assertThat(membersOf("A")).isEmpty()
        assertThat(membersOf("B")).containsExactly("p1")  // pas de doublon (Set)
    }

    // ── B. Purge ────────────────────────────────────────────────────────────────

    @Test
    fun purge_olderThan30Days_isDeleted_joinsRemoved_contactsIntact() = runTest {
        val old = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        categories["oldCat"] = old
        persons.add("p1"); joins.add("p1" to "oldCat")

        categoryRepo().purgeOldDeletedCategories()

        assertThat(categories).doesNotContainKey("oldCat")      // catégorie purgée
        assertThat(membersOf("oldCat")).isEmpty()               // joins supprimés
        assertThat(persons).containsExactly("p1")               // contact INTACT
    }

    @Test
    fun purge_recentlyDeleted_isKept() = runTest {
        val recent = System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000  // < 30 j
        categories["recentCat"] = recent
        categoryRepo().purgeOldDeletedCategories()
        assertThat(categories).containsKey("recentCat")         // conservée
    }

    @Test
    fun purge_activeCategory_isUntouched() = runTest {
        categories["activeCat"] = null                          // non supprimée
        joins.add("p9" to "activeCat")
        categoryRepo().purgeOldDeletedCategories()
        assertThat(categories).containsKey("activeCat")
        assertThat(membersOf("activeCat")).containsExactly("p9")
    }
}
