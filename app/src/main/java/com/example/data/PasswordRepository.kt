package com.example.data

import com.example.util.CryptoManager
import com.example.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PasswordRepository(
    private val passwordDao: PasswordDao,
    private val cryptoManager: CryptoManager = CryptoManager()
) {
    val allPasswords: Flow<Result<List<PasswordEntity>>> = passwordDao.getAllPasswords()
        .map { list ->
            val decryptedList = list.map { entity ->
                entity.copy(passwordValue = cryptoManager.decrypt(entity.passwordValue))
            }
            Result.Success(decryptedList) as Result<List<PasswordEntity>>
        }
        .catch { e -> emit(Result.Error(e)) }

    suspend fun insert(password: PasswordEntity): Result<Unit> {
        return try {
            val encryptedPassword = cryptoManager.encrypt(password.passwordValue)
            passwordDao.insertPassword(password.copy(passwordValue = encryptedPassword))
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
