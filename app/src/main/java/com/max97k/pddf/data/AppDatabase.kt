package com.max97k.pddf.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.max97k.pddf.BuildConfig
import com.max97k.pddf.util.CryptoManager

@Database(entities = [PasswordEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao

    companion object {
        private const val TAG = "AppDatabase"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure all legacy plaintext passwords are encrypted
                val updates = mutableListOf<Pair<Int, String>>()
                db.query("SELECT id, passwordValue FROM passwords").use { cursor ->
                    val cryptoManager = CryptoManager()
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                        val passwordValue = cursor.getString(cursor.getColumnIndexOrThrow("passwordValue"))
                        
                        // If it's not encrypted (doesn't have the prefix), we encrypt it
                        if (!passwordValue.startsWith("ENC_")) {
                            try {
                                val encryptedValue = cryptoManager.encrypt(passwordValue)
                                updates.add(Pair(id, encryptedValue))
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) {
                                    Log.w(TAG, "Failed to encrypt legacy password id=$id", e)
                                }
                            }
                        }
                    }
                }

                for ((id, encryptedValue) in updates) {
                    db.execSQL(
                        "UPDATE passwords SET passwordValue = ? WHERE id = ?",
                        arrayOf<Any>(encryptedValue, id)
                    )
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE passwords ADD COLUMN useCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE passwords ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Migration 2->3 failed or columns already exist", e)
                    }
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf-decryptor-db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
