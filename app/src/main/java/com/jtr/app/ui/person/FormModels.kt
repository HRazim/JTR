package com.jtr.app.ui.person

import androidx.annotation.StringRes
import com.jtr.app.R
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.utils.DateCanonical
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Locale

/**
 * Couche de transfert UI pour la refonte « Contacts Google » de JTR v4.5.
 *
 * Ces structures vivent UNIQUEMENT côté UI/ViewModel — Room v11 reste STRICTEMENT
 * intacte. Au submit, seule la première ligne valide de chaque groupe est repliée
 * sur les colonnes existantes (`phoneNumber`, `email`, `birthdate`). Les lignes
 * supplémentaires et les sous-champs de nom restent en mémoire (migration future).
 */

/**
 * Nettoie une liste avant persistance : retire les lignes dont la valeur est vide
 * et renvoie `null` s'il ne reste rien (→ colonne SQL NULL plutôt que `[]`).
 */
fun sanitizeLines(lines: List<DynamicLine>): List<DynamicLine>? =
    lines.filter { it.value.isNotBlank() }.takeIf { it.isNotEmpty() }

/** Une ligne d'email est valide si elle est vide ou contient au moins un « @ ». */
fun isValidEmailValue(value: String): Boolean =
    value.isBlank() || value.contains('@')

/**
 * Nettoie les emails AVANT persistance : retire les lignes vides ET les lignes
 * syntaxiquement invalides (sans « @ ») — on ne sauvegarde jamais un email mal formé.
 */
fun sanitizeEmailLines(lines: List<DynamicLine>): List<DynamicLine>? =
    lines.filter { it.value.isNotBlank() && it.value.contains('@') }.takeIf { it.isNotEmpty() }

/**
 * Résout le libellé d'affichage d'une ligne dynamique : si [key] correspond à un
 * type connu de [types], renvoie sa ressource localisée ; sinon `null` (→ libellé
 * personnalisé saisi par l'utilisateur, à afficher tel quel).
 */
@StringRes
fun typeLabelResOrNull(types: List<TypeOption>, key: String): Int? =
    types.firstOrNull { it.key == key }?.labelRes

/** Sous-champs avancés du nom — persistés en 5 colonnes Person dès la DB v12. */
data class NameDetails(
    val prefix: String = "",
    val middleName: String = "",
    val suffix: String = "",
    val phonetic: String = "",
    val nickname: String = ""
)

/** Option de type sélectionnable dans le menu déroulant d'une ligne. */
data class TypeOption(val key: String, @StringRes val labelRes: Int)

/**
 * Référence d'un contact pour l'autocomplétion des relations (v7.1.6) : on AFFICHE
 * [name] mais on STOCKE [id] (clé stable, unique) dans [DynamicLine.linkedPersonId]
 * dès qu'une suggestion est choisie — jamais le nom comme clé.
 */
data class PersonRef(val id: String, val name: String)

/**
 * Résultat de la résolution d'une relation cliquable (v7.1.6) — jamais de devinette :
 *  - [Resolved] : cible identifiée sans ambiguïté (par id, ou un seul homonyme) ;
 *  - [Ambiguous] : plusieurs homonymes ⇒ « à vérifier », aucune navigation ;
 *  - [NotFound] : aucun contact correspondant (texte libre ou fiche supprimée).
 */
sealed interface RelationTarget {
    data class Resolved(val personId: String) : RelationTarget
    data object Ambiguous : RelationTarget
    data object NotFound : RelationTarget
}

/** Catalogues de types par groupe + clés stables réutilisées par le repli au submit. */
object FieldTypes {
    const val PHONE_MOBILE = "mobile"
    const val EMAIL_HOME = "home"
    const val RELATION_FRIEND = "friend"
    const val DATE_BIRTHDAY = "birthday"

    /** Clé du type « Personnalisé » : déclenche le dialogue de saisie de libellé. */
    const val CUSTOM = "custom"

    val PHONE = listOf(
        TypeOption(PHONE_MOBILE, R.string.phone_type_mobile),
        TypeOption("home", R.string.phone_type_home),
        TypeOption("work", R.string.phone_type_work),
        TypeOption("main", R.string.phone_type_main),
        TypeOption("other", R.string.type_other),
    )

    val EMAIL = listOf(
        TypeOption(EMAIL_HOME, R.string.email_type_home),
        TypeOption("work", R.string.email_type_work),
        TypeOption("other", R.string.type_other),
        TypeOption("custom", R.string.type_custom),
    )

    val DATE = listOf(
        TypeOption(DATE_BIRTHDAY, R.string.date_type_birthday),
        TypeOption("anniversary", R.string.date_type_anniversary),
        TypeOption("other", R.string.type_other),
        TypeOption("custom", R.string.type_custom),
    )

    val RELATION = listOf(
        TypeOption("mother", R.string.relation_type_mother),
        TypeOption("father", R.string.relation_type_father),
        TypeOption("brother", R.string.relation_type_brother),
        TypeOption("sister", R.string.relation_type_sister),
        TypeOption("spouse", R.string.relation_type_spouse),
        TypeOption("child", R.string.relation_type_child),
        TypeOption(RELATION_FRIEND, R.string.relation_type_friend),
        TypeOption("manager", R.string.relation_type_manager),
        TypeOption("custom", R.string.type_custom),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Saisie de date localisée (partagée UI ↔ ViewModel pour le repli au submit)
// ─────────────────────────────────────────────────────────────────────────────

/** Composant logique d'une date. */
enum class DateField { DAY, MONTH, YEAR }

/** Spécification de format dérivée de la locale système. */
data class DateFormatSpec(
    val order: List<DateField>,
    val separator: Char,
    val segmentLengths: List<Int>
)

/**
 * Déduit l'ordre des composants et le séparateur du motif court localisé
 * (ex. fr → "dd/MM/y", en-US → "M/d/yy", ja → "y/MM/dd"). Repli sûr sur JJ/MM/AAAA.
 */
fun resolveDateFormatSpec(locale: Locale): DateFormatSpec {
    val pattern = try {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale
        )
    } catch (_: Exception) {
        "dd/MM/yyyy"
    }

    val order = ArrayList<DateField>(3)
    for (c in pattern) {
        when (c) {
            'd' -> if (DateField.DAY !in order) order.add(DateField.DAY)
            'M', 'L' -> if (DateField.MONTH !in order) order.add(DateField.MONTH)
            'y', 'u', 'Y' -> if (DateField.YEAR !in order) order.add(DateField.YEAR)
        }
    }
    if (order.size != 3) {
        order.clear()
        order.addAll(listOf(DateField.DAY, DateField.MONTH, DateField.YEAR))
    }

    val separator = pattern.firstOrNull { it == '/' || it == '-' || it == '.' } ?: '/'
    val lengths = order.map { if (it == DateField.YEAR) 4 else 2 }
    return DateFormatSpec(order, separator, lengths)
}

/** Convertit un timestamp (midi local) en chaîne de chiffres dans l'ordre voulu. */
fun millisToRawDigits(millis: Long, order: List<DateField>): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val month = cal.get(Calendar.MONTH) + 1
    val year = cal.get(Calendar.YEAR)
    return order.joinToString("") {
        when (it) {
            DateField.DAY -> "%02d".format(day)
            DateField.MONTH -> "%02d".format(month)
            DateField.YEAR -> "%04d".format(year)
        }
    }
}

/** Année plancher (impose 4 chiffres) pour une date importante saisie. */
private const val MIN_DATE_YEAR = 1000

/**
 * Valide une date saisie (chiffres bruts) pour l'UI ET la sauvegarde (v7.0.5).
 *
 * Une valeur vide est acceptée (date optionnelle) ; sinon la date doit être COMPLÈTE et
 * parseable, avec une **année cohérente à 4 chiffres** dans une plage raisonnable
 * (≥ [MIN_DATE_YEAR], ≤ année courante). Rejette notamment une année incomplète à 3 chiffres
 * (la date n'atteint pas la longueur attendue → [rawDigitsToMillis] renvoie `null`).
 */
fun isDateLineValid(raw: String, spec: DateFormatSpec): Boolean {
    if (raw.isBlank()) return true
    val millis = rawDigitsToMillis(raw, spec) ?: return false
    val cal = Calendar.getInstance()
    val maxYear = cal.get(Calendar.YEAR)
    cal.timeInMillis = millis
    return cal.get(Calendar.YEAR) in MIN_DATE_YEAR..maxYear
}

/**
 * Découpe des chiffres bruts (ordre de [spec]) en [LocalDate] strictement valide, ou
 * `null` si la longueur, le format ou la date sont invalides. Cœur partagé par les
 * conversions vers millis (legacy) et vers l'ISO canonique.
 */
private fun rawDigitsToLocalDate(raw: String, spec: DateFormatSpec): LocalDate? {
    if (raw.length != spec.segmentLengths.sum()) return null
    var idx = 0
    var day = 0; var month = 0; var year = 0
    for ((i, field) in spec.order.withIndex()) {
        val len = spec.segmentLengths[i]
        val part = raw.substring(idx, idx + len).toIntOrNull() ?: return null
        when (field) {
            DateField.DAY -> day = part
            DateField.MONTH -> month = part
            DateField.YEAR -> year = part
        }
        idx += len
    }
    if (year < 1) return null
    return try {
        LocalDate.of(year, month, day) // valide mois (1..12), jour et bissextiles
    } catch (_: Exception) {
        null
    }
}

/**
 * Parse les chiffres bruts (ordre [spec]) en timestamp (midi local), ou `null`. Conservé
 * pour la SAISIE du formulaire (qui manipule toujours des chiffres bruts en locale courante)
 * et pour le repli ultime sur une valeur héritée non encore canonisée.
 */
fun rawDigitsToMillis(raw: String, spec: DateFormatSpec): Long? {
    val d = rawDigitsToLocalDate(raw, spec) ?: return null
    return Calendar.getInstance().apply {
        clear()
        set(d.year, d.monthValue - 1, d.dayOfMonth, 12, 0, 0)
    }.timeInMillis
}

// ─────────────────────────────────────────────────────────────────────────────
// Pont CANONIQUE (v7.1.0) — la forme STOCKÉE est l'ISO yyyy-MM-dd (locale-libre) ;
// le formulaire travaille en chiffres bruts (ordre de la locale courante). Ces ponts
// convertissent UNIQUEMENT au bord (chargement / sauvegarde / lecture).
// ─────────────────────────────────────────────────────────────────────────────

/** L'enum interne du formulaire vers l'ordre canonique de [DateCanonical]. */
private fun DateFormatSpec.canonicalOrder(): DateCanonical.DateOrder = when {
    order.firstOrNull() == DateField.YEAR -> DateCanonical.DateOrder.YMD
    order.indexOf(DateField.MONTH) in 0 until order.indexOf(DateField.DAY) ->
        DateCanonical.DateOrder.MDY
    else -> DateCanonical.DateOrder.DMY
}

/** Chiffres bruts du formulaire (ordre [spec]) → ISO `yyyy-MM-dd`, ou `null` si invalide. */
fun rawDigitsToIso(raw: String, spec: DateFormatSpec): String? {
    val d = rawDigitsToLocalDate(raw, spec) ?: return null
    return "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
}

/** ISO `yyyy-MM-dd` → chiffres bruts dans l'ordre de [spec] (pour réinjection au formulaire). */
fun isoToRawDigits(iso: String, spec: DateFormatSpec): String = try {
    val d = LocalDate.parse(iso)
    spec.order.joinToString("") {
        when (it) {
            DateField.DAY -> "%02d".format(d.dayOfMonth)
            DateField.MONTH -> "%02d".format(d.monthValue)
            DateField.YEAR -> "%04d".format(d.year)
        }
    }
} catch (_: Exception) {
    ""
}

/**
 * Valeur STOCKÉE d'une date (ISO canonique OU chiffres bruts hérités) → chiffres bruts du
 * FORMULAIRE dans la locale courante. Une donnée héritée ambiguë non convertible est
 * conservée telle quelle (meilleur effort, jamais de perte).
 */
fun storedDateToRawDigits(value: String, spec: DateFormatSpec): String {
    if (value.isBlank()) return value
    if (DateCanonical.isIso(value)) return isoToRawDigits(value, spec)
    val iso = DateCanonical.legacyRawDigitsToIso(value, spec.canonicalOrder())
    return if (iso != null) isoToRawDigits(iso, spec) else value
}

/**
 * Valeur STOCKÉE d'une date → epoch millis (midi local), de façon LOCALE-LIBRE pour l'ISO.
 * Repli pour une donnée héritée : désambiguïsation puis, en dernier recours, lecture dans
 * la locale courante. Utilisé par tous les consommateurs en LECTURE (workers, accueil,
 * fiche, partage) — l'interprétation ne dépend plus de la langue.
 */
fun storedDateToMillis(value: String): Long? {
    if (value.isBlank()) return null
    DateCanonical.isoToMillis(value)?.let { return it }
    val spec = resolveDateFormatSpec(Locale.getDefault())
    DateCanonical.legacyRawDigitsToIso(value, spec.canonicalOrder())
        ?.let { DateCanonical.isoToMillis(it) }
        ?.let { return it }
    return rawDigitsToMillis(value, spec)
}

/**
 * Convertit les lignes de date du FORMULAIRE (chiffres bruts, ordre [spec]) vers la forme
 * canonique ISO AVANT persistance. Une valeur vide ou non convertible est laissée telle
 * quelle (la sauvegarde est de toute façon bloquée si une date est invalide).
 */
fun canonicalizeDateLinesForStorage(lines: List<DynamicLine>, spec: DateFormatSpec): List<DynamicLine> =
    lines.map { line ->
        if (line.value.isBlank() || DateCanonical.isIso(line.value)) line
        else rawDigitsToIso(line.value, spec)?.let { line.copy(value = it) } ?: line
    }
