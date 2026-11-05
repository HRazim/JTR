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
 * v7.1.145 — balayage au démarrage des `restore_*` orphelins. Rejoue la chaîne réelle
 * `scan` → [OrphanFileSweeper.restoreOrphansOnly] → `execute` (seule la lecture Room des
 * références est remplacée par une liste) et vérifie les 4 conditions :
 * référencé = CONSERVÉ (photo définitive), non référencé + ancien = SUPPRIMÉ,
 * récent = CONSERVÉ, hors `photos/restore_*` = CONSERVÉ.
 */
class OrphanRestoreStartupSweepTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var context: Context
    private val processStart = System.currentTimeMillis()

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
    }

    private fun file(dir: String, name: String, ageMs: Long = 60_000): File {
        val d = File(filesDir, dir).apply { mkdirs() }
        return File(d, name).apply {
            writeText("img-$name")
            setLastModified(processStart - ageMs)
        }
    }

    private fun sweep(references: List<String>): SweepResult = OrphanFileSweeper.execute(
        context,
        OrphanFileSweeper.restoreOrphansOnly(
            context, OrphanFileSweeper.scan(context, references, processStart)
        )
    )

    @Test
    fun `referenced restore files survive, unreferenced old ones are deleted`() {
        val referencedPath = file("photos", "restore_aaa_photo.jpg")
        val referencedUri = file("photos", "restore_bbb_photo.jpg")
        val orphan = file("photos", "restore_ccc_partial.jpg")

        val result = sweep(
            listOf(referencedPath.absolutePath, "file://" + referencedUri.absolutePath)
        )

        assertThat(referencedPath.exists()).isTrue()
        assertThat(referencedUri.exists()).isTrue()
        assertThat(orphan.exists()).isFalse()
        assertThat(result.deletedCount).isEqualTo(1)
    }

    @Test
    fun `restore file newer than the process start is never swept`() {
        val recent = file("photos", "restore_new_extracting.jpg", ageMs = -5_000)
        val atStart = file("photos", "restore_same_ms.jpg", ageMs = 0)

        val result = sweep(emptyList())

        assertThat(recent.exists()).isTrue()
        assertThat(atStart.exists()).isTrue()
        assertThat(result.deletedCount).isEqualTo(0)
    }

    @Test
    fun `only photos restore prefix is in scope - other orphans are left alone`() {
        val photoOrphanOtherName = file("photos", "profile_old.jpg")
        val galleryRestore = file("gallery", "restore_in_gallery.jpg")
        val cropRestore = file("crops", "restore_in_crops.jpg")
        val nested = file("photos/sub", "restore_nested.jpg")
        val rootFile = file("", "restore_root.jpg")
        val target = file("photos", "restore_target.jpg")

        val result = sweep(emptyList())

        assertThat(target.exists()).isFalse()
        listOf(photoOrphanOtherName, galleryRestore, cropRestore, nested, rootFile)
            .forEach { assertThat(it.exists()).isTrue() }
        assertThat(result.deletedCount).isEqualTo(1)
    }

    @Test
    fun `no photos directory is a clean no-op`() {
        val result = sweep(emptyList())
        assertThat(result.deletedCount).isEqualTo(0)
        assertThat(result.freedBytes).isEqualTo(0L)
    }

    @Test
    fun `filter keeps the byte total of the retained files only`() {
        val a = file("photos", "restore_a.jpg")
        file("photos", "other.jpg")
        val plan = OrphanFileSweeper.restoreOrphansOnly(
            context, OrphanFileSweeper.scan(context, emptyList(), processStart)
        )
        assertThat(plan.files.map { it.name }).containsExactly("restore_a.jpg")
        assertThat(plan.totalBytes).isEqualTo(a.length())
    }
}
