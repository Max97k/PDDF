package com.max97k.pddf.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.max97k.pddf.util.MemoryUtils

@Immutable
@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val passwordValue: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Secondary constructor accepting CharArray to prevent unnecessary String allocations.
     */
    constructor(
        id: Int = 0,
        name: String,
        passwordChars: CharArray,
        timestamp: Long = System.currentTimeMillis()
    ) : this(id, name, String(passwordChars), timestamp)

    /**
     * Extracts password as a wipeable CharArray.
     */
    fun getPasswordChars(): CharArray = passwordValue.toCharArray()

    /**
     * Executes the lambda block with a transient CharArray and wipes it immediately afterward.
     */
    inline fun <R> usePasswordChars(block: (CharArray) -> R): R {
        val chars = getPasswordChars()
        return try {
            block(chars)
        } finally {
            MemoryUtils.wipe(chars)
        }
    }
}
