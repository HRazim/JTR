package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.jtr.app.utils.DateCanonical
import com.jtr.app.utils.DateCanonical.DateOrder
import org.junit.Test
import java.util.Calendar

/**
 * Vérifie le cœur CANONIQUE des dates (v7.1.0) : conversions ISO ⇄ millis et surtout la
 * désambiguïsation des chiffres bruts hérités (le bug du changement de langue).
 */
class DateCanonicalTest {

    @Test
    fun isIso_detectsCanonicalForm() {
        assertThat(DateCanonical.isIso("1995-12-25")).isTrue()
        assertThat(DateCanonical.isIso("12251995")).isFalse()
        assertThat(DateCanonical.isIso("")).isFalse()
        assertThat(DateCanonical.isIso("1995-13-40")).isTrue() // forme seulement (validité ailleurs)
    }

    @Test
    fun isoMillisRoundTrip_isStableAndLocaleFree() {
        val iso = "1990-02-28"
        val millis = DateCanonical.isoToMillis(iso)
        assertThat(millis).isNotNull()
        assertThat(DateCanonical.millisToIso(millis!!)).isEqualTo(iso)
    }

    @Test
    fun millisToIso_usesLocalCalendarDate() {
        val cal = Calendar.getInstance().apply { clear(); set(2001, Calendar.JANUARY, 9, 12, 0, 0) }
        assertThat(DateCanonical.millisToIso(cal.timeInMillis)).isEqualTo("2001-01-09")
    }

    @Test
    fun legacy_alreadyIso_isIdempotent() {
        assertThat(DateCanonical.legacyRawDigitsToIso("1995-12-25", DateOrder.DMY))
            .isEqualTo("1995-12-25")
    }

    @Test
    fun legacy_unambiguousByValidity_ignoresTieBreak() {
        // en-US "12251995" : seule l'interprétation MDY est une date valide (mois 12, jour 25).
        assertThat(DateCanonical.legacyRawDigitsToIso("12251995", DateOrder.DMY))
            .isEqualTo("1995-12-25")
        assertThat(DateCanonical.legacyRawDigitsToIso("12251995", DateOrder.MDY))
            .isEqualTo("1995-12-25")
        // fr "25121995" : seule DMY est valide (jour 25, mois 12).
        assertThat(DateCanonical.legacyRawDigitsToIso("25121995", DateOrder.MDY))
            .isEqualTo("1995-12-25")
    }

    @Test
    fun legacy_trulyAmbiguous_resolvedByTieBreak() {
        // "01021995" : DMY=1er févr., MDY=2 janv. → ambigu, tranché par la locale courante.
        assertThat(DateCanonical.legacyRawDigitsToIso("01021995", DateOrder.DMY))
            .isEqualTo("1995-02-01")
        assertThat(DateCanonical.legacyRawDigitsToIso("01021995", DateOrder.MDY))
            .isEqualTo("1995-01-02")
    }

    @Test
    fun legacy_yearFirst_isParsed() {
        // ja/ko/zh "19951225" (YMD) : year 1995, month 12, day 25.
        assertThat(DateCanonical.legacyRawDigitsToIso("19951225", DateOrder.YMD))
            .isEqualTo("1995-12-25")
    }

    @Test
    fun legacy_invalidOrUnparseable_returnsNullToPreserveRaw() {
        assertThat(DateCanonical.legacyRawDigitsToIso("99999999", DateOrder.DMY)).isNull()
        assertThat(DateCanonical.legacyRawDigitsToIso("123", DateOrder.DMY)).isNull()
        assertThat(DateCanonical.legacyRawDigitsToIso("abcdabcd", DateOrder.DMY)).isNull()
    }
}
