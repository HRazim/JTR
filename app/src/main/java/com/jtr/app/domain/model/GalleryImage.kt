package com.jtr.app.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Image de la GALERIE d'un contact (v7.1.88, DB v23) — plusieurs photos-souvenirs par
 * personne, en plus de l'unique [Person.photoUri] qui reste la photo de profil.
 *
 * Stockage aligné sur l'existant : le fichier est copié dans `filesDir` (même chaîne
 * privacy-first que la photo de profil, Photo Picker sans permission) et SEUL son chemin
 * vit en base — exactement comme `Person.photoUri` et `Category.imagePath`. C'est ce qui
 * permet à l'export `.jtr` de l'embarquer gratuitement : son mécanisme packague les
 * fichiers à partir des CHAMPS image de la base, jamais en balayant un dossier.
 *
 * ⚠️ [personId] est un **TEXT** : `Person.id` est un UUID String, pas un entier.
 *
 * CASCADE sur [personId] : la suppression DÉFINITIVE d'un contact efface ses lignes de
 * galerie. (Le fichier sur disque, lui, n'est PAS effacé par la base — ce nettoyage
 * n'existe pas encore dans l'app, même pour les photos de profil.)
 *
 * [position] et [caption] sont provisionnés DÈS MAINTENANT bien qu'aucune UI ne les
 * exploite : réordonner ou légender plus tard n'exigera alors AUCUNE autre migration.
 */
@Entity(
    tableName = "gallery_images",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId")]
)
data class GalleryImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val personId: String,
    /** Chemin du fichier dans `filesDir` (ou jeton `jtr-media://` le temps d'un export). */
    val path: String,
    /** Ordre manuel futur (0 = non ordonné) — tri secondaire sur [createdAt]. */
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    /** Légende future, facultative. */
    val caption: String? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)
