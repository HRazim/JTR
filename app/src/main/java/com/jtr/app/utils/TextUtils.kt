package com.jtr.app.utils

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
