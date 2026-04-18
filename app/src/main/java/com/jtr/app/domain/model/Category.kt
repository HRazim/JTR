package com.jtr.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Category — Entité pour organiser les contacts par groupes.
 * [PP3] Nouvelle fonctionnalité : Famille, Amis, Travail, etc.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String = "#2E86C1",
    val icon: String = "folder",
    val imagePath: String? = null,
    val order: Int = 0,
    val deletedAt: Long? = null
)
