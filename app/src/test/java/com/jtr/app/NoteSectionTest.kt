package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.jtr.app.domain.model.NOTE_ICON_HEART
import com.jtr.app.domain.model.NOTE_ICON_NOTES
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.deriveNoteSections
import com.jtr.app.domain.model.effectiveNoteSections
import org.junit.Test

/**
 * Vérifie le BACKFILL SANS PERTE (v7.0.3) des notes héritées (`notes` / `likes`) vers
 * les sections de notes — garantie centrale : aucune note existante n'est jamais perdue.
 */
class NoteSectionTest {

    @Test
    fun `derive converts legacy notes and likes without losing content`() {
        val sections = deriveNoteSections(
            notes = "Aime le thé vert",
            likes = "Randonnée, jazz",
            notesTitle = "Notes",
            likesTitle = "What they like"
        )

        assertThat(sections).hasSize(2)
        // Contenu IDENTIQUE préservé, titres + icônes par défaut, ordre 0 puis 1.
        assertThat(sections[0].content).isEqualTo("Aime le thé vert")
        assertThat(sections[0].title).isEqualTo("Notes")
        assertThat(sections[0].iconKey).isEqualTo(NOTE_ICON_NOTES)
        assertThat(sections[0].order).isEqualTo(0)
        assertThat(sections[1].content).isEqualTo("Randonnée, jazz")
        assertThat(sections[1].title).isEqualTo("What they like")
        assertThat(sections[1].iconKey).isEqualTo(NOTE_ICON_HEART)
        assertThat(sections[1].order).isEqualTo(1)
    }

    @Test
    fun `derive ignores blank legacy fields`() {
        assertThat(deriveNoteSections("", "   ", "Notes", "Likes")).isEmpty()
        val onlyNotes = deriveNoteSections("Hello", null, "Notes", "Likes")
        assertThat(onlyNotes).hasSize(1)
        assertThat(onlyNotes[0].content).isEqualTo("Hello")
    }

    @Test
    fun `effectiveNoteSections prefers persisted list over legacy derivation`() {
        // Personne déjà migrée (sections non vides) : on n'utilise PAS le repli legacy.
        val migrated = Person(
            firstName = "Alice",
            notes = "vieille note",
            noteSections = deriveNoteSections("section persistée", null, "Notes", "Likes")
        )
        val eff = migrated.effectiveNoteSections("Notes", "Likes")
        assertThat(eff).hasSize(1)
        assertThat(eff[0].content).isEqualTo("section persistée")

        // Personne legacy (sections vides) : repli sur la conversion des notes héritées.
        val legacy = Person(firstName = "Bob", notes = "ancienne note", likes = null)
        val effLegacy = legacy.effectiveNoteSections("Notes", "Likes")
        assertThat(effLegacy).hasSize(1)
        assertThat(effLegacy[0].content).isEqualTo("ancienne note")
    }
}
