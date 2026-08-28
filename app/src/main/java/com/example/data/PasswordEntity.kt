package com.example.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val passwordValue: String,
    val timestamp: Long = System.currentTimeMillis()
)
