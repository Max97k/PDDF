package com.max97k.pddf.domain.usecase

import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.domain.repository.PasswordRepository
import com.max97k.pddf.util.Result
import kotlinx.coroutines.flow.Flow

class PasswordVaultUseCase(
    private val repository: PasswordRepository
) {
    val allPasswords: Flow<Result<List<PasswordEntity>>> = repository.allPasswords

    suspend fun insertPassword(name: String, passwordValue: String) {
        if (passwordValue.isNotBlank()) {
            repository.insert(PasswordEntity(name = name.ifBlank { "PDF Password" }, passwordValue = passwordValue))
        }
    }

    suspend fun insertPassword(name: String, passwordChars: CharArray) {
        if (passwordChars.isNotEmpty()) {
            repository.insert(name = name.ifBlank { "PDF Password" }, passwordChars = passwordChars)
        }
    }

    suspend fun deletePassword(id: Int) {
        repository.deleteById(id)
    }

    suspend fun recordPasswordHit(id: Int) {
        repository.recordPasswordHit(id)
    }

    suspend fun exportEncryptedVault(outputStream: java.io.OutputStream): Result<Unit> {
        return repository.exportEncryptedVault(outputStream)
    }

    suspend fun importEncryptedVault(inputStream: java.io.InputStream): Result<Int> {
        return repository.importEncryptedVault(inputStream)
    }

    suspend fun getAllDecryptedPasswords(): List<PasswordEntity> {
        return repository.getAllDecryptedPasswords()
    }
}
