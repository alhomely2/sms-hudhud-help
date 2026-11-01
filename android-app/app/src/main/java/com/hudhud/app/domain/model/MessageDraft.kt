package com.hudhud.app.domain.model

/**
 * Represents a generated message draft ready for preview or sending.
 */
data class MessageDraft(
    val sourceRow: Int,
    val phone: String,
    val text: String,
    val errors: List<String> = emptyList(),
) {
    /**
     * Check if the message is valid (no errors)
     */
    val isValid: Boolean
        get() = errors.isEmpty() && phone.isNotBlank() && text.isNotBlank()

    /**
     * Get message length
     */
    val length: Int
        get() = text.length

    /**
     * Check if message exceeds SMS length
     */
    val isLongMessage: Boolean
        get() = length > 160

    /**
     * Estimate SMS parts needed
     */
    val estimatedSmsParts: Int
        get() = when {
            length <= 160 -> 1
            length <= 306 -> 2
            length <= 459 -> 3
            length <= 612 -> 4
            else -> (length / 153) + 1
        }

    /**
     * Get a preview of the message (first 50 chars)
     */
    val preview: String
        get() = if (text.length > 50) text.take(50) + "..." else text

    /**
     * Check if phone number looks valid
     */
    val hasValidPhone: Boolean
        get() = phone.isNotBlank() && (phone.startsWith("+") || phone.startsWith("0"))

    /**
     * Create a copy with added error
     */
    fun withError(error: String): MessageDraft {
        return copy(errors = errors + error)
    }
}

/**
 * Collection of message drafts with metadata
 */
data class MessageDraftCollection(
    val messages: List<MessageDraft>,
    val templateName: String?,
    val generatedAt: Long = System.currentTimeMillis(),
) {
    val totalCount: Int
        get() = messages.size

    val validCount: Int
        get() = messages.count { it.isValid }

    val invalidCount: Int
        get() = messages.count { !it.isValid }

    val uniquePhones: Int
        get() = messages.map { it.phone }.distinct().size

    val totalSmsParts: Int
        get() = messages.sumOf { it.estimatedSmsParts }

    val successRate: Float
        get() = if (totalCount > 0) validCount.toFloat() / totalCount else 0f
}