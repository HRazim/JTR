package com.jtr.app.domain.model

/**
 * Modèle du délai de rappel (v7.0) — un délai est un simple nombre de MINUTES avant
 * l'ancre minuit (00:00) du jour J. Une seule valeur unifie minutes/heures/jours/
 * semaines : 1 h = 60, 1 jour = 1440, 1 semaine = 10080 ; `0` = « le jour J ».
 */
enum class ReminderUnit(val minutes: Int, val max: Int) {
    MINUTES(1, 60),
    HOURS(60, 24),
    DAYS(1440, 30),
    WEEKS(10080, 12)
}

/** Préréglages proposés en premier dans la feuille de rappel (en minutes). */
object ReminderPresets {
    const val ON_DAY = 0
    const val MIN_10 = 10
    const val HOUR_1 = 60
    const val DAY_1 = 1440
    const val WEEK_1 = 10080

    /** Valeurs des préréglages NON personnalisés, dans l'ordre d'affichage. */
    val VALUES = listOf(ON_DAY, MIN_10, HOUR_1, DAY_1, WEEK_1)
}

/**
 * Décompose un délai (en minutes) dans la plus grande unité qui le divise exactement
 * — ex. 4320 → (3, DAYS), 90 → (90, MINUTES). Renvoie `(0, MINUTES)` pour `0`.
 */
fun decomposeReminderOffset(totalMinutes: Int): Pair<Int, ReminderUnit> {
    if (totalMinutes <= 0) return 0 to ReminderUnit.MINUTES
    for (unit in listOf(ReminderUnit.WEEKS, ReminderUnit.DAYS, ReminderUnit.HOURS, ReminderUnit.MINUTES)) {
        if (totalMinutes % unit.minutes == 0) return (totalMinutes / unit.minutes) to unit
    }
    return totalMinutes to ReminderUnit.MINUTES
}
