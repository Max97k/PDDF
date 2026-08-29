package com.example.domain.usecase

import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import com.example.util.Result
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

    suspend fun deletePassword(id: Int) {
        repository.deleteById(id)
    }

    suspend fun getAllDecryptedPasswords(): List<PasswordEntity> {
        return repository.getAllDecryptedPasswords()
    }
}
