package com.jtr.app.utils

import com.jtr.app.domain.model.Person
import java.text.Normalizer

/**
 * Normalise un String pour une comparaison accent-insensitive et case-insensitive.
 *
 * Étapes :
 *   1. NFD — décompose les caractères composés ("é" → "e" + combining accent U+0301)
 *   2. Supprime tous les "Combining Diacritical Marks" (U+0300–U+036F)
 *   3. Passe en minuscules
 *
 * Exemples :
 *   "Thérèse"  → "therese"
 *   "François" → "francois"
 *   "JOSÉ"     → "jose"
 */
fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()

/**
 * Retourne true si [this] contient [other], après normalisation des deux côtés.
 * Remplace un simple `contains(query, ignoreCase = true)` avec support des accents.
 */
fun String.containsNormalized(other: String): Boolean =
    this.normalizeForSearch().contains(other.normalizeForSearch())

/**
 * Moteur de recherche multi-critères PARTAGÉ (Accueil, catégories, sélection de
 * contacts). [normalizedQuery] doit déjà être passé par [normalizeForSearch].
 *
 * Champs interrogés : prénom/nom (+ surnom), entreprise, poste/département,
 * ville/origine, notes/« ce qu'il aime », téléphone/email (scalaires + lignes
 * dynamiques) et les RELATIONS — noms liés, libellés personnalisés, et types
 * standards via [relationTypeLabel] (clé → libellé localisé déjà normalisé,
 * fourni par la couche UI ; `null` si la clé est inconnue).
 */
fun Person.matchesSearch(
    normalizedQuery: String,
    relationTypeLabel: (String) -> String? = { null }
): Boolean {
    if (normalizedQuery.isBlank()) return true

    val scalarFields = listOfNotNull(
        firstName, lastName, nickname,
        company, jobTitle, department,
        city, origin,
        notes, likes,
        phoneNumber, email
    )
    if (scalarFields.any { it.normalizeForSearch().contains(normalizedQuery) }) return true

    val lineValues = listOfNotNull(phoneLines, emailLines)
        .flatten().map { it.value }
    if (lineValues.any { it.normalizeForSearch().contains(normalizedQuery) }) return true

    // Relations : nom du contact lié, libellé personnalisé brut, ou type standard
    // localisé (« Ami », « Collègue »… — résolu par l'appelant).
    relationLines?.forEach { line ->
        if (line.value.normalizeForSearch().contains(normalizedQuery)) return true
        val localized = relationTypeLabel(line.label)
        val label = localized ?: line.label.normalizeForSearch()
        if (label.contains(normalizedQuery)) return true
    }
    return false
}
