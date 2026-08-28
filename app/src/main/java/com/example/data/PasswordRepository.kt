package com.example.data

import com.example.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PasswordRepository(private val passwordDao: PasswordDao) {
    val allPasswords: Flow<Result<List<PasswordEntity>>> = passwordDao.getAllPasswords()
        .map { Result.Success(it) as Result<List<PasswordEntity>> }
        .catch { e -> emit(Result.Error(e)) }

    suspend fun insert(password: PasswordEntity): Result<Unit> {
        return try {
            passwordDao.insertPassword(password)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteById(id: Int): Result<Unit> {
        return try {
            passwordDao.deletePasswordById(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
