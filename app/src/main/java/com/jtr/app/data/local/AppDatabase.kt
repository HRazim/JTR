package com.jtr.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.domain.model.GalleryImage
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.DateCanonical
import org.json.JSONArray
import java.util.Locale

/**
 * AppDatabase — Version 25.
 *
 * v25 : Date de modification d'image (v7.1.102). UNE colonne NULLABLE sur `gallery_images` —
 *       `modifiedAt` INTEGER (epoch millis de la dernière édition des métadonnées). Migration
 *       PUREMENT ADDITIVE : un seul `ALTER TABLE … ADD COLUMN`, aucune table recréée, aucune
 *       ligne lue ni réécrite → ZÉRO perte possible ([MIGRATION_24_25]). PAS de backfill :
 *       `null` = « jamais édité », et le tri « modifié » à venir lira
 *       `COALESCE(modifiedAt, createdAt)`. Posée AVANT toute UI (v7.1.103 branchera
 *       l'écriture à l'édition et le tri), même discipline que v23/v24.
 * v24 : Localisation d'image (v7.1.93). Trois colonnes NULLABLES sur `gallery_images` —
 *       `latitude` REAL, `longitude` REAL, `placeLabel` TEXT. Migration PUREMENT ADDITIVE :
 *       trois `ALTER TABLE … ADD COLUMN`, qui ne touchent que l'en-tête de schéma — aucune
 *       table recréée, aucune ligne lue ni réécrite → ZÉRO perte possible ([MIGRATION_23_24]).
 *       Posées AVANT toute UI, sur le modèle de `Person` (2 coordonnées + 1 libellé composé),
 *       pour que le panneau de détails d'image n'exige JAMAIS d'autre migration.
 * v23 : Galerie d'images par contact (v7.1.88). Nouvelle table `gallery_images`
 *       (id AUTOINCREMENT, personId TEXT FK → persons(id) ON DELETE CASCADE + index,
 *       path TEXT, position/createdAt INTEGER NOT NULL DEFAULT 0, caption TEXT).
 *       Migration PUREMENT ADDITIVE : un seul CREATE TABLE + un CREATE INDEX, aucune
 *       table existante touchée, aucune ligne lue ni écrite → ZÉRO perte possible
 *       ([MIGRATION_22_23]). `position` et `caption` sont provisionnés dès maintenant
 *       bien qu'aucune UI ne les exploite, pour que le réordonnancement et les légendes
 *       n'exigent JAMAIS d'autre migration.
 * v22 : Métadonnées de catégorie (v7.1.48). Ajout de Category.updatedAt (INTEGER NOT NULL
 *       DEFAULT 0) — dernière modification du CONTENU (nom / couleur / image), en parité
 *       avec Person.updatedAt (v14). Les lignes préexistantes sont backfillées depuis
 *       `createdAt` (déjà présent depuis v18) et NON à l'horodatage de migration : une
 *       catégorie jamais modifiée a bien updatedAt == createdAt, et le tri « Dernière
 *       modification » n'est pas bouleversé par la mise à jour. ZÉRO perte de données
 *       ([MIGRATION_21_22]).
 * v21 : Dates en forme CANONIQUE (v7.1.0). AUCUN changement de schéma — migration de
 *       DONNÉES uniquement : les dates de `dateLines` (JSON) étaient stockées en chiffres
 *       bruts ordonnés selon la locale de saisie (changer de langue cassait l'affichage et
 *       BLOQUAIT la sauvegarde). [MIGRATION_20_21] convertit ces valeurs vers l'ISO
 *       `yyyy-MM-dd` (locale-libre). Anti-ambiguïté : l'anniversaire est dérivé du scalaire
 *       canonique `Person.birthdate` (vérité), les autres dates via désambiguïsation par
 *       validité ([DateCanonical.legacyRawDigitsToIso]) ; une valeur incertaine est
 *       PRÉSERVÉE telle quelle (jamais de fausse date). Schéma identique à v20 → la
 *       migration destructive (filet) n'est jamais atteinte.
 * v20 : Sections de notes personnalisables (v7.0.3). Ajout de Person.noteSections
 *       (TEXT NOT NULL DEFAULT '[]', liste JSON [NoteSection]). Les colonnes héritées
 *       `notes` / `likes` sont CONSERVÉES (jamais supprimées) ; leur contenu est
 *       backfillé SANS PERTE en sections côté code (conversion paresseuse au
 *       chargement/sauvegarde, [deriveNoteSections]) → ZÉRO perte ([MIGRATION_19_20]).
 * v19 : Rappels à délai configurable (v7.0). Ajout de Person.birthdateReminderOffset
 *       Minutes (INTEGER NOT NULL DEFAULT 0) — projection scalaire du délai de rappel
 *       de l'anniversaire (0 = « le jour J »). Le délai des AUTRES dates importantes
 *       vit dans le JSON dateLines (DynamicLine.reminderOffsetMinutes) → aucune colonne.
 *       ZÉRO perte de données ([MIGRATION_18_19]).
 * v18 : Tri des catégories en parité avec les contacts (v6.1.7). Ajout de
 *       Category.createdAt, CategoryGroup.createdAt et PersonCategoryJoin.addedAt
 *       (tous INTEGER NOT NULL DEFAULT 0, backfillés à l'horodatage de migration) —
 *       ZÉRO perte de données ([MIGRATION_17_18]).
 * v17 : Person.proximityNotifiedAt — anti-spam du Moteur de Proximité v5.4
 *       (une alerte max par contact par 48 h) ([MIGRATION_16_17]).
 * v16 : CategoryGroup.parentGroupId (sous-groupes imbriqués) ([MIGRATION_15_16]).
 * v15 : CategoryGroup.imagePath (illustration de couverture) ([MIGRATION_14_15]).
 * v14 : Favoris + tri & regroupement (v4.5). Person.updatedAt ; Category.isFavorite
 *       /position/parentGroupId ; nouvelle table CategoryGroup ([MIGRATION_13_14]).
 * v13 : Informations professionnelles (v4.5). Ajout de 3 colonnes nullables sur
 *       Person : jobTitle, department, company ([MIGRATION_12_13]).
 * v12 : Refonte « Contacts Google » (v4.5). Ajout de 5 colonnes de nom (prefix,
 *       middleName, suffix, phonetic, nickname) et de 4 colonnes JSON pour les
 *       listes répétables (phoneLines, emailLines, dateLines, relationLines),
 *       sérialisées via [Converters]. Migration sans perte ([MIGRATION_11_12]).
 * v11 : Retrait de l'entité InteractionLog (journal d'interactions abandonné).
 * v10 : Retrait de reminderIntervalMonths / lastReminderSentAt sur Person.
 */
@Database(
    entities = [Person::class, Category::class, CategoryGroup::class,
        PersonCategoryJoin::class, SocialLinkEntity::class, GalleryImage::class],
    version = 25,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun galleryImageDao(): GalleryImageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryGroupDao(): CategoryGroupDao
    abstract fun personCategoryDao(): PersonCategoryDao
    abstract fun socialLinkDao(): SocialLinkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration v11 → v12, ZÉRO perte de données.
         *
         * 1. ADD COLUMN (nullable) pour les 5 sous-champs de nom et les 4 colonnes JSON.
         * 2. Repli inverse : convertit les scalaires existants `phoneNumber` / `email`
         *    en JSON `[{"value":…,"label":…}]` (concaténation de chaînes — pas de
         *    dépendance à l'extension JSON1, absente avant l'API 30).
         *
         * Les dates ne sont pas converties en SQL (l'ordre des chiffres dépend de la
         * locale, non exprimable en SQLite portable) : la colonne `birthdate` est
         * conservée et la ligne de date est reconstruite, locale-aware, au chargement
         * du profil (EditPersonViewModel.populateFields).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN prefix TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN middleName TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN suffix TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN phonetic TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN nickname TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN phoneLines TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN emailLines TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN dateLines TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN relationLines TEXT")

                db.execSQL(
                    "UPDATE persons SET phoneLines = " +
                        "'[{\"value\":\"' || phoneNumber || '\",\"label\":\"mobile\"}]' " +
                        "WHERE phoneNumber IS NOT NULL AND trim(phoneNumber) <> ''"
                )
                db.execSQL(
                    "UPDATE persons SET emailLines = " +
                        "'[{\"value\":\"' || email || '\",\"label\":\"home\"}]' " +
                        "WHERE email IS NOT NULL AND trim(email) <> ''"
                )
            }
        }

        /**
         * Migration v12 → v13, ZÉRO perte de données : ajoute les 3 colonnes
         * professionnelles nullables (jobTitle, department, company).
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN jobTitle TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN department TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN company TEXT")
            }
        }

        /**
         * Migration v13 → v14, ZÉRO perte de données.
         *
         * - persons.updatedAt (NOT NULL DEFAULT 0) initialisé à createdAt.
         * - categories : isFavorite, position (NOT NULL DEFAULT 0) — position copiée
         *   depuis l'ancien `order` — et parentGroupId (nullable).
         * - nouvelle table category_groups (dossiers de catégories).
         *
         * Les colonnes NOT NULL utilisent un DEFAULT SQL aligné sur @ColumnInfo(
         * defaultValue="0") des entités, pour passer la validation de schéma Room.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE persons SET updatedAt = createdAt")

                db.execSQL("ALTER TABLE categories ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN parentGroupId INTEGER")
                db.execSQL("UPDATE categories SET position = `order`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_groups (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "position INTEGER NOT NULL DEFAULT 0, " +
                        "isFavorite INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        /** Migration v14 → v15 : illustration de couverture optionnelle des groupes. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE category_groups ADD COLUMN imagePath TEXT")
            }
        }

        /** Migration v15 → v16 : groupe parent (sous-groupes imbriqués), nullable. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE category_groups ADD COLUMN parentGroupId INTEGER")
            }
        }

        /** Migration v16 → v17 : anti-spam proximité (horodatage nullable). */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN proximityNotifiedAt INTEGER")
            }
        }

        /**
         * Migration v17 → v18, ZÉRO perte de données — tri des catégories en parité
         * avec les contacts (v6.1.7).
         *
         * 1. ADD COLUMN (NOT NULL DEFAULT 0, aligné sur @ColumnInfo(defaultValue="0")) :
         *    - categories.createdAt        (tri « Création »),
         *    - category_groups.createdAt   (tri « Création » des dossiers),
         *    - person_category_join.addedAt (événement « membre ajouté »).
         * 2. Backfill : les lignes PRÉEXISTANTES reçoivent l'horodatage de migration
         *    (createdAt et addedAt). Les catégories d'avant v18 partagent donc la même
         *    date de création — comportement accepté et documenté.
         *
         * Uniquement des ALTER TABLE ADD COLUMN : aucune table recréée, aucune ligne
         * supprimée → la migration destructive (filet de sécurité) n'est jamais atteinte.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL("ALTER TABLE categories ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE category_groups ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE person_category_join ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE categories SET createdAt = $now")
                db.execSQL("UPDATE category_groups SET createdAt = $now")
                db.execSQL("UPDATE person_category_join SET addedAt = $now")
            }
        }

        /**
         * Migration v18 → v19, ZÉRO perte de données — rappels à délai configurable (v7.0).
         *
         * 1. ADD COLUMN persons.birthdateReminderOffsetMinutes (NOT NULL DEFAULT 0, aligné
         *    sur @ColumnInfo(defaultValue="0")) : délai de rappel de l'anniversaire en
         *    minutes avant minuit (0 = « le jour J »).
         * 2. Backfill EXPLICITE des lignes préexistantes à 0 (comportement « le jour J »
         *    inchangé pour les contacts d'avant v19 — aucune régression).
         *
         * Le délai des autres dates importantes est porté par le JSON dateLines
         * (DynamicLine.reminderOffsetMinutes, désérialisé à 0 si absent) : aucune colonne,
         * donc aucune autre instruction SQL. Uniquement un ALTER TABLE ADD COLUMN : aucune
         * table recréée, aucune ligne supprimée → la migration destructive (filet de
         * sécurité) n'est jamais atteinte.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE persons ADD COLUMN birthdateReminderOffsetMinutes " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE persons SET birthdateReminderOffsetMinutes = 0")
            }
        }

        /**
         * Migration v19 → v20, ZÉRO perte de données — sections de notes (v7.0.3).
         *
         * Une SEULE instruction : ajoute la colonne JSON `noteSections` (NOT NULL DEFAULT
         * '[]', aligné sur @ColumnInfo(defaultValue="[]")). Les lignes existantes prennent
         * automatiquement la valeur par défaut '[]'.
         *
         * Le BACKFILL des notes héritées (`notes` / `likes`) en sections se fait CÔTÉ CODE
         * (conversion paresseuse au chargement/sauvegarde, [deriveNoteSections]) — on évite
         * la construction de JSON en SQL brut (fragile sur l'échappement des guillemets) et
         * on garantit des titres par défaut LOCALISÉS. Les colonnes `notes` / `likes` sont
         * CONSERVÉES intactes : aucune perte possible.
         *
         * Uniquement un ALTER TABLE ADD COLUMN : aucune table recréée, aucune ligne
         * supprimée → la migration destructive (filet de sécurité) n'est jamais atteinte.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN noteSections TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * Migration v20 → v21, ZÉRO perte de données — dates en forme canonique (v7.1.0).
         *
         * Migration de DONNÉES uniquement (aucune colonne ajoutée/supprimée) : réécrit les
         * valeurs de date du JSON `dateLines` (chiffres bruts ordonnés par la locale de
         * saisie) en ISO `yyyy-MM-dd` locale-libre, en Kotlin (parsing JSON via [JSONArray],
         * découplé du modèle Room qui évolue).
         *
         * Stratégie anti-ambiguïté (dates `12/25/1995` vs `25/12/1995`), DÉTERMINISTE :
         *  - ANNIVERSAIRE (label « birthday ») → dérivé du scalaire canonique
         *    `Person.birthdate` (vérité absolue, calculé à la dernière sauvegarde, déjà
         *    locale-libre) → jamais ambigu ;
         *  - autres dates → [DateCanonical.legacyRawDigitsToIso] : une seule interprétation
         *    valide est retenue, une vraie ambiguïté est tranchée par l'ordre de la locale
         *    courante (meilleur proxy de la locale d'écriture) ;
         *  - valeur déjà ISO → ignorée (idempotent) ; valeur INCERTAINE (aucune date valide)
         *    → PRÉSERVÉE telle quelle (jamais de fausse date écrite).
         *
         * Les UPDATE sont appliqués APRÈS fermeture du curseur de lecture. Aucune table
         * recréée → la migration destructive (filet de sécurité) n'est jamais atteinte.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tieBreak = DateCanonical.currentOrder(Locale.getDefault())
                val updates = ArrayList<Pair<String, String>>() // id → nouveau JSON dateLines

                db.query(
                    "SELECT id, dateLines, birthdate FROM persons " +
                        "WHERE dateLines IS NOT NULL AND trim(dateLines) <> ''"
                ).use { c ->
                    val idIdx = c.getColumnIndexOrThrow("id")
                    val dlIdx = c.getColumnIndexOrThrow("dateLines")
                    val bdIdx = c.getColumnIndexOrThrow("birthdate")
                    while (c.moveToNext()) {
                        val id = c.getString(idIdx) ?: continue
                        val json = c.getString(dlIdx) ?: continue
                        val birthdate = if (c.isNull(bdIdx)) null else c.getLong(bdIdx)
                        val arr = try { JSONArray(json) } catch (_: Exception) { continue }

                        var changed = false
                        var birthdayUsed = false
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val value = obj.optString("value", "")
                            if (value.isBlank() || DateCanonical.isIso(value)) continue
                            // « birthday » : const FieldTypes.DATE_BIRTHDAY (literal pour ne pas
                            // coupler la migration à la couche UI, plus stable dans le temps).
                            val iso = if (obj.optString("label", "") == "birthday" &&
                                !birthdayUsed && birthdate != null
                            ) {
                                birthdayUsed = true
                                DateCanonical.millisToIso(birthdate)
                            } else {
                                DateCanonical.legacyRawDigitsToIso(value, tieBreak)
                            }
                            if (iso != null && iso != value) {
                                obj.put("value", iso)
                                changed = true
                            }
                        }
                        if (changed) updates += id to arr.toString()
                    }
                }

                updates.forEach { (id, newJson) ->
                    db.execSQL("UPDATE persons SET dateLines = ? WHERE id = ?", arrayOf(newJson, id))
                }
            }
        }

        /**
         * Migration v21 → v22, ZÉRO perte de données — métadonnées de catégorie (v7.1.48).
         *
         * 1. ADD COLUMN categories.updatedAt (NOT NULL DEFAULT 0, aligné sur @ColumnInfo(
         *    defaultValue="0") de l'entité) : dernière modification du CONTENU de la catégorie.
         * 2. Backfill depuis `createdAt` (colonne déjà présente depuis v18) — et NON à
         *    l'horodatage de migration : une catégorie jamais modifiée doit afficher
         *    updatedAt == createdAt, et un backfill à `now` remonterait TOUTES les catégories
         *    en tête du tri « Dernière modification ». Même stratégie que [MIGRATION_13_14]
         *    pour persons.updatedAt.
         *
         * Uniquement un ALTER TABLE ADD COLUMN : aucune table recréée, aucune ligne supprimée
         * → la migration destructive (filet de sécurité) n'est jamais atteinte.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE categories SET updatedAt = createdAt")
            }
        }

        /**
         * Migration v22 → v23, ZÉRO perte de données — galerie d'images par contact (v7.1.88).
         *
         * PUREMENT ADDITIVE : un `CREATE TABLE` et un `CREATE INDEX`, rien d'autre. Aucune
         * table existante n'est recréée, altérée ni lue ; aucune ligne n'est écrite. Il n'y a
         * donc littéralement aucun chemin par lequel une donnée préexistante pourrait être
         * perdue — contrairement aux migrations qui recréent une table pour changer une
         * contrainte.
         *
         * ⚠️ Le SQL ci-dessous doit correspondre AU CARACTÈRE PRÈS à ce que Room génère pour
         * [GalleryImage] (cf. `schemas/.../23.json`, champ `createSql`) : Room revalide le
         * schéma obtenu au premier lancement et lève « Migration didn't properly handle »
         * à la moindre divergence (type, NOT NULL, DEFAULT, ordre des colonnes, nom d'index).
         * `personId` est un **TEXT** : `Person.id` est un UUID String, et la table parente
         * s'appelle `persons`.
         */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gallery_images` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`personId` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL DEFAULT 0, " +
                        "`caption` TEXT, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gallery_images_personId` " +
                        "ON `gallery_images` (`personId`)"
                )
            }
        }

        /**
         * Migration v23 → v24, ZÉRO perte de données — localisation d'image (v7.1.93).
         *
         * PUREMENT ADDITIVE, et de la forme la plus sûre qui soit : trois `ALTER TABLE …
         * ADD COLUMN`, qui en SQLite n'écrivent QUE l'en-tête de schéma — aucune table n'est
         * recréée, aucune ligne n'est lue ni réécrite. Les lignes de galerie existantes
         * reçoivent simplement `NULL` dans les trois nouvelles colonnes.
         *
         * ⚠️ Les trois colonnes sont NULLABLES et SANS DEFAUT : c'est exactement ce que Room
         * attend d'un `Double?`/`String?` sans `@ColumnInfo(defaultValue = …)`. Ajouter un
         * `DEFAULT` ou un `NOT NULL` ici ferait diverger le schéma obtenu de `24.json` et
         * lèverait « Migration didn't properly handle » au premier lancement (c'est le piège
         * qui avait été prouvé par contrôle négatif en v7.1.88).
         *
         * SQLite n'accepte qu'UNE colonne par `ALTER TABLE` → trois instructions distinctes.
         */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `gallery_images` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `gallery_images` ADD COLUMN `longitude` REAL")
                db.execSQL("ALTER TABLE `gallery_images` ADD COLUMN `placeLabel` TEXT")
            }
        }

        /**
         * Migration v24 → v25, ZÉRO perte de données — date de modification d'image (v7.1.102).
         *
         * PUREMENT ADDITIVE, même forme que [MIGRATION_23_24] : un seul `ALTER TABLE …
         * ADD COLUMN`, qui en SQLite n'écrit QUE l'en-tête de schéma — aucune table recréée,
         * aucune ligne lue ni réécrite. Les lignes existantes reçoivent `NULL` (« jamais
         * édité ») : PAS de backfill, le tri « modifié » à venir lira
         * `COALESCE(modifiedAt, createdAt)`.
         *
         * ⚠️ NULLABLE et SANS DEFAUT : exactement ce que Room attend d'un `Long?` sans
         * `@ColumnInfo(defaultValue = …)` — un `NOT NULL` ou un `DEFAULT` ferait diverger le
         * schéma de `25.json` et lèverait « Migration didn't properly handle » au premier
         * lancement (piège prouvé par contrôle négatif en v7.1.88 et re-prouvé ici).
         */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `gallery_images` ADD COLUMN `modifiedAt` INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jtr_database"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                        MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                        MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                        MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25)
                    // v7.1.63 — AUCUN FALLBACK DESTRUCTIF : la base n'est PLUS JAMAIS
                    // effacée silencieusement, quel que soit le scénario.
                    //
                    // Historique. `fallbackToDestructiveMigration()` (sans suffixe) laissait
                    // Room DÉTRUIRE ET RECRÉER la base dès qu'un chemin de version n'était pas
                    // couvert — y compris à la MONTÉE et sur une simple divergence de schéma.
                    // Pour une app 100 % locale dont l'Auto Backup OS est volontairement
                    // désactivé (v7.1.7), c'était le SEUL chemin capable d'effacer
                    // définitivement les données, sans filet. v7.1.57 (C3) l'a restreint au
                    // DOWNGRADE ; v7.1.63 le retire COMPLÈTEMENT, une fois les 11 migrations
                    // 11→22 toutes couvertes par `AppDatabaseMigrationTest` (H4).
                    //
                    // Conséquence assumée : tout chemin absent ou incohérent — divergence de
                    // schéma, downgrade (rollback Play / APK antérieur), base antérieure à v11 —
                    // lève désormais une IllegalStateException au lieu d'effacer. C'est
                    // L'OBJECTIF : sur disque, la donnée reste INTACTE, donc récupérable en
                    // réinstallant la version courante. Un crash est diagnosticable et
                    // réversible ; une perte de données ne l'est pas.
                    //
                    // ⚠️ Corollaire pour les évolutions futures : bumper `version` SANS fournir
                    // la Migration correspondante ne « repart plus à zéro », cela rend l'app
                    // INDÉMARRABLE. Le garde-fou historique (« ne bumper QUE avec une Migration
                    // explicite ») n'est donc plus une convention mais une contrainte dure.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
