package com.jtr.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jtr.app.data.local.AppDatabase
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Calendar

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
     * Migration v12 → v13 (champs professionnels, v4.x) : les 3 colonnes nullables
     * jobTitle / department / company sont AJOUTÉES sans perte. Aucune donnée
     * préexistante n'est touchée, et les nouvelles colonnes valent NULL (et non ''),
     * ce qui permet à l'UI de distinguer « jamais renseigné » de « vidé ».
     * `runMigrationsAndValidate(..., true, ...)` valide en prime la conformité
     * STRUCTURELLE du schéma résultant à `13.json`.
     */
    @Test
    @Throws(IOException::class)
    fun migrate12To13_addsJobColumnsAsNullWithoutLoss() {
        val personId = "person-pre-v13"

        helper.createDatabase(testDb, 12).use { db ->
            db.execSQL(
                "INSERT INTO persons " +
                    "(id, firstName, lastName, birthdate, birthdateNotify, cityNotify, " +
                    "isFavorite, city, notes, createdAt) " +
                    "VALUES (?, 'Émile', 'Dupont', 631152000000, 1, 0, 1, " +
                    "'Chicoutimi', 'Collègue de bureau', 1700000000000)",
                arrayOf<Any?>(personId)
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 13, true, AppDatabase.MIGRATION_12_13
        )

        db.query(
            "SELECT firstName, lastName, city, notes, birthdate, isFavorite, " +
                "jobTitle, department, company FROM persons WHERE id = ?",
            arrayOf(personId)
        ).use { c ->
            assertTrue("Le contact doit survivre à la migration", c.moveToFirst())
            // Aucune perte sur les colonnes existantes.
            assertEquals("Émile", c.getString(0))
            assertEquals("Dupont", c.getString(1))
            assertEquals("Chicoutimi", c.getString(2))
            assertEquals("Collègue de bureau", c.getString(3))
            assertEquals(631152000000L, c.getLong(4))
            assertEquals(1, c.getInt(5))
            // Les 3 nouvelles colonnes sont NULL (« jamais renseigné »).
            assertTrue("jobTitle doit être NULL", c.isNull(6))
            assertTrue("department doit être NULL", c.isNull(7))
            assertTrue("company doit être NULL", c.isNull(8))
        }
    }

    /**
     * Migration v13 → v14 — LA PLUS RISQUÉE du lot (audit H4) : elle ne fait pas que
     * des ADD COLUMN, elle exécute deux `UPDATE` de backfill et CRÉE une table.
     *
     * Trois pièges couverts ici :
     *  1. `UPDATE persons SET updatedAt = createdAt` doit être PAR LIGNE (et non une
     *     constante) → deux contacts aux `createdAt` DIFFÉRENTS.
     *  2. `UPDATE categories SET position = \`order\`` porte sur une colonne dont le nom
     *     est un MOT-CLÉ SQL réservé (`order`), échappé par des backticks. Une erreur
     *     d'échappement donnerait soit une exception, soit — pire — un tri silencieusement
     *     faux. On insère donc des `order` DISTINCTS et NON TRIVIAUX (7, 3, 0) : un
     *     backfill constant, inversé ou nul serait détecté.
     *  3. La table `category_groups` est CRÉÉE par la migration : on vérifie qu'elle
     *     existe, qu'elle est utilisable en écriture et que son AUTOINCREMENT fonctionne.
     */
    @Test
    @Throws(IOException::class)
    fun migrate13To14_backfillsUpdatedAtAndEscapedOrderAndCreatesGroups() {
        val oldCreatedAt = 1_600_000_000_000L
        val newCreatedAt = 1_700_000_000_000L

        helper.createDatabase(testDb, 13).use { db ->
            // Deux contacts aux createdAt DIFFÉRENTS (piège 1).
            db.execSQL(
                "INSERT INTO persons (id, firstName, birthdateNotify, cityNotify, " +
                    "isFavorite, createdAt) VALUES ('p-old', 'Ancien', 0, 0, 0, ?)",
                arrayOf<Any?>(oldCreatedAt)
            )
            db.execSQL(
                "INSERT INTO persons (id, firstName, birthdateNotify, cityNotify, " +
                    "isFavorite, createdAt) VALUES ('p-new', 'Recent', 0, 0, 0, ?)",
                arrayOf<Any?>(newCreatedAt)
            )
            // Trois catégories aux `order` distincts et non triviaux (piège 2).
            db.execSQL(
                "INSERT INTO categories (id, name, color, icon, imagePath, `order`, deletedAt) " +
                    "VALUES ('c-a', 'Amis', '#2E86C1', 'folder', NULL, 7, NULL)"
            )
            db.execSQL(
                "INSERT INTO categories (id, name, color, icon, imagePath, `order`, deletedAt) " +
                    "VALUES ('c-b', 'Boulot', '#C0392B', 'work', NULL, 3, NULL)"
            )
            db.execSQL(
                "INSERT INTO categories (id, name, color, icon, imagePath, `order`, deletedAt) " +
                    "VALUES ('c-c', 'Famille', '#27AE60', 'home', NULL, 0, NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 14, true, AppDatabase.MIGRATION_13_14
        )

        // Piège 1 : updatedAt backfillé DEPUIS createdAt, par ligne.
        db.query("SELECT id, createdAt, updatedAt FROM persons ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("p-new", c.getString(0))
            assertEquals(newCreatedAt, c.getLong(1))
            assertEquals("updatedAt doit valoir createdAt (p-new)", newCreatedAt, c.getLong(2))

            assertTrue(c.moveToNext())
            assertEquals("p-old", c.getString(0))
            assertEquals(oldCreatedAt, c.getLong(1))
            assertEquals("updatedAt doit valoir createdAt (p-old)", oldCreatedAt, c.getLong(2))
        }

        // Piège 2 : position == `order` POUR CHAQUE ligne, valeurs distinctes conservées.
        db.query(
            "SELECT id, name, `order`, position, isFavorite, parentGroupId " +
                "FROM categories ORDER BY id"
        ).use { c ->
            val positionsById = LinkedHashMap<String, Pair<Int, Int>>()
            assertTrue("Les catégories doivent survivre à la migration", c.moveToFirst())
            do {
                positionsById[c.getString(0)] = c.getInt(2) to c.getInt(3)
                // Les colonnes NOT NULL ajoutées prennent leur DEFAULT 0…
                assertEquals("isFavorite doit valoir 0 par défaut", 0, c.getInt(4))
                // …et parentGroupId (nullable) reste NULL (aucun dossier assigné).
                assertTrue("parentGroupId doit être NULL", c.isNull(5))
            } while (c.moveToNext())

            assertEquals("Les 3 catégories doivent être présentes", 3, positionsById.size)
            // `order` intact ET recopié dans position, sans écrasement ni constante.
            assertEquals(7 to 7, positionsById["c-a"])
            assertEquals(3 to 3, positionsById["c-b"])
            assertEquals(0 to 0, positionsById["c-c"])
        }

        // Piège 3 : la table créée existe, est utilisable, et son AUTOINCREMENT marche.
        db.execSQL("INSERT INTO category_groups (name, position, isFavorite) VALUES ('Proches', 2, 1)")
        db.query("SELECT id, name, position, isFavorite FROM category_groups").use { c ->
            assertTrue("category_groups doit exister et accepter une insertion", c.moveToFirst())
            assertTrue("L'id AUTOINCREMENT doit être attribué (> 0)", c.getLong(0) > 0L)
            assertEquals("Proches", c.getString(1))
            assertEquals(2, c.getInt(2))
            assertEquals(1, c.getInt(3))
        }
    }

    /**
     * Migration v14 → v15 : `category_groups.imagePath` (illustration de couverture)
     * est AJOUTÉE, nullable, sans perte des dossiers existants.
     */
    @Test
    @Throws(IOException::class)
    fun migrate14To15_addsGroupImagePathAsNull() {
        helper.createDatabase(testDb, 14).use { db ->
            db.execSQL(
                "INSERT INTO category_groups (id, name, position, isFavorite) " +
                    "VALUES (1, 'Proches', 4, 1)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 15, true, AppDatabase.MIGRATION_14_15
        )

        db.query(
            "SELECT name, position, isFavorite, imagePath FROM category_groups WHERE id = 1"
        ).use { c ->
            assertTrue("Le dossier doit survivre à la migration", c.moveToFirst())
            // Aucune perte.
            assertEquals("Proches", c.getString(0))
            assertEquals(4, c.getInt(1))
            assertEquals(1, c.getInt(2))
            // Nouvelle colonne nullable, non renseignée.
            assertTrue("imagePath doit être NULL", c.isNull(3))
        }
    }

    /**
     * Migration v15 → v16 : `category_groups.parentGroupId` (sous-dossiers imbriqués)
     * est AJOUTÉE, nullable — les dossiers existants restent donc à la RACINE (NULL),
     * ce qui est le comportement attendu (aucun dossier ne doit se retrouver orphelin
     * ou rattaché arbitrairement).
     */
    @Test
    @Throws(IOException::class)
    fun migrate15To16_addsGroupParentIdAsNullSoGroupsStayAtRoot() {
        helper.createDatabase(testDb, 15).use { db ->
            db.execSQL(
                "INSERT INTO category_groups (id, name, position, isFavorite, imagePath) " +
                    "VALUES (1, 'Proches', 4, 1, '/data/img/proches.jpg')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 16, true, AppDatabase.MIGRATION_15_16
        )

        db.query(
            "SELECT name, position, isFavorite, imagePath, parentGroupId " +
                "FROM category_groups WHERE id = 1"
        ).use { c ->
            assertTrue("Le dossier doit survivre à la migration", c.moveToFirst())
            // Aucune perte.
            assertEquals("Proches", c.getString(0))
            assertEquals(4, c.getInt(1))
            assertEquals(1, c.getInt(2))
            assertEquals("/data/img/proches.jpg", c.getString(3))
            // Le dossier reste à la racine.
            assertTrue("parentGroupId doit être NULL (racine)", c.isNull(4))
        }
    }

    /**
     * Migration v16 → v17 : `persons.proximityNotifiedAt` (anti-spam de proximité,
     * une alerte max par contact par 48 h) est AJOUTÉE, nullable. NULL signifie
     * « jamais notifié » — un backfill à l'horodatage de migration aurait au contraire
     * BÂILLONNÉ toutes les alertes pendant 48 h après la mise à jour.
     */
    @Test
    @Throws(IOException::class)
    fun migrate16To17_addsProximityNotifiedAtAsNullNotBackfilled() {
        val personId = "person-pre-v17"

        helper.createDatabase(testDb, 16).use { db ->
            db.execSQL(
                "INSERT INTO persons " +
                    "(id, firstName, city, cityLat, cityLng, cityNotify, birthdateNotify, " +
                    "isFavorite, createdAt, updatedAt) " +
                    "VALUES (?, 'Fanny', 'Safi', 32.2994, -9.2372, 1, 0, 0, " +
                    "1700000000000, 1700000000000)",
                arrayOf<Any?>(personId)
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 17, true, AppDatabase.MIGRATION_16_17
        )

        db.query(
            "SELECT firstName, city, cityNotify, proximityNotifiedAt " +
                "FROM persons WHERE id = ?",
            arrayOf(personId)
        ).use { c ->
            assertTrue("Le contact doit survivre à la migration", c.moveToFirst())
            // Aucune perte.
            assertEquals("Fanny", c.getString(0))
            assertEquals("Safi", c.getString(1))
            assertEquals(1, c.getInt(2))
            // « Jamais notifié » : la première alerte de proximité reste possible.
            assertTrue("proximityNotifiedAt doit être NULL", c.isNull(3))
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

    /**
     * Migration v18 → v19 (rappels à délai configurable, v7.0) : la colonne
     * persons.birthdateReminderOffsetMinutes est AJOUTÉE sans perte, et les contacts
     * préexistants sont BACKFILLÉS à 0 (« le jour J » — comportement inchangé).
     * `runMigrationsAndValidate(..., true, ...)` valide en prime la conformité
     * STRUCTURELLE du schéma résultant à `19.json` (présence + affinité INTEGER NOT NULL).
     */
    @Test
    @Throws(IOException::class)
    fun migrate18To19_addsReminderOffsetAndBackfillsExistingRows() {
        val personId = "person-pre-v19"

        helper.createDatabase(testDb, 18).use { db ->
            db.execSQL(
                "INSERT INTO persons " +
                    "(id, firstName, birthdate, birthdateNotify, cityNotify, isFavorite, " +
                    "createdAt, updatedAt) " +
                    "VALUES ('$personId', 'Bob', 631152000000, 1, 0, 0, " +
                    "1700000000000, 1700000000000)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 19, true, AppDatabase.MIGRATION_18_19
        )

        db.query(
            "SELECT birthdate, birthdateNotify, birthdateReminderOffsetMinutes " +
                "FROM persons WHERE id = ?",
            arrayOf(personId)
        ).use { c ->
            assertTrue("Le contact doit survivre à la migration", c.moveToFirst())
            // Données préexistantes intactes…
            assertEquals(631152000000L, c.getLong(0))
            assertEquals(1, c.getInt(1))
            // …et le nouveau délai backfillé à 0 (« le jour J »).
            assertEquals(0, c.getInt(2))
        }
    }

    /**
     * Migration v19 → v20 (sections de notes, v7.0.3) : la colonne `noteSections` est
     * AJOUTÉE (TEXT NOT NULL DEFAULT '[]') sans perte, les colonnes héritées `notes` /
     * `likes` sont CONSERVÉES intactes (le backfill en sections est côté code, sans
     * perte). `runMigrationsAndValidate(..., true, ...)` valide en prime la conformité
     * STRUCTURELLE du schéma résultant à `20.json`.
     */
    @Test
    @Throws(IOException::class)
    fun migrate19To20_addsNoteSectionsAndKeepsLegacyNotes() {
        val personId = "person-pre-v20"

        helper.createDatabase(testDb, 19).use { db ->
            db.execSQL(
                "INSERT INTO persons " +
                    "(id, firstName, notes, likes, birthdateNotify, cityNotify, isFavorite, " +
                    "createdAt, updatedAt, birthdateReminderOffsetMinutes) " +
                    "VALUES ('$personId', 'Carol', 'Aime le thé', 'Randonnée', 0, 0, 0, " +
                    "1700000000000, 1700000000000, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 20, true, AppDatabase.MIGRATION_19_20
        )

        db.query(
            "SELECT notes, likes, noteSections FROM persons WHERE id = ?",
            arrayOf(personId)
        ).use { c ->
            assertTrue("Le contact doit survivre à la migration", c.moveToFirst())
            // Notes héritées INTACTES (aucune perte) — le backfill en sections est côté code.
            assertEquals("Aime le thé", c.getString(0))
            assertEquals("Randonnée", c.getString(1))
            // Nouvelle colonne ajoutée avec son défaut '[]'.
            assertEquals("[]", c.getString(2))
        }
    }

    /**
     * Migration v20 → v21 (dates canoniques, v7.1.0) : les valeurs de `dateLines` stockées
     * en chiffres bruts ordonnés par la locale sont converties en ISO `yyyy-MM-dd`.
     *  - l'ANNIVERSAIRE (« 12251995 » à l'anglaise) est réécrit depuis le scalaire canonique
     *    `birthdate` (vérité, locale-libre) → « 1995-12-25 » sans ambiguïté de locale ;
     *  - une autre date NON ambiguë (« 25121990 », jour 25 ⇒ forcément JJ/MM) → « 1990-12-25 ».
     * Aucune perte : les champs non-date (id, label, notify…) sont préservés.
     */
    @Test
    @Throws(IOException::class)
    fun migrate20To21_convertsLocaleDigitsToCanonicalIso() {
        val personId = "person-pre-v21"
        // Scalaire canonique de l'anniversaire (midi local du 25/12/1995) — vérité absolue.
        val birthMillis = Calendar.getInstance()
            .apply { clear(); set(1995, Calendar.DECEMBER, 25, 12, 0, 0) }
            .timeInMillis
        // dateLines HÉRITÉ : anniversaire en ordre en-US + une date « anniversary » en JJ/MM.
        val legacyDateLines =
            """[{"id":"d1","value":"12251995","label":"birthday","notify":false,"reminderOffsetMinutes":0},""" +
                """{"id":"d2","value":"25121990","label":"anniversary","notify":true,"reminderOffsetMinutes":0}]"""

        helper.createDatabase(testDb, 20).use { db ->
            db.execSQL(
                "INSERT INTO persons " +
                    "(id, firstName, birthdate, birthdateNotify, cityNotify, isFavorite, " +
                    "createdAt, updatedAt, birthdateReminderOffsetMinutes, noteSections, dateLines) " +
                    "VALUES (?, ?, ?, 0, 0, 0, 1700000000000, 1700000000000, 0, '[]', ?)",
                arrayOf<Any?>(personId, "Dora", birthMillis, legacyDateLines)
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 21, true, AppDatabase.MIGRATION_20_21
        )

        db.query("SELECT dateLines FROM persons WHERE id = ?", arrayOf(personId)).use { c ->
            assertTrue("Le contact doit survivre à la migration", c.moveToFirst())
            val arr = JSONArray(c.getString(0))
            val byLabel = HashMap<String, String>()
            val notifyByLabel = HashMap<String, Boolean>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                byLabel[o.getString("label")] = o.getString("value")
                notifyByLabel[o.getString("label")] = o.getBoolean("notify")
            }
            // Dates converties en ISO canonique, locale-libre.
            assertEquals("1995-12-25", byLabel["birthday"])
            assertEquals("1990-12-25", byLabel["anniversary"])
            // Champs non-date PRÉSERVÉS (aucune perte).
            assertEquals(true, notifyByLabel["anniversary"])
        }
    }

    /**
     * Migration v21 → v22 (métadonnées de catégorie, v7.1.48) : la colonne
     * categories.updatedAt est AJOUTÉE sans perte et backfillée depuis `createdAt`
     * — et NON à l'horodatage de migration : une catégorie jamais modifiée doit
     * afficher « Dernière modification » == « Créé le », et un backfill à `now`
     * remonterait toutes les catégories en tête du tri « Dernière modification ».
     * `runMigrationsAndValidate(..., true, ...)` valide en prime la conformité
     * STRUCTURELLE du schéma résultant à `22.json` (présence + affinité INTEGER
     * NOT NULL DEFAULT 0).
     */
    @Test
    @Throws(IOException::class)
    fun migrate21To22_addsUpdatedAtBackfilledFromCreatedAt() {
        val categoryId = "cat-pre-v22"
        val createdAt = 1_700_000_000_000L

        helper.createDatabase(testDb, 21).use { db ->
            db.execSQL(
                "INSERT INTO categories " +
                    "(id, name, color, icon, imagePath, `order`, isFavorite, position, " +
                    "parentGroupId, createdAt, deletedAt) " +
                    "VALUES (?, 'Famille', '#2E86C1', 'folder', NULL, 0, 0, 0, NULL, ?, NULL)",
                arrayOf<Any?>(categoryId, createdAt)
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb, 22, true, AppDatabase.MIGRATION_21_22
        )

        db.query(
            "SELECT name, createdAt, updatedAt FROM categories WHERE id = ?",
            arrayOf(categoryId)
        ).use { c ->
            assertTrue("La catégorie doit survivre à la migration", c.moveToFirst())
            // Aucune perte sur les colonnes existantes.
            assertEquals("Famille", c.getString(0))
            assertEquals(createdAt, c.getLong(1))
            // Backfill : updatedAt == createdAt (et surtout PAS l'horodatage de migration).
            assertEquals(createdAt, c.getLong(2))
        }
    }
}
