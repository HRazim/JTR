package com.jtr.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.CategoryDao
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.data.local.PersonDao
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Couvre la gestion des catégories d'une personne depuis sa fiche (v7.1.5), au niveau
 * de la table de jointure RÉUTILISÉE `person_category_join` :
 *  - ajout multiple de liens (appartenance à plusieurs catégories) ;
 *  - retrait d'un lien sans toucher aux autres ni aux entités ;
 *  - réactivité du Flow `getCategoryIdsForPerson` (cases pré-cochées / badges à jour) ;
 *  - « création rapide + auto-sélection » modélisée par insert(catégorie)+insert(lien).
 *
 * Base Room EN MÉMOIRE (schéma v21, aucune migration) → assertions déterministes.
 */
@RunWith(AndroidJUnit4::class)
class PersonCategoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var joinDao: PersonCategoryDao

    private val personId = "p1"

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        personDao = db.personDao()
        categoryDao = db.categoryDao()
        joinDao = db.personCategoryDao()

        // FK : la personne et les catégories doivent exister avant tout lien.
        personDao.insert(Person(id = personId, firstName = "Alice"))
        categoryDao.insert(Category(id = "cat-friends", name = "Amis"))
        categoryDao.insert(Category(id = "cat-work", name = "Travail"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun assignMultipleCategories_isReflectedReactively() = runBlocking {
        // Au départ : aucune appartenance.
        assertTrue(joinDao.getCategoryIdsForPerson(personId).first().isEmpty())

        // Ajout de DEUX liens (sélection multiple depuis la fiche).
        joinDao.insertAll(
            listOf(
                PersonCategoryJoin(personId, "cat-friends"),
                PersonCategoryJoin(personId, "cat-work")
            )
        )

        val ids = joinDao.getCategoryIdsForPerson(personId).first().toSet()
        assertEquals(setOf("cat-friends", "cat-work"), ids)
    }

    @Test
    fun removeOneCategory_keepsOthersAndEntities() = runBlocking {
        joinDao.insertAll(
            listOf(
                PersonCategoryJoin(personId, "cat-friends"),
                PersonCategoryJoin(personId, "cat-work")
            )
        )

        // Retrait d'UN seul lien (décocher une case).
        joinDao.removePersonFromCategory(personId, "cat-work")

        val ids = joinDao.getCategoryIdsForPerson(personId).first()
        assertEquals(listOf("cat-friends"), ids)

        // La personne et la catégorie retirée existent toujours (retrait de lien ≠ suppression).
        assertTrue(personDao.getById(personId) != null)
        assertTrue(categoryDao.getById("cat-work") != null)
    }

    @Test
    fun duplicateAssign_isIgnored() = runBlocking {
        joinDao.insert(PersonCategoryJoin(personId, "cat-friends"))
        // Re-cocher la même catégorie ne crée pas de doublon (OnConflict IGNORE).
        joinDao.insert(PersonCategoryJoin(personId, "cat-friends"))

        assertEquals(1, joinDao.getCategoryIdsForPerson(personId).first().size)
    }

    @Test
    fun quickCreateThenAssign_autoSelectsNewCategory() = runBlocking {
        // Modélise « + Nouvelle catégorie » : création PUIS rattachement immédiat.
        val created = Category(id = "cat-new", name = "Sport")
        categoryDao.insert(created)
        joinDao.insert(PersonCategoryJoin(personId, created.id))

        val ids = joinDao.getCategoryIdsForPerson(personId).first()
        assertTrue("La nouvelle catégorie doit être auto-sélectionnée", created.id in ids)
        assertFalse(ids.contains("cat-work"))
    }
}
