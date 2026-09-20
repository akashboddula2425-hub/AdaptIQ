package com.adaptiq.tutor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        KnowledgeGapEntity::class,
        KnowledgeGapFts::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AdaptIQDatabase : RoomDatabase() {

    abstract fun knowledgeGapDao(): KnowledgeGapDao

    companion object {
        @Volatile
        private var INSTANCE: AdaptIQDatabase? = null

        fun getInstance(context: Context): AdaptIQDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdaptIQDatabase::class.java,
                    "adaptiq_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
