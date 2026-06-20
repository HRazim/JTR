package com.jtr.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Ligne de saisie dynamique générique (téléphone, email, date, relation) — v4.5.
 *
 * Persistée en JSON dans l'entité [Person] via les TypeConverters Room (DB v12).
 * `value` contient la donnée saisie (pour les dates : chiffres bruts dans l'ordre
 * de la locale) et `label` la clé de type sélectionnée (ex. "mobile", "birthday")
 * — ou, pour un type « Personnalisé », directement le libellé saisi par l'utilisateur.
 *
 * `notify` (v4.5) ne concerne que les dates importantes : quand il vaut `true`,
 * une alerte est planifiée pour cette date (cf. [com.jtr.app.worker.ReminderScheduler]).
 * Valeur par défaut `false` ⇒ rétrocompatible avec les JSON pré-v4.5 (champ absent).
 *
 * `reminderOffsetMinutes` (v7.0) — délai de rappel AVANT l'ancre minuit (00:00) du
 * jour J, exprimé en minutes (1 h = 60, 1 jour = 1440, 1 semaine = 10080). `0` (défaut)
 * = « Le jour J » (à minuit). Comme ce champ est sérialisé en JSON, les anciens profils
 * (champ absent) le désérialisent à `0` sans migration de colonne — la migration Room
 * v18→v19 ne concerne QUE la projection scalaire de l'anniversaire.
 *
 * `linkedPersonId` (v7.1.6) — INTÉGRITÉ DES RELATIONS : pour une ligne de type relation,
 * c'est l'**identifiant STABLE** ([Person.id], clé unique) du contact lié — la SEULE clé
 * d'une relation. `value` ne sert qu'à l'AFFICHAGE (nom courant, modifiable, non unique) et
 * ne doit JAMAIS servir à naviguer/résoudre/synchroniser. `null` = texte libre (contact non
 * référencé) ou relation héritée non encore reliée. Sérialisé en JSON → rétrocompatible
 * (JSON sans le champ ⇒ `null`), aucune migration de colonne. Sans objet pour les autres
 * types de ligne (téléphone, email, date) qui le laissent `null`.
 */
@Serializable
data class DynamicLine(
    val id: String = UUID.randomUUID().toString(),
    val value: String = "",
    val label: String = "",
    val notify: Boolean = false,
    val reminderOffsetMinutes: Int = 0,
    val linkedPersonId: String? = null
)
