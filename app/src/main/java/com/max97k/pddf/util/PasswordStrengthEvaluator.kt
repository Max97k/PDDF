package com.max97k.pddf.util

import com.max97k.pddf.R
import kotlin.math.log2
import kotlin.math.pow

object PasswordStrengthEvaluator {

    enum class Strength(val colorResId: Int, val tipResId: Int) {
        NONE(0, R.string.password_strength_tip_none),
        WEAK(android.R.color.holo_red_light, R.string.password_strength_tip_weak),
        MEDIUM(android.R.color.holo_orange_light, R.string.password_strength_tip_medium),
        STRONG(android.R.color.holo_green_light, R.string.password_strength_tip_strong)
    }

    private val COMMON_WEAK_PASSWORDS = setOf(
        "123456", "12345678", "123456789", "1234", "12345",
        "admin", "password", "000000", "0000"
    )

    fun evaluate(password: String): Strength {
        if (password.isEmpty()) return Strength.NONE

        if (COMMON_WEAK_PASSWORDS.contains(password.lowercase())) {
            return Strength.WEAK
        }

        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        val entropy = password.length * log2(poolSize.coerceAtLeast(1).toDouble())

        return when {
            entropy < 40 -> Strength.WEAK
            entropy < 60 -> Strength.MEDIUM
            else -> Strength.STRONG
        }
    }
}
