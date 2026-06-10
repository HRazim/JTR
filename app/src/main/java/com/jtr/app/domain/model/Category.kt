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
    val deletedAt: Long? = null
)
