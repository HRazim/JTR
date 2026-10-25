package com.jtr.app.ui.gallery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * v7.1.110 — contrat d'écriture du « Done » de l'éditeur de date ([commitDateIfChanged]) :
 *
 *  - CHANGEMENT → UNE écriture, avec la valeur réglée (c'est elle qui stampe `modifiedAt`,
 *    une seule fois) ;
 *  - SANS changement → AUCUNE écriture, aucun stamp (rouvrir l'éditeur pour regarder est
 *    un no-op total) ;
 *  - la FERMETURE sans « Done » n'appelle pas cette fonction du tout : l'état local
 *    `pending` est jeté avec le composable — l'annulation est structurelle, il n'existe
 *    aucun autre chemin d'écriture depuis les molettes (plus d'écriture par cran).
 */
class ImageDetailsDateCommitTest {

    @Test
    fun `changed value commits once with the pending value`() {
        val commits = mutableListOf<Long>()
        val committed = commitDateIfChanged(original = 1_000L, pending = 2_000L) { commits += it }
        assertThat(committed).isTrue()
        assertThat(commits).containsExactly(2_000L)
    }

    @Test
    fun `unchanged value never commits`() {
        val commits = mutableListOf<Long>()
        val committed = commitDateIfChanged(original = 1_000L, pending = 1_000L) { commits += it }
        assertThat(committed).isFalse()
        assertThat(commits).isEmpty()
    }

    @Test
    fun `scrolling away and back to the original value is a no-op`() {
        // L'utilisateur fait défiler les roues puis revient à la valeur de départ : au
        // « Done », pending == original → rien ne part, `modifiedAt` n'est pas stampé.
        val commits = mutableListOf<Long>()
        var pending = 1_000L
        pending = 5_000L // crans aller…
        pending = 1_000L // …et retour
        assertThat(commitDateIfChanged(original = 1_000L, pending = pending) { commits += it })
            .isFalse()
        assertThat(commits).isEmpty()
    }
}
