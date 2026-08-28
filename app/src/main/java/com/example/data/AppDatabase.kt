package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.util.CryptoManager

@Database(entities = [PasswordEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure all legacy plaintext passwords are encrypted
                val cursor = db.query("SELECT id, passwordValue FROM passwords")
                val cryptoManager = CryptoManager()
                
                val updates = mutableListOf<Pair<Int, String>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                    val passwordValue = cursor.getString(cursor.getColumnIndexOrThrow("passwordValue"))
                    
                    // If it's not encrypted (doesn't have the prefix), we encrypt it
                    if (!passwordValue.startsWith("ENC_")) {
                        try {
                            val encryptedValue = cryptoManager.encrypt(passwordValue)
                            updates.add(Pair(id, encryptedValue))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                cursor.close()

                for ((id, encryptedValue) in updates) {
                    db.execSQL(
                        "UPDATE passwords SET passwordValue = ? WHERE id = ?",
                        arrayOf(encryptedValue, id)
                    )
                }
            }
        }
    }
}
