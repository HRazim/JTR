package com.jtr.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin

/**
 * AppDatabase — Version 6.
 *
 * Ajout de PersonCategoryJoin pour la relation Many-to-Many Person ↔ Category.
 * Suppression de Person.categoryId (FK simple remplacée par la table de jointure).
 * fallbackToDestructiveMigration() conservé pour le développement.
 */
@Database(
    entities = [Person::class, Category::class, PersonCategoryJoin::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    abstract fun personCategoryDao(): PersonCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jtr_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
