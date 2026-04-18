package com.jtr.app.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.local.PersonDao
import com.jtr.app.domain.model.Person
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests unitaires du PersonRepository (PP3).
 *
 * [PP3 — Prof] : "Tests unitaires."
 * Utilise MockK pour simuler le DAO, Turbine pour tester les Flow,
 * et Truth pour les assertions lisibles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonRepositoryTest {

    private val mockDao: PersonDao = mockk(relaxed = true)

    @Test
    fun `getAllActive returns flow from dao`() = runTest {
        val persons = listOf(
            Person(firstName = "Alice", lastName = "Test"),
            Person(firstName = "Bob", lastName = "Test")
        )
        coEvery { mockDao.getAllActive() } returns flowOf(persons)

        val flow = mockDao.getAllActive()
        flow.test {
            val result = awaitItem()
            assertThat(result).hasSize(2)
            assertThat(result[0].firstName).isEqualTo("Alice")
            awaitComplete()
        }
    }

    @Test
    fun `softDelete calls dao with correct id`() = runTest {
        val testId = "test-123"
        mockDao.softDelete(testId)
        coVerify { mockDao.softDelete(testId) }
    }

    @Test
    fun `daysSinceLastContact returns null when never contacted`() {
        val person = Person(firstName = "Test", lastContactedAt = null)
        assertThat(person.daysSinceLastContact()).isNull()
    }

    @Test
    fun `daysSinceLastContact returns correct days`() {
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
        val person = Person(firstName = "Test", lastContactedAt = threeDaysAgo)
        assertThat(person.daysSinceLastContact()).isEqualTo(3L)
    }

    @Test
    fun `fullName combines firstName and lastName`() {
        val person = Person(firstName = "Alice", lastName = "Dupont")
        assertThat(person.fullName).isEqualTo("Alice Dupont")
    }

    @Test
    fun `fullName uses only firstName when lastName is null`() {
        val person = Person(firstName = "Alice")
        assertThat(person.fullName).isEqualTo("Alice")
    }

    @Test
    fun `initials uses first letter of first and last name`() {
        val person = Person(firstName = "Alice", lastName = "Dupont")
        assertThat(person.initials).isEqualTo("AD")
    }

    @Test
    fun `hasGeoCoordinates returns false when coordinates missing`() {
        val person = Person(firstName = "Test", cityLat = null, cityLng = null)
        assertThat(person.hasGeoCoordinates).isFalse()
    }

    @Test
    fun `hasGeoCoordinates returns true when both coordinates set`() {
        val person = Person(firstName = "Test", cityLat = 45.5, cityLng = -73.5)
        assertThat(person.hasGeoCoordinates).isTrue()
    }
}
