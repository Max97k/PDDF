#!/bin/bash

# Update PasswordRepository.kt
sed -i '/suspend fun recordPasswordHit(id: Int): Result<Unit>/a \
\
    suspend fun exportEncryptedVault(outputStream: java.io.OutputStream): Result<Unit>\
\
    suspend fun importEncryptedVault(inputStream: java.io.InputStream): Result<Int>' app/src/main/java/com/max97k/pddf/domain/repository/PasswordRepository.kt

cat << 'INNER_EOF' > app/src/main/java/com/max97k/pddf/data/PasswordRepository.kt.tmp
<<<<<<< SEARCH
    override suspend fun recordPasswordHit(id: Int): Result<Unit> = withContext(ioDispatcher) {
        try {
            passwordDao.incrementUsage(id, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
=======
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
            val jsonArray = org.json.JSONArray()
            for (entity in list) {
                val jsonObj = org.json.JSONObject()
                jsonObj.put("name", entity.name)
                jsonObj.put("passwordValue", entity.passwordValue)
                jsonArray.put(jsonObj)
            }
            val jsonString = jsonArray.toString()
            val encryptedString = cryptoManager.encrypt(jsonString)
            val bytes = encryptedString.toByteArray(Charsets.UTF_8)
            outputStream.use { it.write(bytes) }
            MemoryUtils.wipe(bytes)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun importEncryptedVault(inputStream: java.io.InputStream): Result<Int> = withContext(ioDispatcher) {
        try {
            val bytes = inputStream.use { it.readBytes() }
            val encryptedString = String(bytes, Charsets.UTF_8)
            MemoryUtils.wipe(bytes)
            val decryptedString = cryptoManager.decrypt(encryptedString)
            val jsonArray = org.json.JSONArray(decryptedString)
            var count = 0
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val name = jsonObj.getString("name")
                val passwordValue = jsonObj.getString("passwordValue")
                passwordDao.insertPassword(PasswordEntity(name = name, passwordValue = passwordValue))
                count++
            }
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
>>>>>>> REPLACE
INNER_EOF
