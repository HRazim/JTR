package com.jtr.app.ui.person

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.data.local.PersonDao
import com.jtr.app.data.local.SocialLinkDao
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.worker.ReminderScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests du chemin d'AUTO-SAVE du brouillon de [AddPersonViewModel] (v7.1.68).
 *
 * Décision produit : on CONSERVE le contact auto-sauvé, mais il doit être COHÉRENT :
 *  - UNE SEULE ligne Person par brouillon (id stable) même après N flushes (zéro doublon) ;
 *  - le lien de catégorie est créé DÈS l'auto-save quand l'écran est ouvert dans une catégorie
 *    (fini l'orphelin visible à l'accueil mais absent de sa catégorie), de manière idempotente ;
 *  - `createdAt` FIXÉ une fois puis préservé entre les flushes ; `updatedAt` ne recule jamais ;
 *  - un brouillon sans prénom ne crée RIEN ;
 *  - un Enregistrer explicite après auto-save ne duplique pas (même id, join idempotent).
 *
 * On pilote le VRAI ViewModel + le VRAI PersonRepository contre des DAO en mémoire (AppDatabase
 * mocké). L'auto-save est déclenché via le debounce 700 ms en temps virtuel (Dispatchers.Main de test).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddPersonDraftPersistTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // ── "Base" en mémoire ──────────────────────────────────────────────────────
    private val persons = linkedMapOf<String, Person>()       // table persons (id -> ligne)
    private val joins = linkedSetOf<Pair<String, String>>()   // person_category_join (personId,categoryId)

    private lateinit var personDao: PersonDao
    private lateinit var categoryDao: PersonCategoryDao

    /** Visible dans une catégorie = INNER JOIN actif + lien présent. */
    private fun visibleInCategory(categoryId: String): List<Person> =
        joins.filter { it.second == categoryId }
            .mapNotNull { persons[it.first] }
            .filter { it.deletedAt == null }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // AutoSaveController observe ProcessLifecycleOwner → addObserver exige le main thread
        // Android (Looper), absent en JVM. On fait exécuter les tâches Arch en ligne (équivaut à
        // InstantTaskExecutorRule) pour que l'observateur s'enregistre sans crash.
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        personDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        val socialDao = mockk<SocialLinkDao>(relaxed = true)

        // persons : insert = REPLACE (upsert par id) ; hardDelete + FK CASCADE sur les liens.
        coEvery { personDao.insert(any()) } answers {
            val p = firstArg<Person>(); persons[p.id] = p
        }
        coEvery { personDao.hardDelete(any()) } answers {
            val id = firstArg<String>(); persons.remove(id); joins.removeAll { it.first == id }
        }
        every { personDao.getAllActive() } returns flowOf(emptyList())
        coEvery { personDao.getById(any()) } answers { persons[firstArg<String>()] }

        // joins : insert/insertAll = OnConflict.IGNORE → le Set dédoublonne (idempotence).
        coEvery { categoryDao.insert(any()) } answers {
            val j = firstArg<PersonCategoryJoin>(); joins.add(j.personId to j.categoryId)
        }
        coEvery { categoryDao.insertAll(any()) } answers {
            firstArg<List<PersonCategoryJoin>>().forEach { joins.add(it.personId to it.categoryId) }
        }

        val db = mockk<AppDatabase>(relaxed = true)
        every { db.personDao() } returns personDao
        every { db.personCategoryDao() } returns categoryDao
        every { db.socialLinkDao() } returns socialDao
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db

        // Pas d'alarmes réelles dans un test JVM.
        mockkObject(ReminderScheduler)
        coEvery { ReminderScheduler.rescheduleAll(any()) } answers {}
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
        unmockkObject(ReminderScheduler)
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    /** Application mockée : fournit un SharedPreferences inerte (defaults) et se retourne comme contexte. */
    private fun fakeApp(): Application {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getBoolean(any(), any()) } answers { secondArg() }
        val app = mockk<Application>(relaxed = true)
        every { app.getSharedPreferences(any(), any()) } returns prefs
        every { app.applicationContext } returns app
        return app
    }

    private fun newVm(categoryId: String?): AddPersonViewModel {
        val handle = if (categoryId != null) SavedStateHandle(mapOf("categoryId" to categoryId))
        else SavedStateHandle()
        return AddPersonViewModel(fakeApp(), handle)
    }

    /** Déclenche l'auto-save (debounce 700 ms) et le laisse s'exécuter. */
    private fun kotlinx.coroutines.test.TestScope.flushDebounce() {
        advanceTimeBy(800)
    }

    @Test
    fun autosave_inCategory_createsOnePersonAndOneJoin_visibleInCategory() = runTest(dispatcher) {
        val vm = newVm("cat1")
        vm.onFirstNameChanged("ZZClaude")
        flushDebounce()

        assertThat(persons).hasSize(1)
        assertThat(joins).hasSize(1)
        val saved = persons.values.first()
        assertThat(saved.firstName).isEqualTo("ZZClaude")
        // Plus d'orphelin : le contact apparaît bien DANS sa catégorie.
        assertThat(visibleInCategory("cat1").map { it.id }).containsExactly(saved.id)
    }

    @Test
    fun autosave_repeatedFlushes_sameDraft_noDuplicate() = runTest(dispatcher) {
        val vm = newVm("cat1")
        vm.onFirstNameChanged("ZZClaude")
        flushDebounce()
        vm.onLastNameChanged("Test")        // 2e rafale
        flushDebounce()
        vm.onCityChanged("Paris")           // 3e rafale
        flushDebounce()

        // Exigence n°1 : une SEULE ligne et un SEUL lien, quel que soit le nombre de flushes.
        assertThat(persons).hasSize(1)
        assertThat(joins).hasSize(1)
    }

    @Test
    fun autosave_preservesCreatedAt_advancesUpdatedAt() = runTest(dispatcher) {
        val vm = newVm("cat1")
        vm.onFirstNameChanged("ZZClaude")
        flushDebounce()
        val first = persons.values.first()

        Thread.sleep(3) // laisse l'horloge RÉELLE avancer (createdAt/updatedAt = millis réels)
        vm.onLastNameChanged("Test")
        flushDebounce()
        val second = persons.values.first()

        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.createdAt).isEqualTo(first.createdAt)   // FIXÉ une fois
        assertThat(second.updatedAt).isAtLeast(first.updatedAt)   // ne recule jamais
        assertThat(second.updatedAt).isAtLeast(second.createdAt)
    }

    @Test
    fun autosave_blankFirstName_createsNothing() = runTest(dispatcher) {
        val vm = newVm("cat1")
        vm.onCityChanged("Paris")           // du contenu, mais PAS de prénom
        flushDebounce()

        assertThat(persons).isEmpty()
        assertThat(joins).isEmpty()
    }

    @Test
    fun explicitSave_afterAutosave_noDuplicate_keepsCreatedAt() = runTest(dispatcher) {
        val vm = newVm("cat1")
        vm.onFirstNameChanged("ZZClaude")
        flushDebounce()
        val afterAutosave = persons.values.first()

        var done = false
        vm.savePerson { done = true }
        advanceTimeBy(100)

        assertThat(done).isTrue()
        assertThat(persons).hasSize(1)                              // pas de doublon
        assertThat(joins).hasSize(1)
        val afterSave = persons.values.first()
        assertThat(afterSave.id).isEqualTo(afterAutosave.id)        // même id réutilisé
        assertThat(afterSave.createdAt).isEqualTo(afterAutosave.createdAt) // createdAt préservé
    }

    @Test
    fun autosave_noCategory_createsPersonWithoutJoin() = runTest(dispatcher) {
        val vm = newVm(null)
        vm.onFirstNameChanged("ZZSolo")
        flushDebounce()

        assertThat(persons).hasSize(1)
        assertThat(joins).isEmpty()         // aucune catégorie cible → aucun lien
    }
}
