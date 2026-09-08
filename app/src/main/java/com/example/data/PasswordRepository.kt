package com.example.data

import com.example.util.CryptoManager
import com.example.util.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PasswordRepository(
    private val passwordDao: PasswordDao,
    private val cryptoManager: CryptoManager = CryptoManager(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val allPasswords: Flow<Result<List<PasswordEntity>>> = passwordDao.getAllPasswords()
        .map { list ->
            val decryptedList = list.map { entity ->
                entity.copy(passwordValue = cryptoManager.decrypt(entity.passwordValue))
            }
            Result.Success(decryptedList) as Result<List<PasswordEntity>>
        }
        .catch { e -> emit(Result.Error(e)) }
        .flowOn(ioDispatcher)

    suspend fun getAllDecryptedPasswords(): List<PasswordEntity> = withContext(ioDispatcher) {
        try {
            val list = passwordDao.getAllPasswordsList()
            list.map { entity ->
                entity.copy(passwordValue = cryptoManager.decrypt(entity.passwordValue))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun insert(password: PasswordEntity): Result<Unit> = withContext(ioDispatcher) {
        try {
            val encryptedPassword = cryptoManager.encrypt(password.passwordValue)
            passwordDao.insertPassword(password.copy(passwordValue = encryptedPassword))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteById(id: Int): Result<Unit> = withContext(ioDispatcher) {
        try {
            passwordDao.deletePasswordById(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
