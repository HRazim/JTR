package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.jtr.app.domain.model.NoteSection
import com.jtr.app.domain.model.combinedText
import com.jtr.app.domain.model.isSameNoteSplit
import com.jtr.app.domain.model.splitCombinedText
import com.jtr.app.domain.model.withCombinedText
import com.jtr.app.ui.person.shouldResyncNoteField
import org.junit.Test

/**
 * v7.1.144 — bug déterministe du curseur des notes : « titre⏎ » (contenu vide) se recombine en
 * « titre » (saut de ligne final perdu). La réconciliation du champ ne doit JAMAIS réécrire la
 * frappe avec cette projection, mais doit TOUJOURS appliquer un vrai changement externe.
 *
 * Les scénarios A/B/C rejouent la chaîne réelle : texte local tapé → withCombinedText (VM) →
 * combinedText (valeur hissée) → décision [shouldResyncNoteField].
 */
class NoteTrailingNewlineTest {

    /** Valeur que la section hissée renvoie au champ après la frappe [typed]. */
    private fun hoistedAfter(typed: String, base: NoteSection = NoteSection()): String =
        base.withCombinedText(typed).combinedText()

    /** Rejoue une suite d'états locaux ; vrai si AU MOINS UNE remontée aurait écrasé le champ. */
    private fun anyResync(vararg localStates: String): Boolean {
        var section = NoteSection()
        return localStates.any { local ->
            section = section.withCombinedText(local)
            shouldResyncNoteField(hoisted = section.combinedText(), local = local)
        }
    }

    // ── Aller-retour : la perte est EXACTEMENT le saut de ligne final seul ───────────

    @Test
    fun `title plus trailing newline loses only the newline on round-trip`() {
        assertThat(splitCombinedText("titre\n")).isEqualTo("titre" to "")
        assertThat(hoistedAfter("titre\n")).isEqualTo("titre")
        assertThat(hoistedAfter("titre\r\n")).isEqualTo("titre\r")
    }

    @Test
    fun `leading newline, crlf, emoji and arabic forms round-trip identically`() {
        val forms = listOf(
            "\nabc", "\n", "  \nabc", "T\r\nA", "T\nA\n", "T\n\n",
            "😀 titre\ncorps 🎉", "مرحبا\nالسطر 2", "titre", ""
        )
        for (text in forms) assertThat(hoistedAfter(text)).isEqualTo(text)
    }

    @Test
    fun `emoji and arabic titles with trailing newline lose only the newline`() {
        assertThat(hoistedAfter("😀 titre\n")).isEqualTo("😀 titre")
        assertThat(hoistedAfter("مرحبا\n")).isEqualTo("مرحبا")
    }

    // ── Prédicat : même découpage ⇔ ne diffèrent que par ce saut de ligne final ──────

    @Test
    fun `same split only for the lossy trailing newline pair`() {
        assertThat(isSameNoteSplit("titre", "titre\n")).isTrue()
        assertThat(isSameNoteSplit("titre\r", "titre\r\n")).isTrue()
        assertThat(isSameNoteSplit("مرحبا", "مرحبا\n")).isTrue()
        assertThat(isSameNoteSplit("titre", "titre\n\n")).isFalse()
        assertThat(isSameNoteSplit("titre", "titre ")).isFalse()
        assertThat(isSameNoteSplit("titre\nx", "titre\n")).isFalse()
        assertThat(isSameNoteSplit("autre", "titre\n")).isFalse()
        assertThat(isSameNoteSplit("", "\n")).isFalse()
    }

    // ── Scénarios au doigt de l'audit (états locaux successifs du champ) ────────────

    @Test
    fun `scenario A - title, Enter, x, erase, y never rewrites the field`() {
        assertThat(anyResync("Z", "Zt", "Zt\n", "Zt\nx", "Zt\n", "Zt\ny")).isFalse()
    }

    @Test
    fun `scenario B - edit title after Enter never rewrites the field`() {
        assertThat(anyResync("Z", "Zt", "Zty", "Zty\n", "Ztay\n", "Ztaby\n")).isFalse()
    }

    @Test
    fun `scenario C - autocorrect replacement then Enter never rewrites the field`() {
        assertThat(anyResync("A", "Ab", "Abc", "Abcès", "Abcès\n")).isFalse()
    }

    @Test
    fun `legacy untitled multi-line note does not trigger a rewrite while typing`() {
        val legacy = NoteSection(title = "", content = "Ligne 1\nLigne 2")
        val local = legacy.combinedText() + "\n"
        val hoisted = legacy.withCombinedText(local).combinedText()
        assertThat(shouldResyncNoteField(hoisted, local)).isFalse()
    }

    // ── Changement externe : jamais « collé » à un texte périmé ─────────────────────

    @Test
    fun `external change after typing title plus newline is applied`() {
        val local = "titre\n"
        // Autre note chargée, undo vers un état antérieur, contenu restauré, section vidée.
        for (external in listOf("Autre note", "titr", "titre\nancien contenu", "", "titre\n\n")) {
            assertThat(shouldResyncNoteField(hoisted = external, local = local)).isTrue()
        }
    }

    @Test
    fun `identical value never resyncs`() {
        assertThat(shouldResyncNoteField("titre\nx", "titre\nx")).isFalse()
        assertThat(shouldResyncNoteField("", "")).isFalse()
    }
}
