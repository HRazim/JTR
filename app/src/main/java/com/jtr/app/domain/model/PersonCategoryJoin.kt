package com.jtr.app.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Table de jointure Many-to-Many entre Person et Category.
 *
 * Contraintes :
 * - CASCADE sur personId  : si la personne est supprimée définitivement, ses liens sont supprimés.
 * - CASCADE sur categoryId: si la catégorie est supprimée définitivement, ses liens sont supprimés.
 * - Index sur categoryId  : optimise les requêtes "contacts de la catégorie X".
 *
 * Garantie d'intégrité : supprimer un contact d'une catégorie via cette table ne supprime
 * ni le contact lui-même, ni ses autres associations avec d'autres catégories.
 */
@Entity(
    tableName = "person_category_join",
    primaryKeys = ["personId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class PersonCategoryJoin(
    val personId: String,
    val categoryId: String
)
