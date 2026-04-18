package com.jtr.app.utils

import com.google.common.truth.Truth.assertThat
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
}
