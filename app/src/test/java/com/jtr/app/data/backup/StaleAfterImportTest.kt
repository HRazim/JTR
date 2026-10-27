package com.jtr.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.files.ImageFileStore
import org.junit.Test

/**
 * v7.1.117 (orphelins partie 2) — le cœur pur du hook d'import : seuls les chemins
 * DÉ-RÉFÉRENCÉS par l'import (présents avant, absents après) sont candidats à la
 * suppression. La protection vient de l'ensemble de référence, pas d'une heuristique.
 */
class StaleAfterImportTest {

    @Test
    fun `remplacement - l ancien chemin devient stale, le nouveau est protege`() {
        val pre = setOf("/data/photos/profile_old.jpg", "/data/photos/category_a.jpg")
        val post = setOf("/data/photos/restore_new.jpg", "/data/photos/category_a.jpg")
        assertThat(staleAfterImport(pre, post))
            .containsExactly("/data/photos/profile_old.jpg")
    }

    @Test
    fun `contact nouveau - aucun ancien fichier, rien a supprimer`() {
        val pre = emptySet<String>()
        val post = setOf("/data/photos/restore_new.jpg")
        assertThat(staleAfterImport(pre, post)).isEmpty()
    }

    @Test
    fun `chemin partage encore reference post-import - preserve`() {
        val shared = "/data/photos/shared.jpg"
        val pre = setOf(shared, "/data/gallery/old.jpg")
        val post = setOf(shared, "/data/photos/restore_new.jpg")
        assertThat(staleAfterImport(pre, post)).containsExactly("/data/gallery/old.jpg")
    }

    @Test
    fun `chemin identique pre et post - preserve`() {
        val same = setOf("/data/photos/a.jpg", "/data/gallery/b.jpg")
        assertThat(staleAfterImport(same, same)).isEmpty()
    }

    @Test
    fun `import sans remplacement - ensembles disjoints uniquement cote post`() {
        val pre = setOf("/data/photos/a.jpg")
        val post = setOf("/data/photos/a.jpg", "/data/photos/restore_b.jpg")
        assertThat(staleAfterImport(pre, post)).isEmpty()
    }

    @Test
    fun `formes file-uri et chemin brut - la normalisation evite un faux stale`() {
        // La capture normalise AVANT le diff : une même image référencée en `file://`
        // avant l'import et en chemin brut après (ou l'inverse) ne doit JAMAIS ressortir.
        // Portable JVM/Android : l'URI est DÉRIVÉE du chemin natif (sur Android les deux
        // écritures sont `/data/…` ; sur la JVM Windows des tests, le séparateur diffère).
        val raw = java.io.File("/data/photos/same.jpg").absolutePath
        val asUri = java.io.File(raw).toURI().toString()
        val pre = setOf(ImageFileStore.normalizeToPath(asUri)!!)
        val post = setOf(ImageFileStore.normalizeToPath(raw)!!)
        assertThat(staleAfterImport(pre, post)).isEmpty()
    }
}
