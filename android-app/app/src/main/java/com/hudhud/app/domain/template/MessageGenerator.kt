package com.hudhud.app.domain.template

import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.data.ai.MessageValidator
import com.hudhud.app.data.ai.MostCommonValueFinder

/**
 * Generates messages from templates and file data.
 * Combines template with data columns to create final messages.
 */
class MessageGenerator {

    private val variableExtractor = VariableExtractor()
    private val messageValidator = MessageValidator()
    private val mostCommonValueFinder = MostCommonValueFinder()

    /**
     * Generate messages from template and file data
     * @param template The template to use
     * @param dataRows List of data rows (column name -> value)
     * @param phoneColumn Name of the column containing phone numbers
     * @param variableMappings Map of template variable -> file column
     * @param fixedValues Fixed values to insert in every message
     * @return List of generated message drafts
     */
    fun generateMessages(
        template: SavedTemplate,
        dataRows: List<Map<String, String>>,
        phoneColumn: String,
        variableMappings: Map<String, String>,
        fixedValues: Map<String, String> = emptyMap(),
    ): List<MessageDraft> {
        
        val messages = mutableListOf<MessageDraft>()

        dataRows.forEachIndexed { index, row ->
            val result = generateSingleMessage(
                template = template,
                dataRow = row,
                phoneColumn = phoneColumn,
                variableMappings = variableMappings,
                fixedValues = fixedValues,
                rowIndex = index,
            )
            messages.add(result)
        }

        return messages
    }

    /**
     * Generate a single message from a data row
     */
    fun generateSingleMessage(
        template: SavedTemplate,
        dataRow: Map<String, String>,
        phoneColumn: String,
        variableMappings: Map<String, String>,
        fixedValues: Map<String, String> = emptyMap(),
        rowIndex: Int = 0,
    ): MessageDraft {
        
        val errors = mutableListOf<String>()

        // Get phone number
        val phone = dataRow[phoneColumn]?.trim() ?: ""
        if (phone.isBlank()) {
            errors.add("Phone number is empty")
        }

        // Build variable values map
        val variableValues = mutableMapOf<String, String?>()

        // Add mapped column values
        variableMappings.forEach { (variable, column) ->
            variableValues[variable] = dataRow[column]?.trim()
        }

        // Add fixed values
        variableValues.putAll(fixedValues)

        // Replace variables in template
        var messageText = template.body
        variableValues.forEach { (variable, value) ->
            val placeholder = "{{$variable}}"
            messageText = messageText.replace(placeholder, value ?: "")
        }

        // Check for remaining unfilled variables
        val missingVariables = variableExtractor.findMissingVariables(
            template.body,
            variableValues.mapValues { it.value }
        )
        
        if (missingVariables.isNotEmpty()) {
            errors.add("Missing values for: ${missingVariables.joinToString(", ")}")
        }

        // Validate the generated message
        val validation = messageValidator.validatePhone(phone)
        if (!validation.isValid && validation.error != null) {
            // Don't add error if we already have phone error
            if (!errors.any { it.startsWith("Phone") }) {
                // Just a warning for phone validation
            }
        }

        return MessageDraft(
            sourceRow = rowIndex + 1, // 1-based for display
            phone = phone,
            text = messageText,
            errors = errors,
        )
    }

    /**
     * Generate messages with smart fill for missing values
     * Uses most common values from the dataset to fill missing data
     */
    fun generateMessagesWithSmartFill(
        template: SavedTemplate,
        dataRows: List<Map<String, String>>,
        phoneColumn: String,
        variableMappings: Map<String, String>,
        fixedValues: Map<String, String> = emptyMap(),
        useSmartFill: Boolean = true,
    ): List<MessageDraft> {
        
        // If smart fill is enabled, calculate most common values
        val smartFillValues = if (useSmartFill) {
            mostCommonValueFinder.findMostCommonValues(dataRows, variableMappings.values.toList())
        } else {
            emptyMap()
        }

        val messages = mutableListOf<MessageDraft>()

        dataRows.forEachIndexed { index, row ->
            // Merge row data with smart fill values
            val mergedRow = row.toMutableMap()
            
            if (useSmartFill) {
                variableMappings.values.forEach { column ->
                    if (mergedRow[column].isNullOrBlank()) {
                        smartFillValues[column]?.let { mergedRow[column] = it }
                    }
                }
            }

            val result = generateSingleMessage(
                template = template,
                dataRow = mergedRow,
                phoneColumn = phoneColumn,
                variableMappings = variableMappings,
                fixedValues = fixedValues,
                rowIndex = index,
            )
            messages.add(result)
        }

        return messages
    }

    /**
     * Generate preview messages (limited number)
     * @param template The template
     * @param dataRows Sample data rows
     * @param phoneColumn Phone column name
     * @param variableMappings Variable to column mappings
     * @param previewCount Number of preview messages to generate
     * @return List of preview messages
     */
    fun generatePreview(
        template: SavedTemplate,
        dataRows: List<Map<String, String>>,
        phoneColumn: String,
        variableMappings: Map<String, String>,
        previewCount: Int = 5,
    ): List<MessageDraft> {
        
        val previewRows = dataRows.take(previewCount)
        return generateMessages(
            template = template,
            dataRows = previewRows,
            phoneColumn = phoneColumn,
            variableMappings = variableMappings,
        )
    }

    /**
     * Generate a test message with placeholder values
     */
    fun generateTestMessage(
        template: SavedTemplate,
    ): MessageDraft {
        
        val testValues = mapOf(
            "Name" to "أحمد",
            "FirstName" to "أحمد",
            "LastName" to "محمد",
            "Amount" to "1,000",
            "Price" to "500",
            "Total" to "1,500",
            "Balance" to "750",
            "Date" to "01/01/2025",
            "DueDate" to "31/01/2025",
            "InvoiceNumber" to "INV-001",
            "OrderNumber" to "ORD-001",
            "Company" to "شركة التجديد",
            "Product" to "المنتج الذهبي",
            "Message" to "هذه رسالة اختبار",
        )

        // Replace variables in template
        var messageText = template.body
        testValues.forEach { (variable, value) ->
            val placeholder = "{{$variable}}"
            messageText = messageText.replace(placeholder, value)
        }

        return MessageDraft(
            sourceRow = 0,
            phone = "+966501234567",
            text = messageText,
            errors = emptyList(),
        )
    }

    /**
     * Get statistics about generated messages
     */
    fun getMessageStatistics(messages: List<MessageDraft>): MessageStatistics {
        val total = messages.size
        val valid = messages.count { it.isValid }
        val invalid = total - valid
        val uniquePhones = messages.map { it.phone }.distinct().size
        val duplicatePhones = total - uniquePhones
        
        // Count various error types
        val phoneErrors = messages.count { it.errors.any { e -> e.contains("Phone") } }
        val missingValueErrors = messages.count { it.errors.any { e -> e.contains("Missing") || e.contains("قيمة") } }
        val emptyMessages = messages.count { it.text.isBlank() }

        return MessageStatistics(
            totalMessages = total,
            validMessages = valid,
            invalidMessages = invalid,
            uniquePhones = uniquePhones,
            duplicatePhones = duplicatePhones,
            phoneErrors = phoneErrors,
            missingValueErrors = missingValueErrors,
            emptyMessages = emptyMessages,
        )
    }

    /**
     * Statistics about generated messages
     */
    data class MessageStatistics(
        val totalMessages: Int,
        val validMessages: Int,
        val invalidMessages: Int,
        val uniquePhones: Int,
        val duplicatePhones: Int,
        val phoneErrors: Int,
        val missingValueErrors: Int,
        val emptyMessages: Int,
    ) {
        val validPercentage: Float
            get() = if (totalMessages > 0) validMessages.toFloat() / totalMessages else 0f
    }
}