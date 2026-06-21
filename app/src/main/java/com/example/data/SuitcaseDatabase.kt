package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Suitcase::class, SuitcaseItem::class], version = 1, exportSchema = false)
abstract class SuitcaseDatabase : RoomDatabase() {
    abstract fun suitcaseDao(): SuitcaseDao

    companion object {
        @Volatile
        private var INSTANCE: SuitcaseDatabase? = null

        fun getDatabase(context: Context): SuitcaseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SuitcaseDatabase::class.java,
                    "suitcase_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
