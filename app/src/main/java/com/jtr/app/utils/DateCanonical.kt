package com.jtr.app.utils

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Locale

/**
 * Cœur CANONIQUE des dates de JTR (v7.1.0) — 100 % indépendant de la locale.
 *
 * Historique du bug corrigé en v7.1.0 : les dates importantes étaient stockées dans
 * `DynamicLine.value` sous forme de **chiffres bruts ordonnés selon la locale de saisie**
 * (ex. en-US `12251995`, fr `25121995`). Changer de langue changeait l'interprétation
 * → date faussée à l'affichage et **sauvegarde bloquée** (validation « 4 chiffres »).
 *
 * Désormais la forme STOCKÉE est l'ISO `yyyy-MM-dd` (locale-libre). L'affichage et la
 * saisie restent localisés, mais **uniquement au bord UI** (cf. `FormModels.kt`). Ces
 * fonctions sont PURES et STABLES : elles servent l'UI, les workers, la migration Room
 * v20→v21 et l'import `.jtr` — sans dépendre du code d'interface.
 *
 * Convention de timestamp : **midi (12:00) local**, identique à l'historique
 * `rawDigitsToMillis` → le scalaire `Person.birthdate` reste inchangé bit à bit.
 */
object DateCanonical {

    /** Ordre des composantes d'une date court-format, par famille de locales. */
    enum class DateOrder { DMY, MDY, YMD }

    private val ISO = Regex("""\d{4}-\d{2}-\d{2}""")

    /** Vrai si [value] est déjà au format canonique ISO `yyyy-MM-dd`. */
    fun isIso(value: String): Boolean = ISO.matches(value)

    /** ISO `yyyy-MM-dd` → epoch millis (midi local), ou `null` si non parseable. */
    fun isoToMillis(iso: String): Long? {
        if (!isIso(iso)) return null
        return try {
            val d = LocalDate.parse(iso)
            Calendar.getInstance().apply {
                clear()
                set(d.year, d.monthValue - 1, d.dayOfMonth, 12, 0, 0)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    /** epoch millis → ISO `yyyy-MM-dd` (date locale). */
    fun millisToIso(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Déduit l'ordre des composantes du motif court de [locale] (utilisé comme bris
     * d'égalité déterministe pour les chiffres bruts hérités ambigus). Repli `DMY`.
     */
    fun currentOrder(locale: Locale): DateOrder {
        val pattern = try {
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale
            )
        } catch (_: Exception) {
            return DateOrder.DMY
        }
        val seq = ArrayList<Char>(3)
        for (c in pattern) when (c) {
            'd' -> if ('D' !in seq) seq.add('D')
            'M', 'L' -> if ('M' !in seq) seq.add('M')
            'y', 'u', 'Y' -> if ('Y' !in seq) seq.add('Y')
        }
        val di = seq.indexOf('D')
        val mi = seq.indexOf('M')
        return when {
            seq.firstOrNull() == 'Y' -> DateOrder.YMD
            mi in 0 until di -> DateOrder.MDY  // mois avant jour, année en fin → en-US
            else -> DateOrder.DMY
        }
    }

    /**
     * Convertit des chiffres bruts HÉRITÉS (8 chiffres, ordre de locale inconnu) en ISO.
     *
     * Stratégie anti-ambiguïté DÉTERMINISTE et DOCUMENTÉE (v7.1.0) :
     *  1. Si [raw] est déjà ISO → renvoyé tel quel (idempotent).
     *  2. On essaie les 3 familles d'ordre des 13 locales supportées (DMY/MDY année en
     *     fin, YMD année en tête), en ne retenant qu'une **date réellement valide**
     *     (LocalDate strict : mois 1-12, jour valide, bissextiles) avec une année
     *     plausible à 4 chiffres (1000-9999).
     *  3. Si une seule interprétation est valide → elle est retenue (cas le plus
     *     fréquent, ex. jour > 12 lève l'ambiguïté DMY↔MDY).
     *  4. Si plusieurs interprétations donnent des dates DIFFÉRENTES mais valides (vraie
     *     ambiguïté, ex. `01/02/1995`) → on tranche par [tieBreak] (l'ordre de la locale
     *     courante, meilleur proxy de la locale d'écriture), sinon la 1re valide.
     *  5. Si AUCUNE interprétation n'est valide → `null` : l'appelant **préserve la
     *     valeur brute** plutôt que de fabriquer une fausse date (aucune corruption).
     *
     * NB : pour l'anniversaire, l'appelant privilégie le scalaire canonique
     * `Person.birthdate` (vérité absolue) avant d'appeler cette heuristique.
     */
    fun legacyRawDigitsToIso(raw: String, tieBreak: DateOrder): String? {
        if (isIso(raw)) return raw
        if (raw.length != 8 || !raw.all { it.isDigit() }) return null

        fun parse(order: DateOrder): LocalDate? = try {
            val d = when (order) {
                DateOrder.YMD -> LocalDate.of(
                    raw.substring(0, 4).toInt(), raw.substring(4, 6).toInt(), raw.substring(6, 8).toInt()
                )
                DateOrder.DMY -> LocalDate.of(
                    raw.substring(4, 8).toInt(), raw.substring(2, 4).toInt(), raw.substring(0, 2).toInt()
                )
                DateOrder.MDY -> LocalDate.of(
                    raw.substring(4, 8).toInt(), raw.substring(0, 2).toInt(), raw.substring(2, 4).toInt()
                )
            }
            d.takeIf { it.year in 1000..9999 }
        } catch (_: Exception) {
            null
        }

        val valid = DateOrder.entries.mapNotNull { order -> parse(order)?.let { order to it } }
        if (valid.isEmpty()) return null
        val distinct = valid.map { it.second }.distinct()
        val chosen = if (distinct.size == 1) {
            distinct.first()
        } else {
            valid.firstOrNull { it.first == tieBreak }?.second ?: valid.first().second
        }
        return "%04d-%02d-%02d".format(chosen.year, chosen.monthValue, chosen.dayOfMonth)
    }
}
