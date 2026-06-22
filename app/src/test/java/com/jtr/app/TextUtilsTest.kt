package com.jtr.app.utils

import com.google.common.truth.Truth.assertThat
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.NoteSection
import com.jtr.app.domain.model.Person
import org.junit.Test

class TextUtilsTest {

    @Test
    fun `normalizeForSearch strips acute accent`() {
        assertThat("é".normalizeForSearch()).isEqualTo("e")
        assertThat("è".normalizeForSearch()).isEqualTo("e")
        assertThat("ê".normalizeForSearch()).isEqualTo("e")
    }

    @Test
    fun `normalizeForSearch strips cedilla`() {
        assertThat("ç".normalizeForSearch()).isEqualTo("c")
        assertThat("Ç".normalizeForSearch()).isEqualTo("c")
    }

    @Test
    fun `normalizeForSearch is case insensitive`() {
        assertThat("THÉRÈSE".normalizeForSearch()).isEqualTo("therese")
    }

    @Test
    fun `normalizeForSearch — Therese finds Therese`() {
        val stored = "Thérèse Dupont".normalizeForSearch()
        val query  = "therese".normalizeForSearch()
        assertThat(stored).contains(query)
    }

    @Test
    fun `normalizeForSearch — Francois finds François`() {
        assertThat("François".normalizeForSearch()).contains("francois")
    }

    @Test
    fun `normalizeForSearch — Jose finds José`() {
        assertThat("José".normalizeForSearch()).contains("jose")
    }

    @Test
    fun `containsNormalized matches across accents`() {
        assertThat("Thérèse".containsNormalized("Therese")).isTrue()
        assertThat("François".containsNormalized("francois")).isTrue()
        assertThat("JOSÉ".containsNormalized("jose")).isTrue()
    }

    @Test
    fun `containsNormalized returns false for non-match`() {
        assertThat("Alice".containsNormalized("Bob")).isFalse()
    }

    @Test
    fun `normalizeForSearch leaves plain ASCII unchanged`() {
        assertThat("alice".normalizeForSearch()).isEqualTo("alice")
        assertThat("Bob123".normalizeForSearch()).isEqualTo("bob123")
    }

    // ── searchTokens : trim, espaces multiples, casse/accents ──────────────────

    @Test
    fun `searchTokens splits on whitespace and normalizes`() {
        assertThat("Nathan Jamel".searchTokens()).containsExactly("nathan", "jamel").inOrder()
    }

    @Test
    fun `searchTokens trims leading and trailing spaces`() {
        assertThat("Nathan ".searchTokens()).containsExactly("nathan")
        assertThat("  Nathan  ".searchTokens()).containsExactly("nathan")
    }

    @Test
    fun `searchTokens collapses multiple internal spaces`() {
        assertThat("Nathan    Jamel".searchTokens()).containsExactly("nathan", "jamel").inOrder()
    }

    @Test
    fun `searchTokens of blank query is empty`() {
        assertThat("".searchTokens()).isEmpty()
        assertThat("    ".searchTokens()).isEmpty()
    }

    // ── matchesAllTokens (texte libre : relation, catégorie, contact natif) ─────

    @Test
    fun `matchesAllTokens requires every token as substring`() {
        assertThat("Nathan Jamel".matchesAllTokens("nathan jamel".searchTokens())).isTrue()
        assertThat("Nathan Jamel".matchesAllTokens("jamel nathan".searchTokens())).isTrue()
        assertThat("Nathan Jamel".matchesAllTokens("tha".searchTokens())).isTrue()
        assertThat("Nathan Jamel".matchesAllTokens("nathan xyz".searchTokens())).isFalse()
    }

    @Test
    fun `matchesAllTokens with empty tokens matches everything`() {
        assertThat("anything".matchesAllTokens(emptyList())).isTrue()
    }

    // ── Person.matchesSearch : LE bug v7.1.9 (Nathan Jamel) ─────────────────────

    private val nathan = Person(firstName = "Nathan", lastName = "Jamel")

    private fun Person.find(query: String): Boolean = matchesSearch(query.searchTokens())

    @Test
    fun `Nathan Jamel — all required queries find the contact`() {
        // Cas exacts du cahier des charges (§8).
        listOf(
            "Nathan", "Jamel", "Nathan Jamel", "nathan jamel", "NATHAN JAMEL",
            "Nathan ", "Nathan Ja", "jamel", "tha"
        ).forEach { q ->
            assertThat(nathan.find(q)).isTrue()
        }
    }

    @Test
    fun `Nathan Jamel — order of words does not matter`() {
        assertThat(nathan.find("jamel nathan")).isTrue()
        assertThat(nathan.find("Ja Nat")).isTrue()
    }

    @Test
    fun `Nathan Jamel — a token absent from all fields fails`() {
        assertThat(nathan.find("Nathan Zorro")).isFalse()
        assertThat(nathan.find("Bob")).isFalse()
    }

    @Test
    fun `empty query matches every person`() {
        assertThat(nathan.find("")).isTrue()
        assertThat(nathan.find("   ")).isTrue()
    }

    @Test
    fun `matchesSearch is accent insensitive across name and full name`() {
        val jose = Person(firstName = "José", lastName = "Pérez")
        assertThat(jose.find("jose")).isTrue()
        assertThat(jose.find("perez")).isTrue()
        assertThat(jose.find("jose perez")).isTrue()
    }

    @Test
    fun `matchesSearch covers company nickname and dynamic lines`() {
        val p = Person(
            firstName = "Alice",
            company = "Acme Corp",
            nickname = "Ali",
            phoneLines = listOf(DynamicLine(value = "514-555-0199", label = "mobile")),
            emailLines = listOf(DynamicLine(value = "alice@acme.io", label = "work"))
        )
        assertThat(p.find("acme")).isTrue()
        assertThat(p.find("ali")).isTrue()
        assertThat(p.find("0199")).isTrue()
        assertThat(p.find("alice acme")).isTrue()
        assertThat(p.find("acme.io")).isTrue()
    }

    @Test
    fun `matchesSearch searches relations by linked name and localized type`() {
        val p = Person(
            firstName = "Manon",
            relationLines = listOf(DynamicLine(value = "Nathan Jamel", label = "friend"))
        )
        // Nom du contact lié.
        assertThat(p.matchesSearch("nathan".searchTokens())).isTrue()
        // Libellé localisé fourni par l'UI (« friend » → « ami »).
        assertThat(p.matchesSearch("ami".searchTokens()) { key ->
            if (key == "friend") "ami" else null
        }).isTrue()
    }

    // ── Sections de notes (m1) : titre ET contenu recherchables ────────────────

    @Test
    fun `matchesSearch covers note section title and content`() {
        val p = Person(
            firstName = "Alice",
            noteSections = listOf(
                NoteSection(title = "Travaux", content = "appeler le plombier vendredi")
            )
        )
        assertThat(p.find("plombier")).isTrue()       // contenu de la section
        assertThat(p.find("travaux")).isTrue()        // titre de la section
        assertThat(p.find("alice plombier")).isTrue() // ET-tokens / OU-champs (nom + note)
        assertThat(p.find("electricien")).isFalse()   // mot absent → pas de faux positif
    }

    @Test
    fun `matchesSearch note sections are accent and case insensitive`() {
        val p = Person(
            firstName = "Bob",
            noteSections = listOf(NoteSection(title = "Réunion", content = "Café à 10h"))
        )
        assertThat(p.find("reunion")).isTrue()
        assertThat(p.find("CAFE")).isTrue()
    }

    @Test
    fun `matchesSearch indexes every note section`() {
        val p = Person(
            firstName = "Chloé",
            noteSections = listOf(
                NoteSection(title = "Santé", content = "allergie aux arachides"),
                NoteSection(title = "Loisirs", content = "escalade le dimanche")
            )
        )
        assertThat(p.find("arachides")).isTrue() // 1re section
        assertThat(p.find("escalade")).isTrue()  // 2e section
    }

    // ── i18n / RTL : l'arabe n'est pas dénaturé ────────────────────────────────

    @Test
    fun `arabic name is preserved and searchable`() {
        val p = Person(firstName = "محمد")
        // Les lettres arabes ne sont pas supprimées par la normalisation latine.
        assertThat("محمد".normalizeForSearch()).isEqualTo("محمد")
        assertThat(p.matchesSearch("محمد".searchTokens())).isTrue()
    }

    @Test
    fun `arabic note section content is searchable`() {
        val p = Person(firstName = "Sara", noteSections = listOf(NoteSection(content = "محمد")))
        assertThat(p.matchesSearch("محمد".searchTokens())).isTrue()
    }
}
