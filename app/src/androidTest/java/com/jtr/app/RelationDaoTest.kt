package com.jtr.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.PersonDao
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v7.1.6 — INTÉGRITÉ DES RELATIONS au niveau base (in-memory, JAMAIS la base réelle) :
 *  - Cas 1 : une relation portant linkedPersonId ouvre LE BON contact, même en présence
 *    d'un homonyme partiel (« Ryan » vs « Ryan Sugarry ») ;
 *  - Cas 2 : `findIdsByName` renvoie TOUS les homonymes → l'ambiguïté est détectée
 *    (jamais un choix arbitraire / faux lien) ;
 *  - Cas 3 : renommer un contact ne casse rien (l'id est stable, la relation reste valide).
 */
@RunWith(AndroidJUnit4::class)
class RelationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.personDao()

        // Deux homonymes : « Ryan » (sans nom) et « Ryan Sugarry » — tous deux firstName=Ryan.
        dao.insert(Person(id = "ryan", firstName = "Ryan"))
        dao.insert(Person(id = "ryan-sugarry", firstName = "Ryan", lastName = "Sugarry"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun ambiguousName_returnsAllHomonyms_neverGuesses() {
        // Cas 2 : « Ryan » désigne DEUX contacts → ambiguïté détectable, pas de faux lien.
        val ids = runBlocking { dao.findIdsByName("Ryan") }.toSet()
        assertEquals(setOf("ryan", "ryan-sugarry"), ids)
        // « Ryan Sugarry » est, lui, sans ambiguïté.
        val unique = runBlocking { dao.findIdsByName("Ryan Sugarry") }
        assertEquals(listOf("ryan-sugarry"), unique)
    }

    @Test
    fun relationWithLinkedId_pointsToExactPerson_notHomonym() = runBlocking {
        // Cas 1 : Alice → ami « Ryan » avec linkedPersonId = id de Ryan (pas Sugarry).
        dao.insert(Person(
            id = "alice", firstName = "Alice",
            relationLines = listOf(DynamicLine(value = "Ryan", label = "friend", linkedPersonId = "ryan"))
        ))
        val alice = dao.getById("alice")!!
        val rel = alice.relationLines!!.first()
        // La clé est l'id — résolution déterministe vers LE BON Ryan.
        assertEquals("ryan", rel.linkedPersonId)
        val target = dao.getById(rel.linkedPersonId!!)!!
        assertEquals("Ryan", target.firstName)
        assertTrue(target.lastName.isNullOrBlank()) // c'est « Ryan », pas « Ryan Sugarry »
    }

    @Test
    fun rename_keepsRelationValid_byStableId() = runBlocking {
        dao.insert(Person(
            id = "alice", firstName = "Alice",
            relationLines = listOf(DynamicLine(value = "Ryan", label = "friend", linkedPersonId = "ryan"))
        ))
        // Cas 3 : on RENOMME Ryan → « Ryan Bouchard » (id inchangé).
        val ryan = dao.getById("ryan")!!
        dao.update(ryan.copy(firstName = "Ryan", lastName = "Bouchard"))

        // La relation d'Alice (par id) reste valide et résout le contact RENOMMÉ.
        val rel = dao.getById("alice")!!.relationLines!!.first()
        assertEquals("ryan", rel.linkedPersonId)
        val target = dao.getById(rel.linkedPersonId!!)
        assertNotNull(target)
        assertEquals("Bouchard", target!!.lastName)
    }
}
