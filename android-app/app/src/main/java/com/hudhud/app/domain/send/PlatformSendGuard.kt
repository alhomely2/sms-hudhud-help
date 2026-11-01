package com.hudhud.app.domain.send

import com.hudhud.app.data.local.AppSettingsStore

/**
 * Protects the delivery layer until the platform contract is explicitly approved.
 *
 * This guard intentionally blocks any real send operation when the selected profile,
 * API key, or contract state is not valid. It is the first line of defense before
 * a remote dispatch repository can enqueue or send payloads.
 */
class PlatformSendGuard(
    private val appSettings: AppSettingsStore,
    private val apiKeyLookup: (String) -> String?,
    private val contractApprovedProvider: () -> Boolean = { false },
) {
    fun evaluate(profileId: String? = null): SendGateResult {
        val targetProfileId = profileId ?: appSettings.getActiveProfile().id
        val profile = appSettings.getProfile(targetProfileId)
            ?: return SendGateResult.blocked(
                SendGateStatus.BLOCKED_MISSING_PROFILE,
                "No site profile exists for id=$targetProfileId",
            )

        if (!profile.isEnabled) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_INVALID_STATE,
                "The selected profile is disabled.",
            )
        }

        if (!isValidBaseUrl(profile.baseUrl)) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_INVALID_URL,
                "The site base URL must be HTTPS and must not contain credentials or query fragments.",
            )
        }

        val apiKey = apiKeyLookup(targetProfileId)
        if (apiKey.isNullOrBlank()) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_MISSING_API_KEY,
                "No API key is stored for the selected profile.",
            )
        }

        if (!profile.isContractApproved || !contractApprovedProvider()) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_UNAPPROVED_CONTRACT,
                "Platform contract and endpoint approval are required before sending.",
            )
        }

        return SendGateResult.approved()
    }

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return false
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        if (trimmed.contains("@") || trimmed.contains("?") || trimmed.contains("#")) return false
        return true
    }
}

enum class SendGateStatus {
    APPROVED,
    BLOCKED_MISSING_PROFILE,
    BLOCKED_INVALID_STATE,
    BLOCKED_MISSING_API_KEY,
    BLOCKED_INVALID_URL,
    BLOCKED_UNAPPROVED_CONTRACT,
}

data class SendGateResult(
    val status: SendGateStatus,
    val detail: String? = null,
) {
    val canProceed: Boolean get() = status == SendGateStatus.APPROVED

    companion object {
        fun approved() = SendGateResult(SendGateStatus.APPROVED, "Ready to send")

        fun blocked(status: SendGateStatus, detail: String?) = SendGateResult(status, detail)
    }
}
