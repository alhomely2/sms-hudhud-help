package com.hudhud.app.domain.settings

import java.net.URI

/**
 * Data model for an isolated site connection profile.
 *
 * Each profile contains its own endpoint URL, rate limiting rules,
 * batch sizing, and network timeouts. Secrets (API keys) are kept
 * separately in encrypted hardware-backed storage.
 */
data class SiteProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val delayBetweenMessagesMs: Long = DEFAULT_DELAY_MS,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val maxRetryAttempts: Int = DEFAULT_MAX_RETRIES,
    val connectTimeoutSeconds: Int = DEFAULT_CONNECT_TIMEOUT_SEC,
    val readTimeoutSeconds: Int = DEFAULT_READ_TIMEOUT_SEC,
    val callTimeoutSeconds: Int = DEFAULT_CALL_TIMEOUT_SEC,
    val isEnabled: Boolean = true,
    val isContractApproved: Boolean = false,
    val preset: ThrottlePreset = ThrottlePreset.fromValues(delayBetweenMessagesMs, maxRetryAttempts),
) {
    /** Returns a normalized base URL without trailing slashes. */
    fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/')

    companion object {
        const val DEFAULT_STAGING_ID = "default_staging"

        const val DEFAULT_DELAY_MS = 2000L
        const val MIN_DELAY_MS = 0L
        const val MAX_DELAY_MS = 60_000L

        const val DEFAULT_BATCH_SIZE = 50
        const val MIN_BATCH_SIZE = 10
        const val MAX_BATCH_SIZE = 500

        const val DEFAULT_MAX_RETRIES = 3
        const val MIN_RETRIES = 1
        const val MAX_RETRIES = 10

        const val DEFAULT_CONNECT_TIMEOUT_SEC = 15
        const val MIN_CONNECT_TIMEOUT_SEC = 5
        const val MAX_CONNECT_TIMEOUT_SEC = 60

        const val DEFAULT_READ_TIMEOUT_SEC = 20
        const val MIN_READ_TIMEOUT_SEC = 5
        const val MAX_READ_TIMEOUT_SEC = 120

        const val DEFAULT_CALL_TIMEOUT_SEC = 30
        const val MIN_CALL_TIMEOUT_SEC = 5
        const val MAX_CALL_TIMEOUT_SEC = 180
    }
}

/**
 * Validates site profile inputs before persistence or network use.
 */
object SiteProfileValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errorKey: String) : ValidationResult()
    }

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return if (trimmed.isBlank()) {
            ValidationResult.Invalid("name_empty")
        } else if (trimmed.length > 50) {
            ValidationResult.Invalid("name_too_long")
        } else {
            ValidationResult.Valid
        }
    }

    fun validateBaseUrl(url: String): ValidationResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return ValidationResult.Invalid("url_empty")
        }

        val uri = try {
            URI.create(trimmed)
        } catch (e: Exception) {
            return ValidationResult.Invalid("url_malformed")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            return ValidationResult.Invalid("url_must_be_https")
        }

        if (!uri.userInfo.isNullOrBlank()) {
            return ValidationResult.Invalid("url_credentials_prohibited")
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return ValidationResult.Invalid("url_host_missing")
        }

        if (uri.query != null || uri.fragment != null) {
            return ValidationResult.Invalid("url_query_prohibited")
        }

        return ValidationResult.Valid
    }

    fun validateDelaySeconds(seconds: Long?): ValidationResult {
        if (seconds == null) return ValidationResult.Invalid("delay_invalid")
        if (seconds < 0 || seconds > (SiteProfile.MAX_DELAY_MS / 1000)) {
            return ValidationResult.Invalid("delay_out_of_range")
        }
        return ValidationResult.Valid
    }

    fun validateDelayMs(delayMs: Long?): ValidationResult {
        if (delayMs == null) return ValidationResult.Invalid("delay_invalid")
        return if (delayMs in SiteProfile.MIN_DELAY_MS..SiteProfile.MAX_DELAY_MS) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("delay_out_of_range")
        }
    }

    fun validateMaxRetries(retries: Int?): ValidationResult {
        if (retries == null) return ValidationResult.Invalid("retries_invalid")
        if (retries !in SiteProfile.MIN_RETRIES..SiteProfile.MAX_RETRIES) {
            return ValidationResult.Invalid("retries_out_of_range")
        }
        return ValidationResult.Valid
    }

    fun validateBatchSize(batchSize: Int?): ValidationResult {
        if (batchSize == null) return ValidationResult.Invalid("batch_size_invalid")
        if (batchSize !in SiteProfile.MIN_BATCH_SIZE..SiteProfile.MAX_BATCH_SIZE) {
            return ValidationResult.Invalid("batch_size_out_of_range")
        }
        return ValidationResult.Valid
    }

    fun validateConnectTimeout(seconds: Int?): ValidationResult =
        validateTimeout(
            seconds,
            SiteProfile.MIN_CONNECT_TIMEOUT_SEC,
            SiteProfile.MAX_CONNECT_TIMEOUT_SEC,
        )

    fun validateReadTimeout(seconds: Int?): ValidationResult =
        validateTimeout(
            seconds,
            SiteProfile.MIN_READ_TIMEOUT_SEC,
            SiteProfile.MAX_READ_TIMEOUT_SEC,
        )

    fun validateCallTimeout(seconds: Int?): ValidationResult =
        validateTimeout(
            seconds,
            SiteProfile.MIN_CALL_TIMEOUT_SEC,
            SiteProfile.MAX_CALL_TIMEOUT_SEC,
        )

    private fun validateTimeout(seconds: Int?, minimum: Int, maximum: Int): ValidationResult {
        if (seconds == null) return ValidationResult.Invalid("timeout_invalid")
        return if (seconds in minimum..maximum) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("timeout_out_of_range")
        }
    }
}
