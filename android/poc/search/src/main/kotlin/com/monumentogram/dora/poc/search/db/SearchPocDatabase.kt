package com.monumentogram.dora.poc.search.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities =
        [ConversationEntity::class, TranscriptSegmentEntity::class, TranscriptSegmentFts::class],
    version = 1,
    exportSchema = true,
)
abstract class SearchPocDatabase : RoomDatabase() {
    abstract fun searchDao(): SearchPocDao

    companion object {
        fun open(context: Context, databaseName: String): SearchPocDatabase =
            Room.databaseBuilder(context, SearchPocDatabase::class.java, databaseName)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .allowMainThreadQueries()
                .build()
    }
}
