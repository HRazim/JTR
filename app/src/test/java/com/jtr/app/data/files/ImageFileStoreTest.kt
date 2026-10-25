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
 * v7.1.105 (lot C1) — contrat du garde-fou [ImageFileStore] :
 *
 *  - NORMALISATION : chemin brut, URI `file://` et URI à séquences `%xx` désignent le même
 *    fichier (71 des 88 `photoUri` réels sont des URI `file://` — l'audit lot C) ;
 *  - POSSESSION : seule une cible dans `photos/`, `gallery/` ou `crops/` de `filesDir` est
 *    supprimable ; la racine (`mbgl-offline.db`…), l'extérieur et les `../` sont REFUSÉS ;
 *  - ROBUSTESSE : fichier absent, `null`, blanc, `content://` → no-op sans erreur ;
 *  - REMPLACEMENT : [ImageFileStore.deleteReplacedFile] ne supprime que l'ANCIEN fichier,
 *    et rien quand la valeur n'a pas changé — même sous deux formes différentes.
 */
class ImageFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
    }

    private fun owned(dir: String, name: String): File {
        val d = File(filesDir, dir).apply { mkdirs() }
        return File(d, name).apply { writeText("img") }
    }

    // ── Normalisation ───────────────────────────────────────────────────────────

    @Test
    fun `raw path and file uri resolve to the same native path`() {
        val a = owned("photos", "profile_a.jpg")
        assertThat(ImageFileStore.normalizeToPath(a.absolutePath)).isEqualTo(a.absolutePath)
        assertThat(ImageFileStore.normalizeToPath(a.toURI().toString())).isEqualTo(a.absolutePath)
        assertThat(ImageFileStore.normalizeToPath(null)).isNull()
        assertThat(ImageFileStore.normalizeToPath("   ")).isNull()
    }

    @Test
    fun `deletes a raw path inside photos`() {
        val f = owned("photos", "profile_raw.jpg")
        assertThat(ImageFileStore.deleteFileIfOwned(context, f.absolutePath)).isTrue()
        assertThat(f.exists()).isFalse()
    }

    @Test
    fun `deletes a file uri inside photos — the audited 71-of-88 shape`() {
        val f = owned("photos", "restore_uri.jpg")
        assertThat(ImageFileStore.deleteFileIfOwned(context, f.toURI().toString())).isTrue()
        assertThat(f.exists()).isFalse()
    }

    @Test
    fun `decodes percent sequences in a file uri`() {
        val f = owned("photos", "with space.jpg")
        val uri = f.toURI().toString() // contient « %20 » pour l'espace
        assertThat(uri).contains("%20")
        assertThat(ImageFileStore.deleteFileIfOwned(context, uri)).isTrue()
        assertThat(f.exists()).isFalse()
    }

    @Test
    fun `gallery and crops are owned directories too`() {
        val g = owned("gallery", "gallery_x.jpg")
        val c = owned("crops", "crop_x.jpg")
        assertThat(ImageFileStore.deleteFileIfOwned(context, g.absolutePath)).isTrue()
        assertThat(ImageFileStore.deleteFileIfOwned(context, c.absolutePath)).isTrue()
        assertThat(g.exists()).isFalse()
        assertThat(c.exists()).isFalse()
    }

    // ── Possession : tout le reste est refusé ───────────────────────────────────

    @Test
    fun `refuses filesDir root — mbgl-offline db stays`() {
        val f = File(filesDir, "mbgl-offline.db").apply { writeText("map cache") }
        assertThat(ImageFileStore.deleteFileIfOwned(context, f.absolutePath)).isFalse()
        assertThat(f.exists()).isTrue()
    }

    @Test
    fun `refuses a path outside filesDir`() {
        val outside = tmp.newFile("elsewhere.jpg")
        assertThat(ImageFileStore.deleteFileIfOwned(context, outside.absolutePath)).isFalse()
        assertThat(outside.exists()).isTrue()
    }

    @Test
    fun `refuses dot-dot traversal out of an owned directory`() {
        val target = File(filesDir, "mbgl-offline.db").apply { writeText("map cache") }
        val sneaky = File(filesDir, "photos").apply { mkdirs() }
            .resolve("..${File.separator}mbgl-offline.db").path
        assertThat(ImageFileStore.deleteFileIfOwned(context, sneaky)).isFalse()
        assertThat(target.exists()).isTrue()
    }

    @Test
    fun `missing file, blank and content uri are silent no-ops`() {
        assertThat(ImageFileStore.deleteFileIfOwned(context, File(filesDir, "photos/absent.jpg").absolutePath))
            .isFalse()
        assertThat(ImageFileStore.deleteFileIfOwned(context, null)).isFalse()
        assertThat(ImageFileStore.deleteFileIfOwned(context, "  ")).isFalse()
        assertThat(ImageFileStore.deleteFileIfOwned(context, "content://media/external/images/1"))
            .isFalse()
    }

    // ── Remplacement ────────────────────────────────────────────────────────────

    @Test
    fun `replaced file goes away, the new one stays`() {
        val old = owned("photos", "profile_old.jpg")
        val new = owned("photos", "profile_new.jpg")
        assertThat(ImageFileStore.deleteReplacedFile(context, old.absolutePath, new.absolutePath))
            .isTrue()
        assertThat(old.exists()).isFalse()
        assertThat(new.exists()).isTrue()
    }

    @Test
    fun `unchanged value never deletes — even across raw and uri forms`() {
        val f = owned("photos", "profile_same.jpg")
        val uriForm = f.toURI().toString()
        // Même fichier sous deux formes : PAS une « nouvelle » photo, rien n'est supprimé.
        assertThat(ImageFileStore.deleteReplacedFile(context, f.absolutePath, f.absolutePath))
            .isFalse()
        assertThat(f.exists()).isTrue()
        assertThat(ImageFileStore.deleteReplacedFile(context, uriForm, f.absolutePath)).isFalse()
        assertThat(f.exists()).isTrue()
    }

    @Test
    fun `null old value is a no-op`() {
        val new = owned("photos", "profile_first.jpg")
        assertThat(ImageFileStore.deleteReplacedFile(context, null, new.absolutePath)).isFalse()
        assertThat(new.exists()).isTrue()
    }
}
