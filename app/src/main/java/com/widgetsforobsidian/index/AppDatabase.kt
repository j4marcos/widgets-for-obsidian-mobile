package com.widgetsforobsidian.index

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "vault-index.db")
                // The index is a cache of the vault: rebuilding it is always safe.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
