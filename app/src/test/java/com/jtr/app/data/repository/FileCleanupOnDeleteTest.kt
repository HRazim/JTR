package com.jtr.app.data.repository

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.CategoryDao
import com.jtr.app.data.local.GalleryImageDao
import com.jtr.app.data.local.PersonDao
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v7.1.105 (lot C1) — les REPOSITORIES effacent les fichiers des entités supprimées et les
 * anciens fichiers remplacés (vrais fichiers dans un `filesDir` temporaire, DAOs mockés) :
 *
 *  - [PersonRepository.hardDelete] : photo de profil (même en URI `file://`) ET fichiers de
 *    galerie partent, la ligne est bien hard-deletée — et un fichier hors dossier possédé
 *    SURVIT (garde-fou) ;
 *  - [PersonRepository.update] : l'ancienne photo remplacée part, la nouvelle reste ;
 *    photo inchangée → rien ne bouge ;
 *  - [CategoryRepository.hardDeleteCategory] / [CategoryRepository.update] : même contrat
 *    pour l'image de catégorie.
 */
class FileCleanupOnDeleteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var app: Application
    private lateinit var personDao: PersonDao
    private lateinit var galleryDao: GalleryImageDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        personDao = mockk(relaxed = true)
        galleryDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        val db = mockk<AppDatabase>(relaxed = true)
        every { db.personDao() } returns personDao
        every { db.galleryImageDao() } returns galleryDao
        every { db.categoryDao() } returns categoryDao
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db

        app = mockk(relaxed = true)
        every { app.applicationContext } returns app
        every { app.filesDir } returns filesDir
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
    }

    private fun owned(dir: String, name: String): File {
        val d = File(filesDir, dir).apply { mkdirs() }
        return File(d, name).apply { writeText("img") }
    }

    private fun person(id: String, photoUri: String?) =
        Person(id = id, firstName = "ZZ", photoUri = photoUri)

    // ── Contact ─────────────────────────────────────────────────────────────────

    @Test
    fun `hardDelete removes the profile photo and the gallery files`() {
        val photo = owned("photos", "profile_zz.jpg")
        val g1 = owned("gallery", "gallery_zz1.jpg")
        val g2 = owned("gallery", "gallery_zz2.jpg")
        // La photo est volontairement en URI file:// — la forme majoritaire de l'audit.
        coEvery { personDao.getById("zz") } returns person("zz", photo.toURI().toString())
        coEvery { galleryDao.pathsForPerson("zz") } returns listOf(g1.absolutePath, g2.absolutePath)

        runBlocking { PersonRepository(app).hardDelete("zz") }

        coVerify { personDao.hardDelete("zz") }
        assertThat(photo.exists()).isFalse()
        assertThat(g1.exists()).isFalse()
        assertThat(g2.exists()).isFalse()
    }

    @Test
    fun `hardDelete never touches a file outside the owned directories`() {
        val outside = File(filesDir, "mbgl-offline.db").apply { writeText("map cache") }
        coEvery { personDao.getById("zz") } returns person("zz", outside.absolutePath)
        coEvery { galleryDao.pathsForPerson("zz") } returns emptyList()

        runBlocking { PersonRepository(app).hardDelete("zz") }

        assertThat(outside.exists()).isTrue()
    }

    @Test
    fun `emptying the trash removes every deleted person file read beforehand`() {
        val p1 = owned("photos", "profile_t1.jpg")
        val g1 = owned("gallery", "gallery_t1.jpg")
        coEvery { personDao.getDeletedPhotoUris() } returns listOf(p1.toURI().toString())
        coEvery { galleryDao.pathsForDeletedPersons() } returns listOf(g1.absolutePath)

        runBlocking { PersonRepository(app).hardDeleteAllDeleted() }

        coVerify { personDao.hardDeleteAllDeleted() }
        assertThat(p1.exists()).isFalse()
        assertThat(g1.exists()).isFalse()
    }

    @Test
    fun `update deletes the replaced photo only — unchanged photo stays`() {
        val old = owned("photos", "profile_old.jpg")
        val new = owned("photos", "profile_new.jpg")
        coEvery { personDao.getById("zz") } returns person("zz", old.absolutePath)

        runBlocking { PersonRepository(app).update(person("zz", new.absolutePath)) }
        assertThat(old.exists()).isFalse()
        assertThat(new.exists()).isTrue()

        // Deuxième update SANS changement : la photo courante survit.
        coEvery { personDao.getById("zz") } returns person("zz", new.absolutePath)
        runBlocking { PersonRepository(app).update(person("zz", new.absolutePath)) }
        assertThat(new.exists()).isTrue()
    }

    // ── Catégorie ───────────────────────────────────────────────────────────────

    private fun category(id: String, imagePath: String?) =
        Category(id = id, name = "ZZ", imagePath = imagePath)

    @Test
    fun `hardDeleteCategory removes its image file`() {
        val img = owned("photos", "category_zz.jpg")
        coEvery { categoryDao.getById("zzc") } returns category("zzc", img.absolutePath)

        runBlocking { CategoryRepository(app).hardDeleteCategory("zzc") }

        coVerify { categoryDao.hardDelete("zzc") }
        assertThat(img.exists()).isFalse()
    }

    @Test
    fun `category update deletes the replaced image and keeps the new one`() {
        val old = owned("photos", "category_old.jpg")
        val new = owned("photos", "category_new.jpg")
        coEvery { categoryDao.getById("zzc") } returns category("zzc", old.absolutePath)

        runBlocking { CategoryRepository(app).update(category("zzc", new.absolutePath)) }

        assertThat(old.exists()).isFalse()
        assertThat(new.exists()).isTrue()
    }
}
