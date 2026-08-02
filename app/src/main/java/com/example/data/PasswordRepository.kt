package com.example.data

import kotlinx.coroutines.flow.Flow

class PasswordRepository(private val passwordDao: PasswordDao) {
    val allPasswords: Flow<List<PasswordEntity>> = passwordDao.getAllPasswords()

    suspend fun insert(password: PasswordEntity) = passwordDao.insertPassword(password)

    suspend fun deleteById(id: Int) = passwordDao.deletePasswordById(id)
}
