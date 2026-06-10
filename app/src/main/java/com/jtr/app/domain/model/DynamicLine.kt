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
 * [com.jtr.app.worker.ImportantDateCheckWorker] envoie une alerte le jour dit.
 * Valeur par défaut `false` ⇒ rétrocompatible avec les JSON pré-v4.5 (champ absent).
 */
@Serializable
data class DynamicLine(
    val id: String = UUID.randomUUID().toString(),
    val value: String = "",
    val label: String = "",
    val notify: Boolean = false
)
