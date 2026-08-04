package com.jtr.app.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Category — Entité pour organiser les contacts par groupes.
 * [PP3] Nouvelle fonctionnalité : Famille, Amis, Travail, etc.
 *
 * v4.5 (DB v14) : tri personnalisé ([position]) + favoris flottants ([isFavorite])
 * + appartenance optionnelle à un dossier/groupe ([parentGroupId] → CategoryGroup).
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String = "#2E86C1",
    val icon: String = "folder",
    val imagePath: String? = null,
    val order: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    val parentGroupId: Long? = null,
    // v6.1.7 (DB v18) — date de création, pour le tri « Création » des catégories
    // (parité avec les contacts). Défaut SQL "0" : la migration v17→v18 backfille les
    // lignes existantes à l'horodatage de migration.
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    // v7.1.48 (DB v22) — dernière modification du CONTENU de la catégorie (nom, couleur,
    // image). Estampillé par CategoryRepository.update() ; les changements d'ORGANISATION
    // (favori, position, dossier) et les mouvements de membres n'y touchent PAS — l'activité
    // des membres est dérivée à la volée (PersonCategoryDao.getCategoryActivity).
    // Contrat identique à Person.updatedAt : défaut SQL "0", backfillé depuis createdAt par
    // la migration v21→v22 (une catégorie jamais modifiée a bien updatedAt == createdAt).
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
