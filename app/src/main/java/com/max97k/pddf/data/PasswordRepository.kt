package com.max97k.pddf.data

import com.max97k.pddf.util.CryptoManager
import com.max97k.pddf.util.MemoryUtils
import com.max97k.pddf.util.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

open class PasswordRepository(
    private val passwordDao: PasswordDao,
    private val cryptoManager: CryptoManager = CryptoManager(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : com.max97k.pddf.domain.repository.PasswordRepository {
    override val allPasswords: Flow<Result<List<PasswordEntity>>> = passwordDao.getAllPasswords()
        .map { list ->
            val decryptedList = list.map { entity ->
                val chars = cryptoManager.decryptToChars(entity.passwordValue)
                val decrypted = String(chars)
                MemoryUtils.wipe(chars)
                entity.copy(passwordValue = decrypted)
            }
            Result.Success(decryptedList) as Result<List<PasswordEntity>>
        }
        .catch { e -> emit(Result.Error(e)) }
        .flowOn(ioDispatcher)

    override suspend fun getAllDecryptedPasswords(): List<PasswordEntity> = withContext(ioDispatcher) {
        try {
            val list = passwordDao.getAllPasswordsList()
            list.map { entity ->
                val chars = cryptoManager.decryptToChars(entity.passwordValue)
                val decrypted = String(chars)
                MemoryUtils.wipe(chars)
                entity.copy(passwordValue = decrypted)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun insert(password: PasswordEntity): Result<Unit> = withContext(ioDispatcher) {
        try {
            val encryptedPassword = password.usePasswordChars { chars ->
                cryptoManager.encrypt(chars)
            }
            passwordDao.insertPassword(password.copy(passwordValue = encryptedPassword))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun insert(name: String, passwordChars: CharArray): Result<Unit> = withContext(ioDispatcher) {
        try {
            val encryptedPassword = cryptoManager.encrypt(passwordChars)
            passwordDao.insertPassword(
                PasswordEntity(
                    name = name.ifBlank { "PDF Password" },
                    passwordValue = encryptedPassword
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteById(id: Int): Result<Unit> = withContext(ioDispatcher) {
        try {
            passwordDao.deletePasswordById(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun recordPasswordHit(id: Int): Result<Unit> = withContext(ioDispatcher) {
        try {
            passwordDao.incrementUsage(id, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
