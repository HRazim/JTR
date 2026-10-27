package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.jtr.app.data.backup.BackupPayload
import com.jtr.app.domain.model.GalleryImage
import org.junit.Test

/**
 * Contrat `.jtr` du champ [GalleryImage.modifiedAt] (v7.1.102, DB v25). `BackupManager`
 * sérialise la data class ENTIÈRE par réflexion Gson — ce test fige les deux moitiés du
 * contrat, les mêmes qui ont été prouvées sur appareil pour les colonnes v24 :
 *
 *  1. **Export** : une ligne éditée emporte son `modifiedAt` ; une ligne jamais éditée
 *     OMET simplement la clé (Gson saute les nulls) — l'archive reste identique à avant.
 *  2. **Import d'un ancien `.jtr`** (écrit avant v25, sans la clé) : le champ ressort
 *     `null` (« jamais édité ») — aucun crash, aucune valeur inventée.
 */
class GalleryImageBackupFieldTest {

    private val gson = Gson()

    private fun img(modifiedAt: Long? = null) = GalleryImage(
        id = 7L,
        personId = "p1",
        path = "/data/gallery/souvenir.jpg",
        position = 3,
        caption = "Avant v25",
        createdAt = 1_700_000_000_000L,
        latitude = 48.8588897,
        longitude = 2.320041,
        placeLabel = "Tour Eiffel, Paris, France",
        modifiedAt = modifiedAt
    )

    @Test
    fun `an edited image exports its modifiedAt and survives the round-trip`() {
        val edited = img(modifiedAt = 1_800_000_000_000L)

        val json = gson.toJson(edited)
        assertThat(json).contains("\"modifiedAt\":1800000000000")

        val back = gson.fromJson(json, GalleryImage::class.java)
        assertThat(back).isEqualTo(edited)
    }

    @Test
    fun `a never-edited image omits the key, exactly like a pre-v25 archive`() {
        val json = gson.toJson(img(modifiedAt = null))
        assertThat(json).doesNotContain("modifiedAt")
    }

    @Test
    fun `a pre-v25 jtr payload imports with modifiedAt null`() {
        // Item de galerie tel qu'écrit par un export v24 : TOUTES les clés d'époque,
        // AUCUNE clé modifiedAt.
        val legacyPayload = """
            {"formatVersion":1,"exportedAt":1700000000000,
             "galleryImages":[{"id":7,"personId":"p1","path":"/data/gallery/souvenir.jpg",
               "position":3,"caption":"Avant v25","createdAt":1700000000000,
               "latitude":48.8588897,"longitude":2.320041,
               "placeLabel":"Tour Eiffel, Paris, France"}]}
        """.trimIndent()

        val payload = gson.fromJson(legacyPayload, BackupPayload::class.java)
        val restored = payload.galleryImages!!.single()

        // Les champs d'époque sont intacts…
        assertThat(restored.path).isEqualTo("/data/gallery/souvenir.jpg")
        assertThat(restored.caption).isEqualTo("Avant v25")
        assertThat(restored.createdAt).isEqualTo(1_700_000_000_000L)
        assertThat(restored.placeLabel).isEqualTo("Tour Eiffel, Paris, France")
        // …et le nouveau champ vaut null (« jamais édité »), sans crash ni valeur inventée.
        assertThat(restored.modifiedAt).isNull()
    }
}
