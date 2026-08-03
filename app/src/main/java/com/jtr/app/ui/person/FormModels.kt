package com.jtr.app.ui.person

import android.text.format.DateFormat
import androidx.annotation.StringRes
import com.jtr.app.R
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.utils.DateCanonical
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Date
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

/**
 * Option de type sélectionnable dans le menu déroulant d'une ligne.
 *
 * [groupRes] (v7.1.44) — en-tête de section affiché dans le menu quand le groupe change d'une
 * option à la suivante. `null` = aucun en-tête : c'est le cas de PHONE/EMAIL/DATE (listes
 * courtes) et de « Personnalisé ». Seul le catalogue RELATION, devenu long (26 entrées), est
 * groupé — l'ORDRE de la liste reste l'unique source de vérité, ce champ ne fait que l'annoter.
 */
data class TypeOption(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val groupRes: Int? = null,
)

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

    /**
     * v7.1.44 — catalogue étendu à 25 types + « Personnalisé », groupé pour rester lisible.
     * Les paires asymétriques sont ADJACENTES (source puis inverse) et « mother » reste en
     * TÊTE : c'est `RELATION.first()` qui fournit le type par défaut d'une ligne ajoutée
     * depuis la section Relations (cf. ProfileFormFields) — l'ordre n'est donc pas cosmétique.
     */
    private val GROUP_FAMILY = R.string.relation_group_family
    private val GROUP_PRO = R.string.relation_group_professional
    private val GROUP_SOCIAL = R.string.relation_group_social

    val RELATION = listOf(
        // ── Famille ──────────────────────────────────────────────────────────
        TypeOption("mother", R.string.relation_type_mother, GROUP_FAMILY),
        TypeOption("father", R.string.relation_type_father, GROUP_FAMILY),
        // v7.1.43 — « parent » (neutre) : inverse d'« enfant » produit par le miroir, et
        // type saisissable à part entière. Idem « employé », inverse de « manager ».
        TypeOption("parent", R.string.relation_type_parent, GROUP_FAMILY),
        TypeOption("child", R.string.relation_type_child, GROUP_FAMILY),
        TypeOption("brother", R.string.relation_type_brother, GROUP_FAMILY),
        TypeOption("sister", R.string.relation_type_sister, GROUP_FAMILY),
        TypeOption("spouse", R.string.relation_type_spouse, GROUP_FAMILY),
        TypeOption("partner", R.string.relation_type_partner, GROUP_FAMILY),

        // ── Professionnel ────────────────────────────────────────────────────
        TypeOption("manager", R.string.relation_type_manager, GROUP_PRO),
        TypeOption("employee", R.string.relation_type_employee, GROUP_PRO),
        TypeOption("colleague", R.string.relation_type_colleague, GROUP_PRO),
        TypeOption("teacher", R.string.relation_type_teacher, GROUP_PRO),
        TypeOption("student", R.string.relation_type_student, GROUP_PRO),
        TypeOption("mentor", R.string.relation_type_mentor, GROUP_PRO),
        TypeOption("mentee", R.string.relation_type_mentee, GROUP_PRO),
        TypeOption("coach", R.string.relation_type_coach, GROUP_PRO),
        TypeOption("player", R.string.relation_type_player, GROUP_PRO),
        TypeOption("doctor", R.string.relation_type_doctor, GROUP_PRO),
        TypeOption("patient", R.string.relation_type_patient, GROUP_PRO),
        TypeOption("consultant", R.string.relation_type_consultant, GROUP_PRO),
        TypeOption("client", R.string.relation_type_client, GROUP_PRO),

        // ── Social ───────────────────────────────────────────────────────────
        TypeOption(RELATION_FRIEND, R.string.relation_type_friend, GROUP_SOCIAL),
        TypeOption("best_friend", R.string.relation_type_best_friend, GROUP_SOCIAL),
        TypeOption("classmate", R.string.relation_type_classmate, GROUP_SOCIAL),
        TypeOption("neighbor", R.string.relation_type_neighbor, GROUP_SOCIAL),

        // Toujours en dernier, hors groupe : ouvre le dialogue de libellé libre.
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
) {
    /** Index du segment ANNÉE dans [order] (toujours présent : 0, 1 ou 2). */
    val yearIndex: Int = order.indexOf(DateField.YEAR)

    /**
     * v7.1.38 — vrai si l'ANNÉE est le DERNIER segment (locales DMY/MDY ≈ 10/13 langues) : la
     * saisie « année laissée vide ⇒ sans année » y est NATURELLE (les 4 premiers chiffres = jour
     * + mois, l'année omise est en queue). Faux pour YMD (ja/zh/ko, année EN TÊTE) où l'année
     * vide ne se déduit pas du préfixe → une petite affordance « sans année » est requise.
     */
    val isYearLast: Boolean = yearIndex == order.lastIndex

    /** Ordre des composantes d'une date SANS année (= [order] privé du segment ANNÉE). */
    val monthDayOrder: List<DateField> = order.filter { it != DateField.YEAR }

    /** Longueurs des segments d'une date SANS année (jour & mois = 2 chiffres) → somme = 4. */
    val monthDaySegmentLengths: List<Int> = monthDayOrder.map { 2 }
}

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
            DateField.DAY -> "%02d".format(Locale.ROOT, day)
            DateField.MONTH -> "%02d".format(Locale.ROOT, month)
            DateField.YEAR -> "%04d".format(Locale.ROOT, year)
        }
    }
}

/** Année plancher (impose 4 chiffres) pour une date importante saisie. */
private const val MIN_DATE_YEAR = 1000

/** Année plafond (borne haute saine) — autorise les événements ponctuels futurs (v7.1.29). */
private const val MAX_DATE_YEAR = 9999

/**
 * Valide une date saisie (chiffres bruts) pour l'UI ET la sauvegarde.
 *
 * Une valeur vide est acceptée (date optionnelle) ; sinon la date doit être COMPLÈTE et
 * parseable, avec une **année cohérente à 4 chiffres** entre MIN_DATE_YEAR et MAX_DATE_YEAR.
 * Rejette une année incomplète à 3 chiffres (la longueur attendue n'est pas atteinte →
 * [rawDigitsToMillis] renvoie `null`).
 *
 * v7.1.29 — les **années FUTURES sont désormais autorisées** (événement ponctuel daté, ex.
 * « avril 2027 »). Le futur n'est REFUSÉ que pour [FieldTypes.DATE_BIRTHDAY] (« on ne naît pas
 * dans le futur ») : un anniversaire avec une année > année courante est invalide. Les autres
 * types (anniversaire de mariage, autre, personnalisé) acceptent le futur.
 */
fun isDateLineValid(raw: String, spec: DateFormatSpec, label: String = ""): Boolean {
    if (raw.isBlank()) return true
    // Date SANS année DÉJÀ STOCKÉE (`--MM-dd` : importée B3b, ou saisie YMD via l'affordance) :
    // VALIDE telle quelle. Le futur n'a pas de sens sans année (aucune garde « anniversaire futur »).
    if (DateCanonical.isMonthDay(raw)) return DateCanonical.monthDayOf(raw) != null
    // v7.1.38 — SAISIE year-less « année laissée vide » : 4 chiffres jour/mois valides, UNIQUEMENT
    // pour les locales année-en-dernier (en YMD le year-less passe par `--MM-dd` direct, branche
    // ci-dessus). Pas de garde « futur ». La canonicalisation en `--MM-dd` a lieu à l'enregistrement.
    if (spec.isYearLast && raw.length == spec.monthDaySegmentLengths.sum())
        return rawDigitsToMonthDay(raw, spec.monthDayOrder) != null
    val millis = rawDigitsToMillis(raw, spec) ?: return false
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val year = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
    if (year !in MIN_DATE_YEAR..MAX_DATE_YEAR) return false
    // Seul l'anniversaire interdit le futur (année > année courante).
    if (label == FieldTypes.DATE_BIRTHDAY && year > currentYear) return false
    return true
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
    return "%04d-%02d-%02d".format(Locale.ROOT, d.year, d.monthValue, d.dayOfMonth)
}

/** ISO `yyyy-MM-dd` → chiffres bruts dans l'ordre de [spec] (pour réinjection au formulaire). */
fun isoToRawDigits(iso: String, spec: DateFormatSpec): String = try {
    val d = LocalDate.parse(iso)
    spec.order.joinToString("") {
        when (it) {
            DateField.DAY -> "%02d".format(Locale.ROOT, d.dayOfMonth)
            DateField.MONTH -> "%02d".format(Locale.ROOT, d.monthValue)
            DateField.YEAR -> "%04d".format(Locale.ROOT, d.year)
        }
    }
} catch (_: Exception) {
    ""
}

// ─────────────────────────────────────────────────────────────────────────────
// v7.1.38 — Pont SANS année (`--MM-dd`) : pendants year-less de rawDigitsToIso /
// isoToRawDigits. La forme STOCKÉE d'une date sans année est `--MM-dd` (acquis B3b,
// v7.1.37) ; le formulaire la manipule en 4 chiffres bruts ordonnés jour/mois selon la
// locale. La LOGIQUE stricte « 4 chiffres d'année » de `isIso`/`rawDigitsToIso` est inchangée
// (dates DATÉES non régressées) ; seul le FORMATAGE des chiffres est forcé en `Locale.ROOT` →
// la forme canonique reste ASCII même en arabe/persan (sinon `--٠٣-١٥`/`١٩٩٠-...` casserait
// `isIso`/`isMonthDay` et `LocalDate.parse`). Vérifié sur device en arabe (v7.1.38).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 4 chiffres bruts du formulaire (ordre [monthDayOrder]) → `--MM-dd` si le couple jour/mois
 * forme un [java.time.MonthDay] RÉELLEMENT valide (mois 1-12, jour ≤ max du mois, `--02-29`
 * accepté car l'année n'est pas fixée), sinon `null`. Pendant year-less de [rawDigitsToIso].
 */
fun rawDigitsToMonthDay(raw: String, monthDayOrder: List<DateField>): String? {
    if (raw.length != 4) return null
    var idx = 0
    var day = 0; var month = 0
    for (field in monthDayOrder) {
        val part = raw.substring(idx, idx + 2).toIntOrNull() ?: return null
        when (field) {
            DateField.DAY -> day = part
            DateField.MONTH -> month = part
            DateField.YEAR -> return null // ordre sans année : YEAR ne doit pas y figurer
        }
        idx += 2
    }
    return try {
        java.time.MonthDay.of(month, day) // valide mois (1..12) + jour max (29/02 OK)
        "--%02d-%02d".format(Locale.ROOT, month, day)
    } catch (_: Exception) {
        null
    }
}

/**
 * `--MM-dd` → 4 chiffres bruts jour/mois dans l'ordre [monthDayOrder] (réinjection au
 * formulaire en mode year-less, et relecture propre — fin du « --/03/-15 »). Pendant
 * year-less de [isoToRawDigits]. Renvoie [value] inchangée si ce n'est pas un `--MM-dd`
 * valide (meilleur effort, jamais de perte).
 */
fun monthDayToRawDigits(value: String, monthDayOrder: List<DateField>): String {
    val (month, day) = DateCanonical.monthDayOf(value) ?: return value
    return monthDayOrder.joinToString("") {
        when (it) {
            DateField.DAY -> "%02d".format(Locale.ROOT, day)
            DateField.MONTH -> "%02d".format(Locale.ROOT, month)
            DateField.YEAR -> ""
        }
    }
}

/**
 * Valeur STOCKÉE d'une date (ISO canonique OU chiffres bruts hérités) → chiffres bruts du
 * FORMULAIRE dans la locale courante. Une donnée héritée ambiguë non convertible est
 * conservée telle quelle (meilleur effort, jamais de perte).
 */
fun storedDateToRawDigits(value: String, spec: DateFormatSpec): String {
    if (value.isBlank()) return value
    // v7.1.38 — date SANS année (`--MM-dd`) relue pour la SAISIE :
    //  • locales année-en-dernier → 4 chiffres bruts jour/mois (le champ unique l'affiche en
    //    « 15/03 » via le masque adaptatif ; fin du « --/03/-15 » cosmétique du socle B3b) ;
    //  • locales YMD (année en tête) → conservée TELLE QUELLE en `--MM-dd` : le mode « sans
    //    année » de l'affordance la consomme directement (l'année vide ne se déduit pas du préfixe).
    if (DateCanonical.isMonthDay(value)) {
        return if (spec.isYearLast) monthDayToRawDigits(value, spec.monthDayOrder) else value
    }
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
        val v = line.value
        when {
            // `--MM-dd` (sans année, B3b) et ISO complet sont DÉJÀ canoniques : laissés intacts.
            v.isBlank() || DateCanonical.isIso(v) || DateCanonical.isMonthDay(v) -> line
            // v7.1.38 — état TERMINAL year-less : en locale année-en-dernier, 4 chiffres jour/mois
            // valides ⇒ `--MM-dd` (l'année laissée vide fige une date sans année). 5-7 chiffres =
            // incomplet → rawDigitsToIso renvoie null → ligne intacte (la sauvegarde est bloquée).
            spec.isYearLast && v.length == spec.monthDaySegmentLengths.sum() ->
                rawDigitsToMonthDay(v, spec.monthDayOrder)?.let { line.copy(value = it) } ?: line
            else -> rawDigitsToIso(v, spec)?.let { line.copy(value = it) } ?: line
        }
    }

/**
 * v7.1.37 (B3b) — Formate une valeur de date STOCKÉE pour l'affichage LONG localisé :
 *  - ISO `yyyy-MM-dd` → « d MMMM yyyy » (avec année) ;
 *  - `--MM-dd` (sans année) → squelette « MMMMd » localisé par l'OS (« 15 mars », « March 15 »,
 *    « 3月15日 », arabe RTL) — l'année factice de calcul n'apparaît PAS.
 * Renvoie `null` si [value] n'est pas une date affichable (consommateur : `mapNotNull`/skip).
 */
fun formatStoredDateLong(value: String, locale: Locale): String? {
    DateCanonical.monthDayOf(value)?.let { (month, day) ->
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMd")
        val cal = Calendar.getInstance().apply {
            clear(); set(2020, month - 1, day, 12, 0, 0) // 2020 bissextile → 29/02 OK ; année non affichée
        }
        return SimpleDateFormat(pattern, locale).format(cal.time)
    }
    val millis = storedDateToMillis(value) ?: return null
    return SimpleDateFormat("d MMMM yyyy", locale).format(Date(millis))
}
