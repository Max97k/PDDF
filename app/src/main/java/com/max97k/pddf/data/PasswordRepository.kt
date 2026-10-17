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

    override suspend fun exportEncryptedVault(outputStream: java.io.OutputStream): Result<Unit> = withContext(ioDispatcher) {
        try {
            val list = passwordDao.getAllPasswordsList()
            // To ensure we can wipe plaintext memory effectively and avoid org.json Stub! errors in tests,
            // we'll serialize to a simple CSV-like format using a CharArray manually, without using String or StringBuilder.
            // Format: name|password\n

            // First, calculate total length needed
            var totalLength = 0
            for (entity in list) {
                totalLength += entity.name.length + 1 + entity.passwordValue.length + 1
            }

            val chars = CharArray(totalLength)
            var idx = 0
            for (entity in list) {
                for (c in entity.name) {
                    chars[idx++] = if (c == '|' || c == '\n') '_' else c
                }
                chars[idx++] = '|'
                entity.usePasswordChars { passChars ->
                    for (c in passChars) {
                        chars[idx++] = c
                    }
                }
                chars[idx++] = '\n'
            }

            val encryptedString = cryptoManager.encrypt(chars)
            com.max97k.pddf.util.MemoryUtils.wipe(chars)

            val bytes = encryptedString.toByteArray(kotlin.text.Charsets.UTF_8)
            outputStream.use { it.write(bytes) }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun importEncryptedVault(inputStream: java.io.InputStream): Result<Int> = withContext(ioDispatcher) {
        try {
            val bytes = inputStream.use { it.readBytes() }
            val encryptedString = String(bytes, kotlin.text.Charsets.UTF_8)

            val chars = cryptoManager.decryptToChars(encryptedString)
            var count = 0

            var i = 0
            while (i < chars.size) {
                // Find length of name and password
                var nameLen = 0
                var tempI = i
                while (tempI < chars.size && chars[tempI] != '|') {
                    nameLen++
                    tempI++
                }
                tempI++ // skip '|'
                var passLen = 0
                while (tempI < chars.size && chars[tempI] != '\n') {
                    passLen++
                    tempI++
                }

                if (nameLen > 0 || passLen > 0) {
                    val nameChars = CharArray(nameLen)
                    for (j in 0 until nameLen) {
                        nameChars[j] = chars[i++]
                    }
                    val nameStr = String(nameChars)
                    com.max97k.pddf.util.MemoryUtils.wipe(nameChars)

                    i++ // skip '|'

                    val passChars = CharArray(passLen)
                    for (j in 0 until passLen) {
                        passChars[j] = chars[i++]
                    }

                    i++ // skip '\n'

                    passwordDao.insertPassword(PasswordEntity(name = nameStr, passwordValue = String(passChars)))
                    com.max97k.pddf.util.MemoryUtils.wipe(passChars)
                    count++
                } else {
                    i++
                }
            }

            com.max97k.pddf.util.MemoryUtils.wipe(chars)
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
