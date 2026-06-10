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
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.domain.model.SocialLinkEntity

/**
 * AppDatabase — Version 16.
 *
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
        PersonCategoryJoin::class, SocialLinkEntity::class],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jtr_database"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                        MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
