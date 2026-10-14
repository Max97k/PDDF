package com.max97k.pddf.domain.repository

import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.util.Result
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {
    val allPasswords: Flow<Result<List<PasswordEntity>>>

    suspend fun getAllDecryptedPasswords(): List<PasswordEntity>

    suspend fun insert(password: PasswordEntity): Result<Unit>

    suspend fun insert(name: String, passwordChars: CharArray): Result<Unit>

    suspend fun deleteById(id: Int): Result<Unit>

    suspend fun recordPasswordHit(id: Int): Result<Unit>
}
