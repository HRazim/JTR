package com.jtr.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jtr.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Validation stricte de la migration Room v11 → v12 (refonte « Contacts Google »).
 *
 * [MigrationTestHelper] charge dynamiquement les schémas exportés (assets du test
 * APK). `runMigrationsAndValidate` valide structurellement le schéma résultant
 * contre `12.json` : toute non-conformité (colonne manquante, mauvaise affinité,
 * NOT NULL inattendu…) ferait échouer l'appel avec une IllegalStateException —
 * exactement le crash runtime que ce test prévient.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate11To12_keepsLegacyContactAndDenormalizesToJson() {
        val personId = "legacy-1"
        val birthMillis = 631152000000L // 1990-01-01 (midi local approx.)

        // 1) Crée la base au schéma v11 et insère un contact « legacy » n'utilisant
        //    que les colonnes scalaires (phoneNumber, email, birthdate).
        helper.createDatabase(testDb, 11).use { db ->
            db.execSQL(
                """
                INSERT INTO persons
                    (id, firstName, lastName, gender, photoUri, birthdate, birthdateNotify,
                     city, cityLat, cityLng, cityNotify, isFavorite, lastContactedAt,
                     notes, likes, origin, phoneNumber, email, createdAt, deletedAt)
                VALUES
                    ('$personId', 'Alice', 'Legacy', NULL, NULL, $birthMillis, 0,
                     'Chicoutimi', NULL, NULL, 0, 0, NULL,
                     NULL, NULL, NULL, '+1 418 555 0199', 'alice@example.com', 1700000000000, NULL)
                """.trimIndent()
            )
        }

        // 2) Applique et VALIDE la migration v11 → v12 (conformité du schéma Room).
        val db = helper.runMigrationsAndValidate(
            testDb, 12, true, AppDatabase.MIGRATION_11_12
        )

        // 3) Le contact a survécu ; les scalaires dénormalisés sont intacts.
        db.query(
            "SELECT phoneNumber, email, birthdate, phoneLines, emailLines, " +
                "dateLines, relationLines, prefix, nickname FROM persons WHERE id = ?",
            arrayOf(personId)
        ).use { c ->
            assertTrue("Le contact legacy doit survivre à la migration", c.moveToFirst())

            assertEquals("+1 418 555 0199", c.getString(0))
            assertEquals("alice@example.com", c.getString(1))
            assertEquals(birthMillis, c.getLong(2))

            // 4) Repli SQL inverse : phoneNumber/email convertis en JSON cohérent.
            assertEquals(
                """[{"value":"+1 418 555 0199","label":"mobile"}]""",
                c.getString(3)
            )
            assertEquals(
                """[{"value":"alice@example.com","label":"home"}]""",
                c.getString(4)
            )

            // 5) Colonnes non converties en SQL : NULL (dates reconstruites côté app
            //    depuis birthdate ; relations et sous-champs de nom encore vides).
            assertTrue("dateLines doit être NULL après migration", c.isNull(5))
            assertTrue("relationLines doit être NULL après migration", c.isNull(6))
            assertTrue("prefix doit être NULL après migration", c.isNull(7))
            assertTrue("nickname doit être NULL après migration", c.isNull(8))
        }
    }

    /**
     * Migration v17 → v18 (tri des catégories en parité contacts) : les nouvelles
     * colonnes d'horodatage sont AJOUTÉES sans perte, et les lignes préexistantes
     * sont BACKFILLÉES (> 0). `runMigrationsAndValidate(..., true, ...)` valide en
     * prime la conformité STRUCTURELLE du schéma résultant à `18.json` (présence et
     * affinité de categories.createdAt, category_groups.createdAt, join.addedAt).
     */
    @Test
    @Throws(IOException::class)
    fun migrate17To18_addsTimestampsAndBackfillsExistingRows() {
        val categoryId = "cat-legacy"

        helper.createDatabase(testDb, 17).use { db ->
            db.execSQL(
                "INSERT INTO categories " +
                    "(id, name, color, icon, imagePath, `order`, isFavorite, position, parentGroupId, deletedAt) " +
                    "VALUES ('$categoryId', 'Famille', '#2E86C1', 'folder', NULL, 0, 0, 0, NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO category_groups (id, name, position, isFavorite, imagePath, parentGroupId) " +
                    "VALUES (1, 'Proches', 0, 0, NULL, NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 18, true, AppDatabase.MIGRATION_17_18
        )

        db.query("SELECT createdAt FROM categories WHERE id = ?", arrayOf(categoryId)).use { c ->
            assertTrue("La catégorie doit survivre à la migration", c.moveToFirst())
            assertTrue("categories.createdAt doit être backfillé (> 0)", c.getLong(0) > 0L)
        }
        db.query("SELECT createdAt FROM category_groups WHERE id = 1").use { c ->
            assertTrue("Le dossier doit survivre à la migration", c.moveToFirst())
            assertTrue("category_groups.createdAt doit être backfillé (> 0)", c.getLong(0) > 0L)
        }
    }
}
