package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.jtr.app.domain.model.NoteSection
import com.jtr.app.domain.model.combinedText
import com.jtr.app.domain.model.splitCombinedText
import com.jtr.app.domain.model.withCombinedText
import com.jtr.app.ui.person.sanitizeNoteSections
import org.junit.Test

/**
 * Contrat des notes simplifiées « 1 champ, 1re ligne = titre » (v7.1.75) : le round-trip
 * combine/split est SANS PERTE. Invariant central : `combined(split(x)) == x` pour tout
 * texte x — le texte visible ne change jamais, seule la répartition title/content peut
 * être redistribuée, et elle est un point fixe dès la première passe. C'est la garantie
 * qu'aucune note existante (titrée, sans titre, multi-lignes, RTL, emoji, CRLF…) n'est
 * corrompue par la bascule.
 */
class NoteSectionCombineTest {

    /** Reconstruit le texte combiné depuis un couple (title, content) découpé. */
    private fun recombine(split: Pair<String, String>): String =
        NoteSection(title = split.first, content = split.second).combinedText()

    private fun roundTrip(text: String): String = recombine(splitCombinedText(text))

    // ── Identité round-trip : le texte combiné ressort octet pour octet ──────────

    @Test
    fun `titled multiline note round-trips identically`() {
        val s = NoteSection(title = "Titre", content = "Ligne 2\nLigne 3")
        val combined = s.combinedText()
        assertThat(combined).isEqualTo("Titre\nLigne 2\nLigne 3")
        assertThat(splitCombinedText(combined)).isEqualTo("Titre" to "Ligne 2\nLigne 3")
        assertThat(roundTrip(combined)).isEqualTo(combined)
    }

    @Test
    fun `internal blank lines are preserved - split only on the first newline`() {
        val combined = "T\nA\n\nB"
        assertThat(splitCombinedText(combined)).isEqualTo("T" to "A\n\nB")
        assertThat(roundTrip(combined)).isEqualTo(combined)
    }

    @Test
    fun `trailing newline is preserved`() {
        val combined = "T\nA\n"
        assertThat(splitCombinedText(combined)).isEqualTo("T" to "A\n")
        assertThat(roundTrip(combined)).isEqualTo(combined)
    }

    @Test
    fun `title-only note round-trips identically`() {
        val s = NoteSection(title = "Juste un titre", content = "")
        assertThat(s.combinedText()).isEqualTo("Juste un titre")
        assertThat(splitCombinedText("Juste un titre")).isEqualTo("Juste un titre" to "")
        assertThat(roundTrip("Juste un titre")).isEqualTo("Juste un titre")
    }

    // ── Redistribution « title vide » : voulue, sans perte, idempotente ──────────

    @Test
    fun `untitled content promotes its first line to title without losing text`() {
        val s = NoteSection(title = "", content = "Premiere ligne\nReste du texte")
        val combined = s.combinedText()
        assertThat(combined).isEqualTo("Premiere ligne\nReste du texte")
        val redistributed = s.withCombinedText(combined)
        assertThat(redistributed.title).isEqualTo("Premiere ligne")
        assertThat(redistributed.content).isEqualTo("Reste du texte")
        // Texte total conservé, et point fixe : un second cycle ne change plus rien.
        assertThat(redistributed.combinedText()).isEqualTo(combined)
        assertThat(redistributed.withCombinedText(redistributed.combinedText()))
            .isEqualTo(redistributed)
    }

    @Test
    fun `single-line untitled content becomes a title-only note, text intact`() {
        val s = NoteSection(title = "", content = "Une seule ligne")
        val redistributed = s.withCombinedText(s.combinedText())
        assertThat(redistributed.title).isEqualTo("Une seule ligne")
        assertThat(redistributed.content).isEmpty()
        assertThat(redistributed.combinedText()).isEqualTo("Une seule ligne")
    }

    // ── Règle anti-perte : 1re ligne blanche jamais promue ───────────────────────

    @Test
    fun `leading blank line is never promoted and survives byte-exact`() {
        for (combined in listOf("\nA", "  \nA\nB", "\n\nA")) {
            val (t, c) = splitCombinedText(combined)
            assertThat(t).isEmpty()
            assertThat(c).isEqualTo(combined)
            assertThat(roundTrip(combined)).isEqualTo(combined)
        }
    }

    // ── Formes hostiles : CRLF, RTL, emoji, espaces — octet pour octet ───────────

    @Test
    fun `crlf rtl emoji and surrounding spaces are preserved byte-exact`() {
        val forms = listOf(
            "T\r\nA\r\nB",                       // CRLF importés : \r reste en place
            "  Titre avec espaces  \n  corps  ", // aucun trim
            "مرحبا\nالسطر 2", // arabe RTL
            "😀 emoji titre\ncorps 🎉",                     // surrogate pairs
            "Tab\tet fin \n deuxieme"
        )
        for (combined in forms) {
            assertThat(roundTrip(combined)).isEqualTo(combined)
        }
    }

    @Test
    fun `crlf content without title keeps the full byte sequence through redistribution`() {
        val s = NoteSection(title = "", content = "A\r\nB")
        val redistributed = s.withCombinedText(s.combinedText())
        // Le \r reste collé à la 1re ligne ; le texte total est identique.
        assertThat(redistributed.title).isEqualTo("A\r")
        assertThat(redistributed.content).isEqualTo("B")
        assertThat(redistributed.combinedText()).isEqualTo("A\r\nB")
    }

    // ── Vide + sanitisation + order ──────────────────────────────────────────────

    @Test
    fun `empty note stays empty and is dropped by sanitize, order reindexed`() {
        val empty = NoteSection(title = "", content = "")
        assertThat(empty.combinedText()).isEmpty()
        assertThat(splitCombinedText("")).isEqualTo("" to "")

        val sections = listOf(
            NoteSection(title = "A", content = "", order = 0),
            empty.copy(order = 1),
            NoteSection(title = "", content = "B", order = 2)
        )
        val sanitized = sanitizeNoteSections(sections)
        assertThat(sanitized.map { it.title to it.content })
            .containsExactly("A" to "", "" to "B").inOrder()
        assertThat(sanitized.map { it.order }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `withCombinedText keeps id iconKey and order untouched`() {
        val s = NoteSection(id = "fixe", title = "", content = "X\nY", iconKey = "star", order = 7)
        val updated = s.withCombinedText(s.combinedText())
        assertThat(updated.id).isEqualTo("fixe")
        assertThat(updated.iconKey).isEqualTo("star")
        assertThat(updated.order).isEqualTo(7)
    }
}
