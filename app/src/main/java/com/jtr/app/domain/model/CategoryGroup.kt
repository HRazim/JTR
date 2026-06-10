package com.jtr.app.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CategoryGroup — dossier/groupe regroupant plusieurs catégories (ex. « Famille »).
 *
 * v4.5 (DB v14). Une catégorie référence son groupe via `Category.parentGroupId`
 * (nullable). Clé primaire `Long` auto-générée pour rester légère côté UI.
 */
@Entity(tableName = "category_groups")
data class CategoryGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
    // v4.5 (DB v15) — illustration de couverture optionnelle (style tuile catégorie).
    val imagePath: String? = null,
    // v4.5 (DB v16) — groupe parent : permet les SOUS-GROUPES imbriqués (null = racine).
    val parentGroupId: Long? = null
)
