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

    // ── B3b (v7.1.37) : dates SANS année « --MM-dd » ──────────────────────────

    @Test
    fun isMonthDay_detectsYearLessForm() {
        assertThat(DateCanonical.isMonthDay("--06-25")).isTrue()
        assertThat(DateCanonical.isMonthDay("--02-29")).isTrue()
        assertThat(DateCanonical.isMonthDay("1995-06-25")).isFalse() // ISO complet ≠ year-less
        assertThat(DateCanonical.isMonthDay("06-25")).isFalse()
        assertThat(DateCanonical.isMonthDay("")).isFalse()
    }

    @Test
    fun monthDayOf_parsesValidAndRejectsImpossible() {
        assertThat(DateCanonical.monthDayOf("--06-25")).isEqualTo(6 to 25)
        assertThat(DateCanonical.monthDayOf("--02-29")).isEqualTo(2 to 29) // année non fixée → 29/02 OK
        assertThat(DateCanonical.monthDayOf("--13-01")).isNull()           // mois invalide
        assertThat(DateCanonical.monthDayOf("--02-30")).isNull()           // jour invalide
        assertThat(DateCanonical.monthDayOf("1995-06-25")).isNull()        // pas un « --MM-dd »
    }

    @Test
    fun strictIsoFunctions_ignoreYearLess_noRegression() {
        // Garde-fou B3b : les fonctions ISO STRICTES ne traitent JAMAIS un « --MM-dd ».
        assertThat(DateCanonical.isIso("--06-25")).isFalse()
        assertThat(DateCanonical.isoToMillis("--06-25")).isNull()
        // …et les dates existantes restent EXACTEMENT inchangées.
        assertThat(DateCanonical.isIso("1995-12-25")).isTrue()
        assertThat(DateCanonical.isoToMillis("1995-12-25")).isNotNull()
    }

    @Test
    fun nextOccurrenceMillis_thisYearWhenDayNotPassed() {
        val from = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 1, 9, 0, 0) }.timeInMillis
        val cal = Calendar.getInstance().apply { timeInMillis = DateCanonical.nextOccurrenceMillis("--06-25", from)!! }
        assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026)
        assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.JUNE)
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(25)
    }

    @Test
    fun nextOccurrenceMillis_nextYearWhenDayPassed() {
        val from = Calendar.getInstance().apply { clear(); set(2026, Calendar.DECEMBER, 1, 9, 0, 0) }.timeInMillis
        val cal = Calendar.getInstance().apply { timeInMillis = DateCanonical.nextOccurrenceMillis("--06-25", from)!! }
        assertThat(cal.get(Calendar.YEAR)).isEqualTo(2027)
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(25)
    }

    @Test
    fun nextOccurrenceMillis_todayCountsAsNotPassed() {
        // Occurrence le jour même : retenue (comparaison au DÉBUT du jour, pas à l'heure courante).
        val from = Calendar.getInstance().apply { clear(); set(2026, Calendar.JUNE, 25, 15, 0, 0) }.timeInMillis
        val cal = Calendar.getInstance().apply { timeInMillis = DateCanonical.nextOccurrenceMillis("--06-25", from)!! }
        assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026)
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(25)
    }

    @Test
    fun nextOccurrenceMillis_feb29ClampedInNonLeapYear() {
        val from = Calendar.getInstance().apply { clear(); set(2025, Calendar.MARCH, 1, 9, 0, 0) }.timeInMillis
        val cal = Calendar.getInstance().apply { timeInMillis = DateCanonical.nextOccurrenceMillis("--02-29", from)!! }
        assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026)            // 2025-02 déjà passé
        assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.FEBRUARY)
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(28)      // 2026 non bissextile → coerce 28
    }

    @Test
    fun nextOccurrenceMillis_rejectsNonMonthDay() {
        assertThat(DateCanonical.nextOccurrenceMillis("1995-06-25", 0L)).isNull()
        assertThat(DateCanonical.nextOccurrenceMillis("--13-01", 0L)).isNull()
    }
}
