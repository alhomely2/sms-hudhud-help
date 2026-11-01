package com.hudhud.app.domain.ready

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadyMessageProcessorTest {
    private val processor = ReadyMessageProcessor()

    @Test
    fun `maps selected columns and trims surrounding whitespace`() {
        val result = processor.process(
            rows = listOf(listOf("  +966500000000 ", " مرحباً ")),
            phoneColumnIndex = 0,
            messageColumnIndex = 1,
        )

        assertEquals("+966500000000", result.single().phone)
        assertEquals("مرحباً", result.single().text)
        assertTrue(result.single().isValid)
    }

    @Test
    fun `marks missing phone and message without dropping the row`() {
        val result = processor.process(
            rows = listOf(listOf("", null)),
            phoneColumnIndex = 0,
            messageColumnIndex = 1,
        )

        assertEquals(1, result.size)
        assertFalse(result.single().isValid)
        assertEquals(2, result.single().errors.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects using one column for phone and message`() {
        processor.process(listOf(listOf("value")), 0, 0)
    }
}
