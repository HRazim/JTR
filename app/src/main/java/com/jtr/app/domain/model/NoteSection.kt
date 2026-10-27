package com.jtr.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Section de notes personnalisable (v7.0.3) — cœur de JTR : un bloc de notes libre
 * avec son propre titre et son icône.
 *
 * Persistée en LISTE JSON dans l'entité [Person] (colonne `noteSections TEXT NOT NULL
 * DEFAULT '[]'`), cohérente avec le motif [DynamicLine] (même (dé)sérialisation
 * kotlinx via les TypeConverters Room).
 *
 * - `id`     : identifiant stable (réordonnancement, suivi des éditions).
 * - `iconKey`: CLÉ TEXTE stable d'un jeu d'icônes curé (PAS un id de ressource) →
 *   robuste aux versions (cf. `NoteIcons`).
 * - `order`  : rang d'affichage (persisté ; le glisser-déposer le met à jour).
 */
@Serializable
data class NoteSection(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val iconKey: String = NOTE_ICON_NOTES,
    val content: String = "",
    val order: Int = 0
)

/** Clés d'icônes par défaut (stables) utilisées par le backfill des notes héritées. */
const val NOTE_ICON_NOTES = "notes"
const val NOTE_ICON_HEART = "heart"

/**
 * Backfill SANS PERTE (v7.0.3) : convertit les colonnes héritées `notes` (Misc notes)
 * et `likes` (What they like) en sections, contenu IDENTIQUE préservé. Les titres par
 * défaut sont passés en paramètre (localisés par l'appelant — ViewModel / vue lecture).
 *
 * Fonction PURE (testable) : réutilisée par le chargement paresseux du formulaire et
 * par l'affichage en lecture, garantissant qu'aucune note existante n'est jamais perdue.
 */
fun deriveNoteSections(
    notes: String?,
    likes: String?,
    notesTitle: String,
    likesTitle: String
): List<NoteSection> {
    val out = ArrayList<NoteSection>(2)
    if (!notes.isNullOrBlank()) {
        out += NoteSection(title = notesTitle, iconKey = NOTE_ICON_NOTES, content = notes, order = 0)
    }
    if (!likes.isNullOrBlank()) {
        out += NoteSection(title = likesTitle, iconKey = NOTE_ICON_HEART, content = likes, order = 1)
    }
    return out
}

/**
 * Sections effectives à utiliser pour l'affichage/l'édition : la liste persistée si elle
 * existe, sinon la conversion paresseuse des notes héritées (sans perte). Tant qu'une
 * personne n'a pas été ré-enregistrée, ses notes legacy restent intactes en base.
 */
fun Person.effectiveNoteSections(notesTitle: String, likesTitle: String): List<NoteSection> =
    noteSections.takeIf { it.isNotEmpty() }
        ?: deriveNoteSections(notes, likes, notesTitle, likesTitle)

// ── v7.1.75 — notes simplifiées : UN champ par section, la 1re ligne = le titre ────────

/**
 * Texte UNIQUE d'une section (v7.1.75) : titre et contenu recombinés pour le champ
 * d'édition unique (« 1re ligne = titre »). Concaténation PURE — aucun trim, aucune
 * normalisation. Invariant avec [splitCombinedText] : `combined(split(x)) == x` pour
 * tout x → le texte visible ne change jamais ; seule la répartition title/content peut
 * être redistribuée, et elle est un point fixe dès la première passe (aucune dérive
 * aux cycles d'édition suivants).
 */
fun NoteSection.combinedText(): String = when {
    title.isEmpty() -> content
    content.isEmpty() -> title
    else -> title + "\n" + content
}

/**
 * Découpe du champ unique (v7.1.75) : 1re ligne → titre, reste → contenu.
 *  - pas de retour à la ligne → tout en titre ;
 *  - 1re ligne BLANCHE → rien n'est promu, TOUT reste en contenu (règle ANTI-PERTE :
 *    sans elle, une ligne vide de tête serait perdue au round-trip) ;
 *  - découpe sur le PREMIER '\n' uniquement (indexOf/substring) : aucun trim — lignes
 *    vides internes, CRLF importés, RTL, emojis (surrogate pairs) et espaces de
 *    tête/queue sont préservés octet pour octet.
 */
fun splitCombinedText(text: String): Pair<String, String> {
    val idx = text.indexOf('\n')
    if (idx < 0) return text to ""
    val first = text.substring(0, idx)
    if (first.isBlank()) return "" to text
    return first to text.substring(idx + 1)
}

/** Section mise à jour depuis le champ unique : title/content redistribués, reste intact. */
fun NoteSection.withCombinedText(text: String): NoteSection {
    val (t, c) = splitCombinedText(text)
    return copy(title = t, content = c)
}
