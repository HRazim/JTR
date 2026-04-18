package com.jtr.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person

/**
 * AppDatabase — PP3 : Base de données Room avec Person + Category.
 *
 * IMPORTANT : version passée à 2 pour inclure la table categories.
 * fallbackToDestructiveMigration() utilisé pour simplifier (acceptable en dev).
 */
@Database(
    entities = [Person::class, Category::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao

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
