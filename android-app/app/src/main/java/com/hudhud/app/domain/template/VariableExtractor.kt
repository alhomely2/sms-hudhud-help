package com.hudhud.app.domain.template

import java.util.regex.Pattern

/**
 * Extracts template variables from template text.
 * Variables are in the format {{VariableName}}
 */
class VariableExtractor {

    /**
     * Pattern to match template variables: {{VariableName}}
     */
    private val variablePattern = Pattern.compile("\\{\\{([^}]+)\\}\\}")

    /**
     * Extract all unique variables from template text
     * @param templateText The template text containing variables
     * @return List of extracted variable names (without {{ }})
     */
    fun extractVariables(templateText: String): List<String> {
        if (templateText.isBlank()) return emptyList()

        val matcher = variablePattern.matcher(templateText)
        val variables = mutableSetOf<String>()

        while (matcher.find()) {
            val variableName = matcher.group(1)?.trim()
            if (!variableName.isNullOrBlank()) {
                variables.add(variableName)
            }
        }

        return variables.toList()
    }

    /**
     * Extract variables with their positions in the template
     * @param templateText The template text
     * @return List of variable info with positions
     */
    fun extractVariablesWithPosition(templateText: String): List<VariableInfo> {
        if (templateText.isBlank()) return emptyList()

        val matcher = variablePattern.matcher(templateText)
        val variables = mutableListOf<VariableInfo>()

        while (matcher.find()) {
            val variableName = matcher.group(1)?.trim()
            if (!variableName.isNullOrBlank()) {
                variables.add(
                    VariableInfo(
                        name = variableName,
                        fullMatch = matcher.group(0)!!,
                        startIndex = matcher.start(),
                        endIndex = matcher.end(),
                    )
                )
            }
        }

        return variables.distinctBy { it.name }
    }

    /**
     * Check if a text contains any variables
     * @param templateText The template text
     * @return true if variables are found
     */
    fun hasVariables(templateText: String): Boolean {
        return variablePattern.matcher(templateText).find()
    }

    /**
     * Count the number of variables in template text
     * @param templateText The template text
     * @return Number of unique variables
     */
    fun countVariables(templateText: String): Int {
        return extractVariables(templateText).size
    }

    /**
     * Replace all variables in template with values from a map
     * @param templateText The template text
     * @param values Map of variable names to values
     * @return Text with all variables replaced
     */
    fun replaceVariables(templateText: String, values: Map<String, String>): String {
        var result = templateText
        values.forEach { (variable, value) ->
            val placeholder = "{{$variable}}"
            result = result.replace(placeholder, value)
        }
        return result
    }

    /**
     * Replace a single variable in template
     * @param templateText The template text
     * @param variableName Variable name (without {{ }})
     * @param value New value
     * @return Text with variable replaced
     */
    fun replaceVariable(templateText: String, variableName: String, value: String): String {
        val placeholder = "{{$variableName}}"
        return templateText.replace(placeholder, value)
    }

    /**
     * Find variables that are not provided in the values map
     * @param templateText The template text
     * @param values Map of provided values
     * @return List of missing variable names
     */
    fun findMissingVariables(templateText: String, values: Map<String, String?>): List<String> {
        val templateVariables = extractVariables(templateText)
        return templateVariables.filter { variable ->
            values[variable].isNullOrBlank()
        }
    }

    /**
     * Validate that all required variables have values
     * @param templateText The template text
     * @param values Map of provided values
     * @return ValidationResult indicating success or missing variables
     */
    fun validate(templateText: String, values: Map<String, String?>): ValidationResult {
        val templateVariables = extractVariables(templateText)
        val missingVariables = templateVariables.filter { variable ->
            values[variable].isNullOrBlank()
        }

        return ValidationResult(
            isValid = missingVariables.isEmpty(),
            missingVariables = missingVariables,
            allVariables = templateVariables,
        )
    }

    /**
     * Get suggested variable names based on common patterns
     * @return List of common variable name suggestions
     */
    fun getCommonVariableSuggestions(): List<String> = listOf(
        "Name",
        "FirstName",
        "LastName",
        "FullName",
        "Phone",
        "Mobile",
        "Amount",
        "Price",
        "Total",
        "Balance",
        "Date",
        "DueDate",
        "InvoiceNumber",
        "OrderNumber",
        "Company",
        "Product",
        "Message",
        "Greeting",
    )

    /**
     * Parse variable name into display-friendly format
     * @param variableName Variable name
     * @return Display name
     */
    fun toDisplayName(variableName: String): String {
        return variableName
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
    }

    /**
     * Data class for variable information with position
     */
    data class VariableInfo(
        val name: String,
        val fullMatch: String,
        val startIndex: Int,
        val endIndex: Int,
    )

    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val missingVariables: List<String>,
        val allVariables: List<String>,
    )
}