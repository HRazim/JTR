package com.jtr.app.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.PersonDao
import com.jtr.app.domain.model.Person
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests de la SÉMANTIQUE de la requête « Récents » (v7.1.69), sans Room (pas de
 * connectedAndroidTest) : on reproduit fidèlement la clause SQL
 * `WHERE deletedAt IS NULL AND lastContactedAt IS NOT NULL ORDER BY lastContactedAt DESC LIMIT n`
 * (PersonDao.getRecent) et on vérifie qu'elle fait bien ce que l'accueil attend. Si la clause
 * du DAO change, ce test doit être mis à jour en miroir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentsQueryTest {

    /** Réplique EXACTE de la clause SQL de [com.jtr.app.data.local.PersonDao.getRecent]. */
    private fun recentOf(all: List<Person>, limit: Int): Flow<List<Person>> = flowOf(
        all.filter { it.deletedAt == null && it.lastContactedAt != null }
            .sortedByDescending { it.lastContactedAt }
            .take(limit)
    )

    private fun person(name: String, opened: Long?, deleted: Long? = null) =
        Person(firstName = name, lastContactedAt = opened, deletedAt = deleted)

    @Test
    fun `orders by lastContactedAt desc, newest first`() = runTest {
        val a = person("A", opened = 100)
        val b = person("B", opened = 300)
        val c = person("C", opened = 200)
        val result = recentOf(listOf(a, b, c), 8).first()
        assertThat(result.map { it.firstName }).containsExactly("B", "C", "A").inOrder()
    }

    @Test
    fun `caps the result at the given limit`() = runTest {
        val people = (1..20).map { person("P$it", opened = it.toLong()) }
        val result = recentOf(people, 8).first()
        assertThat(result).hasSize(8)
        // Les 8 derniers ouverts (timestamps 20..13), plus récent d'abord.
        assertThat(result.first().firstName).isEqualTo("P20")
        assertThat(result.last().firstName).isEqualTo("P13")
    }

    @Test
    fun `excludes soft-deleted contacts`() = runTest {
        val alive = person("Alive", opened = 100)
        val trashed = person("Trashed", opened = 999, deleted = 123456)
        val result = recentOf(listOf(alive, trashed), 8).first()
        assertThat(result.map { it.firstName }).containsExactly("Alive")
    }

    @Test
    fun `excludes never-opened contacts (null lastContactedAt)`() = runTest {
        val opened = person("Opened", opened = 100)
        val neverOpened = person("NeverOpened", opened = null)
        val result = recentOf(listOf(opened, neverOpened), 8).first()
        assertThat(result.map { it.firstName }).containsExactly("Opened")
    }

    @Test
    fun `empty when nothing has been opened`() = runTest {
        val people = listOf(person("A", opened = null), person("B", opened = null))
        assertThat(recentOf(people, 8).first()).isEmpty()
    }

    @Test
    fun `repository getRecent delegates to dao with the given limit`() = runTest {
        mockkObject(AppDatabase.Companion)
        try {
            val dao = mockk<PersonDao>(relaxed = true)
            val flow = flowOf(listOf(person("A", opened = 100)))
            every { dao.getRecent(8) } returns flow
            val db = mockk<AppDatabase>(relaxed = true)
            every { db.personDao() } returns dao
            every { AppDatabase.getInstance(any()) } returns db

            val repo = PersonRepository(mockk<Context>(relaxed = true))
            val result = repo.getRecent(8).first()

            assertThat(result.map { it.firstName }).containsExactly("A")
            coVerify(exactly = 1) { dao.getRecent(8) }
        } finally {
            unmockkObject(AppDatabase.Companion)
        }
    }

    @Test
    fun `reopening an older contact moves it to the front`() = runTest {
        val a = person("A", opened = 100)
        val b = person("B", opened = 200)
        // État initial : B puis A.
        assertThat(recentOf(listOf(a, b), 8).first().map { it.firstName })
            .containsExactly("B", "A").inOrder()
        // On « rouvre » A (lastContactedAt devient le plus récent) → A repasse en tête.
        val aReopened = a.copy(lastContactedAt = 300)
        assertThat(recentOf(listOf(aReopened, b), 8).first().map { it.firstName })
            .containsExactly("A", "B").inOrder()
    }
}
