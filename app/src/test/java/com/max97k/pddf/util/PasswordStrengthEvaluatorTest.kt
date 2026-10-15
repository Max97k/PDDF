package com.max97k.pddf.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordStrengthEvaluatorTest {

    @Test
    fun `test empty password`() {
        assertEquals(PasswordStrengthEvaluator.Strength.NONE, PasswordStrengthEvaluator.evaluate(""))
    }

    @Test
    fun `test common weak passwords`() {
        assertEquals(PasswordStrengthEvaluator.Strength.WEAK, PasswordStrengthEvaluator.evaluate("123456"))
        assertEquals(PasswordStrengthEvaluator.Strength.WEAK, PasswordStrengthEvaluator.evaluate("password"))
        assertEquals(PasswordStrengthEvaluator.Strength.WEAK, PasswordStrengthEvaluator.evaluate("ADMIN"))
    }

    @Test
    fun `test low entropy password`() {
        assertEquals(PasswordStrengthEvaluator.Strength.WEAK, PasswordStrengthEvaluator.evaluate("abcde"))
    }

    @Test
    fun `test medium entropy password`() {
        assertEquals(PasswordStrengthEvaluator.Strength.MEDIUM, PasswordStrengthEvaluator.evaluate("password123"))
    }

    @Test
    fun `test high entropy password`() {
        assertEquals(PasswordStrengthEvaluator.Strength.STRONG, PasswordStrengthEvaluator.evaluate("Str0ng!P@ssw0rd"))
    }
}
