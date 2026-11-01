package com.hudhud.app.domain.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateValidatorTest {

    @Test
    fun `a named template with a body is valid`() {
        val result = TemplateValidator.validate("تذكير", "مرحباً {{الاسم}}")

        assertTrue(result.isValid)
        assertEquals(listOf("الاسم"), result.placeholders)
    }

    @Test
    fun `a fixed body without placeholders is valid`() {
        val result = TemplateValidator.validate("تذكير", "موعد الاجتماع غداً")

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.placeholders)
    }

    @Test
    fun `blank name is rejected`() {
        val result = TemplateValidator.validate("   ", "مرحباً")

        assertFalse(result.isValid)
        assertTrue(result.has(TemplateValidationError.NAME_REQUIRED))
    }

    @Test
    fun `blank body is rejected`() {
        val result = TemplateValidator.validate("تذكير", "  ")

        assertFalse(result.isValid)
        assertTrue(result.has(TemplateValidationError.BODY_REQUIRED))
    }

    @Test
    fun `both blank fields report both errors`() {
        val result = TemplateValidator.validate("", "")

        assertEquals(
            setOf(TemplateValidationError.NAME_REQUIRED, TemplateValidationError.BODY_REQUIRED),
            result.errors,
        )
    }

    @Test
    fun `placeholders come from the body on every keystroke`() {
        val result = TemplateValidator.validate("عرض", "{{الاسم}} {{المدينة}} {{الاسم}}")

        assertEquals(listOf("الاسم", "المدينة"), result.placeholders)
    }
}
