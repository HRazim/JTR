package com.jtr.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.domain.model.SocialLinkEntity

/**
 * AppDatabase — Version 11.
 *
 * v11 : Retrait de l'entité InteractionLog (journal d'interactions abandonné).
 * v10 : Retrait de reminderIntervalMonths / lastReminderSentAt sur Person
 *       (suppression du système de rappels périodiques).
 */
@Database(
    entities = [Person::class, Category::class, PersonCategoryJoin::class, SocialLinkEntity::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    abstract fun personCategoryDao(): PersonCategoryDao
    abstract fun socialLinkDao(): SocialLinkDao

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
