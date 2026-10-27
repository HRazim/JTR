package com.jtr.app.data.files

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v7.1.106 (lot C2) — contrat du balayage d'orphelins [OrphanFileSweeper] :
 *
 *  - RÉFÉRENCES : un fichier référencé par la base survit TOUJOURS au scan — y compris
 *    référencé par une ligne « en corbeille » (le piège n°1 du lot), et y compris quand
 *    la référence est une URI `file://` alors que le disque porte un chemin brut
 *    (comparaison CANONIQUE, 71 des 88 `photoUri` réels sont des URI) ;
 *  - GARDE mtime : un fichier créé/modifié PENDANT le scan n'est jamais candidat ;
 *  - PÉRIMÈTRE : seuls `photos/`, `gallery/`, `crops/` sont balayés — la racine de
 *    `filesDir` (`mbgl-offline.db`) et les autres dossiers ne sont JAMAIS listés ;
 *  - EXÉCUTION : `execute` ne touche QUE le plan, et re-passe chaque fichier par le
 *    garde-fou de possession (un intrus hors dossiers possédés resterait intact).
 */
class OrphanFileSweeperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var context: Context

    /** Borne « le scan démarre MAINTENANT » : tout fichier posé par [oldFile] lui est antérieur. */
    private val scanStart = System.currentTimeMillis()

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
    }

    /** Crée un fichier ANTÉRIEUR au scan (mtime posé une minute avant [scanStart]). */
    private fun oldFile(dir: String, name: String, content: String = "img"): File {
        val d = File(filesDir, dir).apply { mkdirs() }
        return File(d, name).apply {
            writeText(content)
            setLastModified(scanStart - 60_000)
        }
    }

    // ── Ensemble de référence ────────────────────────────────────────────────────

    @Test
    fun `referenced files survive, unreferenced files are planned`() {
        val active = oldFile("photos", "profile_active.jpg")
        val orphan = oldFile("photos", "restore_orphan.jpg")
        val plan = OrphanFileSweeper.scan(context, listOf(active.absolutePath), scanStart)
        assertThat(plan.files.map { it.canonicalPath }).containsExactly(orphan.canonicalPath)
        assertThat(plan.totalBytes).isEqualTo(orphan.length())
    }

    @Test
    fun `a trashed row's photo is a reference too — never an orphan`() {
        // La corbeille N'EST PAS filtrée : la référence d'une fiche soft-deleted arrive
        // dans la même liste que les actives, et son fichier est préservé.
        val trashed = oldFile("photos", "profile_trashed.jpg")
        val orphan = oldFile("photos", "restore_old.jpg")
        val plan = OrphanFileSweeper.scan(context, listOf(trashed.absolutePath), scanStart)
        assertThat(plan.files.map { it.canonicalPath }).containsExactly(orphan.canonicalPath)
        assertThat(trashed.exists()).isTrue()
    }

    @Test
    fun `a file uri reference protects the raw path on disk — canonical comparison`() {
        val referenced = oldFile("photos", "profile_uri.jpg")
        val orphan = oldFile("photos", "crop_left.jpg")
        // Référence sous forme URI `file://` (la forme des 71/88 mesurés) : le fichier
        // sur disque (chemin brut) doit être reconnu comme référencé.
        val plan = OrphanFileSweeper.scan(context, listOf(referenced.toURI().toString()), scanStart)
        assertThat(plan.files.map { it.canonicalPath }).containsExactly(orphan.canonicalPath)
    }

    @Test
    fun `blank and content uri references are ignored without breaking the scan`() {
        val orphan = oldFile("gallery", "gallery_x.jpg")
        val plan = OrphanFileSweeper.scan(
            context, listOf("  ", "content://media/external/images/1"), scanStart
        )
        assertThat(plan.files.map { it.canonicalPath }).containsExactly(orphan.canonicalPath)
    }

    // ── Garde mtime < début du scan ──────────────────────────────────────────────

    @Test
    fun `a file created during the scan is never a candidate`() {
        val fresh = oldFile("crops", "crop_fresh.jpg").apply {
            setLastModified(scanStart + 5_000) // « créé pendant le scan »
        }
        val plan = OrphanFileSweeper.scan(context, emptyList(), scanStart)
        assertThat(plan.files).isEmpty()
        assertThat(fresh.exists()).isTrue()
    }

    // ── Périmètre : photos-gallery-crops seulement ───────────────────────────────

    @Test
    fun `filesDir root and foreign directories are never listed`() {
        File(filesDir, "mbgl-offline.db").apply {
            writeText("map cache"); setLastModified(scanStart - 60_000)
        }
        File(filesDir, "profileInstalled").apply {
            writeText(""); setLastModified(scanStart - 60_000)
        }
        oldFile("other_dir", "stray.jpg")
        val orphan = oldFile("crops", "crop_old.jpg")
        val plan = OrphanFileSweeper.scan(context, emptyList(), scanStart)
        assertThat(plan.files.map { it.canonicalPath }).containsExactly(orphan.canonicalPath)
    }

    @Test
    fun `nested files inside an owned directory are swept and compared canonically`() {
        val referenced = oldFile("gallery/person-1", "gallery_kept.jpg")
        val orphan = oldFile("gallery/person-2", "gallery_gone.jpg")
        val plan = OrphanFileSweeper.scan(context, listOf(referenced.absolutePath), scanStart)
        assertThat(plan.files.map { it.canonicalPath }).containsExactly(orphan.canonicalPath)
    }

    // ── Exécution : le plan, rien que le plan ────────────────────────────────────

    @Test
    fun `execute deletes exactly the plan and reports freed bytes`() {
        val kept = oldFile("photos", "profile_kept.jpg")
        val o1 = oldFile("photos", "restore_1.jpg", "aaaa")
        val o2 = oldFile("gallery", "gallery_2.jpg", "bbbbbb")
        val plan = OrphanFileSweeper.scan(context, listOf(kept.absolutePath), scanStart)
        val expectedBytes = o1.length() + o2.length()

        val result = OrphanFileSweeper.execute(context, plan)

        assertThat(result.deletedCount).isEqualTo(2)
        assertThat(result.freedBytes).isEqualTo(expectedBytes)
        assertThat(o1.exists()).isFalse()
        assertThat(o2.exists()).isFalse()
        assertThat(kept.exists()).isTrue()
    }

    @Test
    fun `execute refuses an intruder outside the owned directories`() {
        // Même si un chemin hors possession se glissait dans un plan, deleteFileIfOwned
        // le refuse : le fichier reste et n'est pas compté.
        val intruder = File(filesDir, "mbgl-offline.db").apply { writeText("map cache") }
        val result = OrphanFileSweeper.execute(
            context, SweepPlan(listOf(intruder), intruder.length())
        )
        assertThat(result.deletedCount).isEqualTo(0)
        assertThat(result.freedBytes).isEqualTo(0L)
        assertThat(intruder.exists()).isTrue()
    }

    @Test
    fun `an empty plan is a no-op`() {
        val result = OrphanFileSweeper.execute(context, SweepPlan(emptyList(), 0L))
        assertThat(result.deletedCount).isEqualTo(0)
        assertThat(result.freedBytes).isEqualTo(0L)
    }
}
