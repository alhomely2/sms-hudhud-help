package com.hudhud.app.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteProfileTest {

    @Test
    fun `https url with valid host is valid`() {
        val result = SiteProfileValidator.validateBaseUrl("https://staging.mytestdomain.club")
        assertTrue(result is SiteProfileValidator.ValidationResult.Valid)
    }

    @Test
    fun `http url is rejected for security`() {
        val result = SiteProfileValidator.validateBaseUrl("http://insecure.example.com")
        assertTrue(result is SiteProfileValidator.ValidationResult.Invalid)
        assertEquals("url_must_be_https", (result as SiteProfileValidator.ValidationResult.Invalid).errorKey)
    }

    @Test
    fun `url containing credentials in authority is rejected`() {
        val result = SiteProfileValidator.validateBaseUrl("https://admin:secret123@api.example.com")
        assertTrue(result is SiteProfileValidator.ValidationResult.Invalid)
        assertEquals("url_credentials_prohibited", (result as SiteProfileValidator.ValidationResult.Invalid).errorKey)
    }

    @Test
    fun `blank or missing url is rejected`() {
        val result = SiteProfileValidator.validateBaseUrl("   ")
        assertTrue(result is SiteProfileValidator.ValidationResult.Invalid)
        assertEquals("url_empty", (result as SiteProfileValidator.ValidationResult.Invalid).errorKey)
    }

    @Test
    fun `url with query params or fragment is rejected`() {
        val result = SiteProfileValidator.validateBaseUrl("https://api.example.com/test?token=123")
        assertTrue(result is SiteProfileValidator.ValidationResult.Invalid)
        assertEquals("url_query_prohibited", (result as SiteProfileValidator.ValidationResult.Invalid).errorKey)
    }

    @Test
    fun `name validation checks blank and length`() {
        assertTrue(SiteProfileValidator.validateName("") is SiteProfileValidator.ValidationResult.Invalid)
        assertTrue(SiteProfileValidator.validateName("   ") is SiteProfileValidator.ValidationResult.Invalid)
        assertTrue(SiteProfileValidator.validateName("موقع الإنتاج") is SiteProfileValidator.ValidationResult.Valid)
    }

    @Test
    fun `delay validation enforces bounds`() {
        assertTrue(SiteProfileValidator.validateDelaySeconds(-1) is SiteProfileValidator.ValidationResult.Invalid)
        assertTrue(SiteProfileValidator.validateDelaySeconds(0) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateDelaySeconds(2) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateDelaySeconds(60) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateDelaySeconds(61) is SiteProfileValidator.ValidationResult.Invalid)
    }

    @Test
    fun `millisecond delay validation preserves fractional-second settings`() {
        assertTrue(SiteProfileValidator.validateDelayMs(0L) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateDelayMs(1500L) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateDelayMs(SiteProfile.MAX_DELAY_MS) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateDelayMs(-1L) is SiteProfileValidator.ValidationResult.Invalid)
        assertTrue(SiteProfileValidator.validateDelayMs(SiteProfile.MAX_DELAY_MS + 1) is SiteProfileValidator.ValidationResult.Invalid)
    }

    @Test
    fun `max retries validation enforces bounds`() {
        assertTrue(SiteProfileValidator.validateMaxRetries(0) is SiteProfileValidator.ValidationResult.Invalid)
        assertTrue(SiteProfileValidator.validateMaxRetries(1) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateMaxRetries(3) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateMaxRetries(10) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateMaxRetries(11) is SiteProfileValidator.ValidationResult.Invalid)
    }

    @Test
    fun `batch size validation enforces bounds`() {
        assertTrue(SiteProfileValidator.validateBatchSize(5) is SiteProfileValidator.ValidationResult.Invalid)
        assertTrue(SiteProfileValidator.validateBatchSize(10) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateBatchSize(50) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateBatchSize(200) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateBatchSize(500) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateBatchSize(501) is SiteProfileValidator.ValidationResult.Invalid)
    }

    @Test
    fun `timeout validators enforce separate safe bounds`() {
        assertTrue(SiteProfileValidator.validateConnectTimeout(5) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateConnectTimeout(60) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateConnectTimeout(4) is SiteProfileValidator.ValidationResult.Invalid)
        assertTrue(SiteProfileValidator.validateConnectTimeout(61) is SiteProfileValidator.ValidationResult.Invalid)

        assertTrue(SiteProfileValidator.validateReadTimeout(5) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateReadTimeout(120) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateReadTimeout(121) is SiteProfileValidator.ValidationResult.Invalid)

        assertTrue(SiteProfileValidator.validateCallTimeout(5) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateCallTimeout(180) is SiteProfileValidator.ValidationResult.Valid)
        assertTrue(SiteProfileValidator.validateCallTimeout(181) is SiteProfileValidator.ValidationResult.Invalid)
    }

    @Test
    fun `normalizedBaseUrl trims whitespace and trailing slashes`() {
        val profile = SiteProfile(
            id = "test",
            name = "Test",
            baseUrl = "  https://example.com/api///  ",
        )
        assertEquals("https://example.com/api", profile.normalizedBaseUrl())
    }
}
