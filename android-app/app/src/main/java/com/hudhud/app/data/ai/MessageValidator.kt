package com.hudhud.app.data.ai

import com.hudhud.app.domain.model.AnalysisStatistics
import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.SmartAnalysisResult
import com.hudhud.app.domain.model.SmartSuggestion
import com.hudhud.app.domain.model.SuggestionAction
import com.hudhud.app.domain.model.SuggestionType

/**
 * Validates generated messages and detects various issues.
 * Part of the Smart AI engine for message analysis.
 */
class MessageValidator {

    // Phone number patterns
    private val validPhonePatterns = listOf(
        Regex("^\\+[1-9]\\d{6,14}$"),      // +966501234567
        Regex("^00[1-9]\\d{6,14}$"),       // 00966501234567
        Regex("^0\\d{9,14}$"),             // 0501234567
        Regex("^5\\d{8}$"),                // Saudi mobile 05xxxxxxxx
    )

    // Maximum message length (SMS standard)
    private val maxSmsLength = 160
    private val maxMultiPartLength = 153 // Per segment when concatenated

    // Minimum message length
    private val minMessageLength = 3

    // Patterns for detecting missing variables
    private val missingVariablePattern = Regex("\\{\\{[^}]+\\}\\}")

    /**
     * Validate a list of message drafts
     * @param messages List of message drafts to validate
     * @return SmartAnalysisResult with validation results and suggestions
     */
    fun validateMessages(messages: List<MessageDraft>): SmartAnalysisResult {
        val suggestions = mutableListOf<SmartSuggestion>()
        val warnings = mutableListOf<SmartSuggestion>()

        var validCount = 0
        var errorCount = 0
        var missingValueCount = 0
        var invalidPhoneCount = 0
        var duplicateCount = 0
        var longMessageCount = 0

        // Track duplicates
        val seenMessages = mutableSetOf<String>()
        val seenPhones = mutableSetOf<String>()

        messages.forEachIndexed { index, message ->
            val rowErrors = mutableListOf<String>()

            // Check for missing variables
            if (missingVariablePattern.containsMatchIn(message.text)) {
                missingValueCount++
                rowErrors.add("Missing template variables")
                suggestions.add(
                    SmartSuggestion(
                        id = "missing_$index",
                        type = SuggestionType.VALUE_MISSING,
                        title = "Row ${index + 1}: Missing values",
                        description = "Template variables not filled in this message",
                        confidence = 1.0f,
                        action = SuggestionAction.EXCLUDE_ROW,
                        metadata = mapOf("row" to index, "text" to message.text),
                    )
                )
            }

            // Validate phone number
            val phoneValidation = validatePhone(message.phone)
            if (!phoneValidation.isValid) {
                invalidPhoneCount++
                rowErrors.add("Invalid phone: ${phoneValidation.error}")
                suggestions.add(
                    SmartSuggestion(
                        id = "phone_$index",
                        type = SuggestionType.PHONE_INVALID,
                        title = "Row ${index + 1}: Invalid phone",
                        description = phoneValidation.error ?: "Phone number is invalid",
                        confidence = 1.0f,
                        action = SuggestionAction.EXCLUDE_ROW,
                        metadata = mapOf("row" to index, "phone" to message.phone),
                    )
                )
            }

            // Check message length
            val lengthValidation = validateMessageLength(message.text)
            if (!lengthValidation.isValid) {
                longMessageCount++
                if (lengthValidation.warning != null) {
                    warnings.add(
                        SmartSuggestion(
                            id = "length_$index",
                            type = SuggestionType.MESSAGE_LONG,
                            title = "Row ${index + 1}: Long message",
                            description = lengthValidation.warning,
                            confidence = 0.8f,
                            action = SuggestionAction.CONTINUE,
                            metadata = mapOf("row" to index, "length" to message.text.length),
                        )
                    )
                }
            }

            // Check for empty message
            if (message.text.length < minMessageLength) {
                errorCount++
                rowErrors.add("Message too short")
            }

            // Check for duplicates
            val messageKey = "${message.phone}::${message.text}"
            if (seenMessages.contains(messageKey)) {
                duplicateCount++
                suggestions.add(
                    SmartSuggestion(
                        id = "duplicate_$index",
                        type = SuggestionType.MESSAGE_DUPLICATE,
                        title = "Row ${index + 1}: Duplicate message",
                        description = "This message is identical to a previous one",
                        confidence = 1.0f,
                        action = SuggestionAction.EXCLUDE_ROW,
                        metadata = mapOf("row" to index),
                    )
                )
            } else {
                seenMessages.add(messageKey)
            }

            // Check for duplicate phones
            if (seenPhones.contains(message.phone) && message.phone.isNotBlank()) {
                // Just a warning, not an error
                warnings.add(
                    SmartSuggestion(
                        id = "phone_dup_$index",
                        type = SuggestionType.MESSAGE_DUPLICATE,
                        title = "Row ${index + 1}: Duplicate phone",
                        description = "This phone number appears in multiple messages",
                        confidence = 0.7f,
                        action = SuggestionAction.CONTINUE,
                        metadata = mapOf("row" to index, "phone" to message.phone),
                    )
                )
            } else {
                seenPhones.add(message.phone)
            }

            // Update counts
            if (rowErrors.isEmpty()) {
                validCount++
            } else {
                errorCount++
            }
        }

        // Generate batch-level warnings
        if (messages.size > 1000) {
            warnings.add(
                SmartSuggestion(
                    id = "batch_large",
                    type = SuggestionType.BATCH_WARNING,
                    title = "Large batch size",
                    description = "This batch contains ${messages.size} messages. Processing may take time.",
                    confidence = 0.9f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf("count" to messages.size),
                )
            )
        }

        return SmartAnalysisResult(
            suggestions = suggestions,
            warnings = warnings,
            statistics = AnalysisStatistics(
                totalCount = messages.size,
                validCount = validCount,
                errorCount = errorCount,
                warningCount = warnings.size,
                missingValueCount = missingValueCount,
                invalidPhoneCount = invalidPhoneCount,
                duplicateCount = duplicateCount,
                longMessageCount = longMessageCount,
            ),
        )
    }

    /**
     * Validate a single phone number
     */
    fun validatePhone(phone: String): PhoneValidationResult {
        if (phone.isBlank()) {
            return PhoneValidationResult(false, "Phone number is empty")
        }

        val cleaned = phone.replace(Regex("[\\s\\-()]"), "")

        // Check minimum length
        if (cleaned.length < 7) {
            return PhoneValidationResult(false, "Phone too short")
        }

        // Check maximum length
        if (cleaned.length > 15) {
            return PhoneValidationResult(false, "Phone too long")
        }

        // Check against valid patterns
        val isValid = validPhonePatterns.any { it.matches(cleaned) }

        if (!isValid) {
            // Check if it looks like a phone but just doesn't match patterns
            val looksLikePhone = cleaned.all { it.isDigit() || it == '+' }
            return if (looksLikePhone) {
                PhoneValidationResult(true, null, true) // Accept but warn
            } else {
                PhoneValidationResult(false, "Invalid phone format")
            }
        }

        return PhoneValidationResult(true, null)
    }

    /**
     * Validate message length
     */
    fun validateMessageLength(text: String): LengthValidationResult {
        val length = text.length

        return when {
            length < minMessageLength -> LengthValidationResult(
                isValid = false,
                error = "Message too short (minimum $minMessageLength characters)",
            )
            length <= maxSmsLength -> LengthValidationResult(isValid = true)
            length <= maxSmsLength * 10 -> LengthValidationResult(
                isValid = true,
                warning = "Message is $length characters (${(length / maxMultiPartLength) + 1} SMS parts)",
            )
            else -> LengthValidationResult(
                isValid = false,
                error = "Message is too long ($length characters)",
            )
        }
    }

    /**
     * Validate a single message
     */
    fun validateMessage(message: MessageDraft): MessageValidationResult {
        val errors = mutableListOf<String>()

        // Validate phone
        val phoneValidation = validatePhone(message.phone)
        if (!phoneValidation.isValid) {
            errors.add("Invalid phone: ${phoneValidation.error}")
        } else if (phoneValidation.hasWarning) {
            errors.add("Phone format warning: ${phoneValidation.error}")
        }

        // Check for missing variables
        if (missingVariablePattern.containsMatchIn(message.text)) {
            errors.add("Template variables not filled")
        }

        // Check length
        val lengthValidation = validateMessageLength(message.text)
        if (!lengthValidation.isValid) {
            errors.add(lengthValidation.error ?: "Invalid length")
        } else if (lengthValidation.warning != null) {
            // Just a warning, not an error
        }

        return MessageValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            phoneValid = phoneValidation.isValid,
            lengthValid = lengthValidation.isValid,
            hasMissingVariables = missingVariablePattern.containsMatchIn(message.text),
        )
    }

    /**
     * Data class for phone validation result
     */
    data class PhoneValidationResult(
        val isValid: Boolean,
        val error: String?,
        val hasWarning: Boolean = false,
    )

    /**
     * Data class for length validation result
     */
    data class LengthValidationResult(
        val isValid: Boolean,
        val error: String? = null,
        val warning: String? = null,
    )

    /**
     * Data class for message validation result
     */
    data class MessageValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val phoneValid: Boolean,
        val lengthValid: Boolean,
        val hasMissingVariables: Boolean,
    )
}