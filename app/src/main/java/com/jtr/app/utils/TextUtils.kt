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
 * NOTE i18n / RTL : seul le bloc Unicode latin « Combining Diacritical Marks »
 * (U+0300–U+036F) est retiré. Les diacritiques ARABES (harakat, U+064B+) ne sont
 * PAS dans ce bloc → l'arabe est préservé tel quel ; `lowercase()` est un no-op sur
 * les scripts sans casse. La normalisation ne dénature donc aucun script non-latin.
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

/** Séparateur de mots : tout bloc d'espaces (espaces multiples, tabulations, retours). */
private val WHITESPACE = Regex("\\s+")

/**
 * Découpe une requête en TOKENS de recherche normalisés — base UNIQUE du moteur de
 * recherche de l'app (à passer ensuite à [matchesSearch] / [matchesAllTokens]).
 *
 *   - trim : les blocs d'espaces en tête/queue produisent des morceaux vides, éliminés
 *     ("Nathan " → ["nathan"], pas d'échec sur l'espace final) ;
 *   - réduction des espaces multiples internes ("Nathan   Jamel" → ["nathan","jamel"]) ;
 *   - accents/casse insensibles (via [normalizeForSearch]).
 */
fun String.searchTokens(): List<String> =
    normalizeForSearch().split(WHITESPACE).filter { it.isNotEmpty() }

/**
 * Vrai si ce texte libre contient TOUS les [tokens] en sous-chaîne (tokens déjà
 * normalisés via [searchTokens]). Une requête sans token (vide) matche tout.
 *
 * Sert aux recherches sur un seul libellé : nom d'un contact dans le sélecteur de
 * relation, nom d'une catégorie/dossier, nom affiché d'un contact natif à importer.
 */
fun String.matchesAllTokens(tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return true
    val blob = normalizeForSearch()
    return tokens.all { blob.contains(it) }
}

/**
 * Moteur de recherche multi-critères CENTRAL et UNIQUE (Accueil, catégories,
 * sélection de contacts, relations…). Toute recherche de personne de l'app passe
 * par ici → comportement strictement identique partout.
 *
 * Construit un « blob » cherchable normalisé = concaténation de TOUS les champs
 * cherchables (nom complet + sous-champs, surnom, nom phonétique, entreprise,
 * poste/département, ville/origine, notes/likes, téléphones/emails — scalaires ET
 * lignes dynamiques — et les RELATIONS : noms liés + libellés). Une personne matche
 * si CHAQUE token (mot de la requête) y figure en sous-chaîne : **ET sur les tokens,
 * OU sur les champs**. Ainsi « Nathan Jamel » trouve { firstName=Nathan, lastName=
 * Jamel } (chaque mot dans un champ différent), tout comme « Nathan », « tha » ou
 * « jamel nathan » (ordre libre).
 *
 * [tokens] doivent provenir de [searchTokens] (tokenisation faite une seule fois en
 * amont, hors de la boucle de filtrage). [relationTypeLabel] résout une clé de type
 * standard en libellé localisé DÉJÀ normalisé (« friend » → « ami »…), fourni par la
 * couche UI ; `null` pour une clé inconnue (libellé personnalisé, pris brut).
 */
fun Person.matchesSearch(
    tokens: List<String>,
    relationTypeLabel: (String) -> String? = { null }
): Boolean {
    if (tokens.isEmpty()) return true
    val blob = buildSearchBlob(relationTypeLabel)
    return tokens.all { blob.contains(it) }
}

/** Concatène, normalisés et séparés par des espaces, tous les champs cherchables. */
private fun Person.buildSearchBlob(relationTypeLabel: (String) -> String?): String {
    val sb = StringBuilder()
    fun add(value: String?) {
        if (!value.isNullOrBlank()) sb.append(value.normalizeForSearch()).append(' ')
    }
    // Nom complet + sous-champs avancés
    add(firstName); add(lastName); add(middleName); add(prefix); add(suffix)
    add(nickname); add(phonetic)
    // Professionnel
    add(company); add(jobTitle); add(department)
    // Lieu
    add(city); add(origin)
    // Notes héritées (colonnes legacy `notes`/`likes`, conservées sans perte).
    add(notes); add(likes)
    // Sections de notes (v7.0.3) — UI PRINCIPALE des notes : on indexe le titre ET le
    // contenu de CHAQUE section pour que la recherche unifiée retrouve le texte saisi
    // (sinon une note écrite dans une section restait introuvable). Liste non-null.
    noteSections.forEach { add(it.title); add(it.content) }
    // Contacts (scalaires + lignes dynamiques)
    add(phoneNumber); add(email)
    phoneLines?.forEach { add(it.value) }
    emailLines?.forEach { add(it.value) }
    // Relations : nom du contact lié + libellé (type localisé normalisé, sinon brut)
    relationLines?.forEach { line ->
        add(line.value)
        val label = relationTypeLabel(line.label) ?: line.label.normalizeForSearch()
        if (label.isNotBlank()) sb.append(label).append(' ')
    }
    return sb.toString()
}
