package com.jtr.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.PersonDao
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v7.1.43 — RÉCIPROCITÉ DES RELATIONS, bout en bout : [PersonRepository.syncMirrorRelations]
 * sur la VRAIE base Room de l'app (DAO, transaction, résolution par identifiant), tel que le
 * chemin d'exécution des ViewModels Add/Edit l'emprunte.
 *
 * Sûreté : l'APK de test est `com.jtr.app.debug` (applicationIdSuffix) — base et données
 * DISTINCTES de la production `com.jtr.app`, y compris à la désinstallation par Gradle.
 * Chaque test purge malgré tout les fiches qu'il a créées ([tearDown]).
 *
 * Prouve les 4 corrections : B1 (inverse réel), B2 (suppression du miroir), B3 (mère→père
 * ne détruit pas le miroir), B4 (aucun doublon) — plus la non-régression des symétriques et
 * la préservation des relations importées (que l'import ne réciproque pas, par design).
 */
@RunWith(AndroidJUnit4::class)
class MirrorRelationsTest {

    private lateinit var repo: PersonRepository
    private lateinit var dao: PersonDao
    private val created = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repo = PersonRepository(context)
        dao = AppDatabase.getInstance(context).personDao()
    }

    @After
    fun tearDown() = runBlocking {
        created.forEach { dao.hardDelete(it) }
    }

    /** Insère une fiche jetable (nom improbable ⇒ aucune ambiguïté avec un contact existant). */
    private suspend fun insert(name: String, relations: List<DynamicLine> = emptyList()): Person {
        val person = Person(
            id = "mirror-test-$name",
            firstName = "ZzTest$name",
            relationLines = relations.takeIf { it.isNotEmpty() },
        )
        dao.insert(person)
        created += person.id
        return person
    }

    private fun relation(target: Person, label: String) =
        DynamicLine(value = target.fullName, label = label, linkedPersonId = target.id)

    private suspend fun linesOf(person: Person): List<DynamicLine> =
        dao.getById(person.id)?.relationLines.orEmpty()

    @Test
    fun b1_childRelation_mirrorsAsParent_notChild() = runBlocking {
        // LE BUG SIGNALÉ : « Johan → enfant Maxime » doit afficher « parent » chez Maxime.
        val maxime = insert("Maxime")
        val johan = insert("Johan", listOf(relation(maxime, "child")))
        repo.syncMirrorRelations(johan, previousLines = null)

        val mirror = linesOf(maxime).single()
        assertEquals("parent", mirror.label)
        assertEquals(johan.id, mirror.linkedPersonId)
        assertEquals(johan.fullName, mirror.value)
    }

    @Test
    fun b1_fatherRelation_stillMirrorsAsChild() = runBlocking {
        // Non-régression : le miroir déjà correct avant v7.1.43 le reste.
        val maxime = insert("Maxime")
        val johan = insert("Johan", listOf(relation(maxime, "father")))
        repo.syncMirrorRelations(johan, previousLines = null)

        assertEquals("child", linesOf(maxime).single().label)
    }

    @Test
    fun b1_managerRelation_mirrorsAsEmployee() = runBlocking {
        val maxime = insert("Maxime")
        val johan = insert("Johan", listOf(relation(maxime, "manager")))
        repo.syncMirrorRelations(johan, previousLines = null)

        assertEquals("employee", linesOf(maxime).single().label)
    }

    @Test
    fun symmetricRelations_areUnchanged() = runBlocking {
        val maxime = insert("Maxime")
        val johan = insert("Johan", listOf(relation(maxime, "friend"), relation(maxime, "spouse")))
        repo.syncMirrorRelations(johan, previousLines = null)

        assertEquals(setOf("friend", "spouse"), linesOf(maxime).map { it.label }.toSet())
    }

    @Test
    fun b2_removingRelation_removesTheMirror() = runBlocking {
        val maxime = insert("Maxime")
        val before = listOf(relation(maxime, "child"))
        val johan = insert("Johan", before)
        repo.syncMirrorRelations(johan, previousLines = null)
        assertEquals("parent", linesOf(maxime).single().label)

        // Johan supprime sa ligne : le miroir « parent » ne doit pas rester en fantôme.
        val emptied = johan.copy(relationLines = null)
        dao.update(emptied)
        repo.syncMirrorRelations(emptied, previousLines = before)

        assertTrue(linesOf(maxime).isEmpty())
    }

    @Test
    fun b2_removingChildRelation_alsoRemovesAGenderedMirror() = runBlocking {
        // Le miroir d'en face peut être « mère » (saisi à la main) : même classe d'équivalence.
        val johan = insert("Johan")
        val maxime = insert("Maxime", listOf(relation(johan, "mother")))
        val before = listOf(relation(maxime, "child"))
        val withChild = johan.copy(relationLines = before)
        dao.update(withChild)

        val emptied = johan.copy(relationLines = null)
        dao.update(emptied)
        repo.syncMirrorRelations(emptied, previousLines = before)

        assertTrue(linesOf(maxime).isEmpty())
    }

    @Test
    fun b3_switchingMotherToFather_keepsTheChildMirror() = runBlocking {
        val maxime = insert("Maxime")
        val before = listOf(relation(maxime, "mother"))
        val johan = insert("Johan", before)
        repo.syncMirrorRelations(johan, previousLines = null)
        assertEquals("child", linesOf(maxime).single().label)

        // Correction du type côté Johan : le miroir de Maxime doit SURVIVRE (bug B3).
        val corrected = johan.copy(relationLines = listOf(relation(maxime, "father")))
        dao.update(corrected)
        repo.syncMirrorRelations(corrected, previousLines = before)

        val mirror = linesOf(maxime).single()
        assertEquals("child", mirror.label)
        assertEquals(johan.id, mirror.linkedPersonId)
    }

    @Test
    fun b4_manuallyTypedMirror_isNeverDuplicated() = runBlocking {
        // Maxime porte DÉJÀ « mère : Johan » saisi à la main ; Johan ajoute « enfant : Maxime ».
        val johan = insert("Johan")
        val maxime = insert("Maxime", listOf(relation(johan, "mother")))
        val withChild = johan.copy(relationLines = listOf(relation(maxime, "child")))
        dao.update(withChild)
        repo.syncMirrorRelations(withChild, previousLines = null)

        val lines = linesOf(maxime)
        assertEquals(1, lines.size)
        assertEquals("mother", lines.single().label) // ni doublon, ni écrasement du choix de l'utilisateur
    }

    // ── v7.1.44 (P2) — catalogue étendu ──────────────────────────────────────
    // La mécanique de réconciliation est déjà prouvée ci-dessus ; on vérifie ici que les
    // nouveaux rôles la traversent correctement de bout en bout.

    @Test
    fun p2_teacherRelation_mirrorsAsStudent_andBack() = runBlocking {
        val maxime = insert("Maxime")
        val johan = insert("Johan", listOf(relation(maxime, "teacher")))
        repo.syncMirrorRelations(johan, previousLines = null)
        assertEquals("student", linesOf(maxime).single().label)

        // Sens inverse, sur une autre paire de fiches.
        val lea = insert("Lea")
        val nour = insert("Nour", listOf(relation(lea, "student")))
        repo.syncMirrorRelations(nour, previousLines = null)
        assertEquals("teacher", linesOf(lea).single().label)
    }

    @Test
    fun p2_neighborRelation_isSymmetric() = runBlocking {
        val maxime = insert("Maxime")
        val johan = insert("Johan", listOf(relation(maxime, "neighbor")))
        repo.syncMirrorRelations(johan, previousLines = null)

        assertEquals("neighbor", linesOf(maxime).single().label)
    }

    @Test
    fun p2_bestFriendOverExistingFriend_createsNoDuplicate() = runBlocking {
        // Maxime a déjà « ami : Johan » ; Johan déclare « meilleur ami : Maxime ».
        val johan = insert("Johan")
        val maxime = insert("Maxime", listOf(relation(johan, "friend")))
        val withBestFriend = johan.copy(relationLines = listOf(relation(maxime, "best_friend")))
        dao.update(withBestFriend)
        repo.syncMirrorRelations(withBestFriend, previousLines = null)

        val lines = linesOf(maxime)
        assertEquals(1, lines.size)
        assertEquals("friend", lines.single().label) // l'intensité choisie par Maxime est respectée
    }

    @Test
    fun importedRelation_isNotDeletedByAnUnrelatedEdit() = runBlocking {
        // L'import ne crée pas de miroir (par design) : la ligne importée de Maxime vers Johan
        // n'a pas de contrepartie et ne doit pas être emportée par une édition sans rapport.
        val johan = insert("Johan")
        val maxime = insert("Maxime", listOf(relation(johan, "mother")))
        val withFriend = johan.copy(relationLines = listOf(relation(maxime, "friend")))
        dao.update(withFriend)
        repo.syncMirrorRelations(withFriend, previousLines = null)

        assertEquals(setOf("mother", "friend"), linesOf(maxime).map { it.label }.toSet())
    }
}
