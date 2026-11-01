package com.hudhud.app.domain.template

import com.hudhud.app.domain.model.SavedTemplate

enum class TemplateValidationError {
    NAME_REQUIRED,
    BODY_REQUIRED,
}

data class TemplateValidationResult(
    val errors: Set<TemplateValidationError> = emptySet(),
    val placeholders: List<String> = emptyList(),
) {
    val isValid: Boolean get() = errors.isEmpty()
    fun has(error: TemplateValidationError): Boolean = error in errors
}

/**
 * Validates templates for correctness and completeness.
 * Checks template structure, variables, and phone column configuration.
 */
class TemplateValidator {

    companion object {
        fun validate(name: String, body: String): TemplateValidationResult {
            val errors = mutableSetOf<TemplateValidationError>()
            if (name.isBlank()) errors += TemplateValidationError.NAME_REQUIRED
            if (body.isBlank()) errors += TemplateValidationError.BODY_REQUIRED
            val placeholders = TemplatePlaceholderParser.names(body)
            return TemplateValidationResult(errors = errors, placeholders = placeholders)
        }
    }

    private val variableExtractor = VariableExtractor()
    
    // Minimum template length
    private val minTemplateLength = 3
    
    // Maximum template length (SMS segments)
    private val maxTemplateLength = 1530 // 10 SMS segments

    /**
     * Validate a template
     * @param template The template to validate
     * @return ValidationResult with status and errors
     */
    fun validate(template: SavedTemplate): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Validate name
        val nameValidation = validateName(template.name)
        errors.addAll(nameValidation.errors)
        warnings.addAll(nameValidation.warnings)

        // Validate body
        val bodyValidation = validateBody(template.body)
        errors.addAll(bodyValidation.errors)
        warnings.addAll(bodyValidation.warnings)

        // Validate variables
        val variableValidation = validateVariables(template)
        errors.addAll(variableValidation.errors)
        warnings.addAll(variableValidation.warnings)

        // Check phone column
        if (template.phoneColumn.isBlank() && template.variableColumns.isEmpty()) {
            warnings.add("No phone column or variables specified")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            variableCount = variableExtractor.countVariables(template.body),
            estimatedLength = template.body.length,
        )
    }

    /**
     * Validate template text
     */
    fun validateBody(body: String): BodyValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check empty
        if (body.isBlank()) {
            errors.add("Template body is empty")
            return BodyValidationResult(isValid = false, errors, warnings)
        }

        // Check minimum length
        if (body.length < minTemplateLength) {
            errors.add("Template is too short (minimum $minTemplateLength characters)")
        }

        // Check maximum length
        if (body.length > maxTemplateLength) {
            warnings.add("Template is very long (${body.length} chars). Consider splitting into multiple templates.")
        }

        // Check for unbalanced braces
        val openBraces = body.count { it == '{' }
        val closeBraces = body.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add("Unbalanced braces in template: $openBraces '{' vs $closeBraces '}'")
        }

        // Check for potentially invalid variable syntax
        val variablePattern = Regex("\\{\\{[^}]+\\}\\}")
        val variables = variablePattern.findAll(body).toList()
        
        // Check for empty variable names
        variables.forEach { match ->
            val varName = match.groupValues[1].trim()
            if (varName.isEmpty()) {
                errors.add("Empty variable name found: ${match.value}")
            }
        }

        // Check for common mistakes
        if (body.contains("{{}}")) {
            errors.add("Empty variable braces found: {{}}")
        }

        return BodyValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            variableCount = variables.size,
        )
    }

    /**
     * Validate template name
     */
    fun validateName(name: String): NameValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("Template name is required")
            return NameValidationResult(isValid = false, errors, warnings)
        }

        if (name.length < 2) {
            errors.add("Template name is too short (minimum 2 characters)")
        }

        if (name.length > 100) {
            errors.add("Template name is too long (maximum 100 characters)")
        }

        // Check for invalid characters
        val invalidChars = name.filter { it in "!@#$%^&*()+={}[]|\\:;\"<>?," }
        if (invalidChars.isNotEmpty()) {
            warnings.add("Template name contains special characters: $invalidChars")
        }

        return NameValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Validate template variables
     */
    fun validateVariables(template: SavedTemplate): VariableValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val variables = variableExtractor.extractVariables(template.body)

        // Check for no variables
        if (variables.isEmpty()) {
            warnings.add("Template has no variables - all messages will be identical")
        }

        // Check variables against defined columns
        template.variableColumns.forEach { (variable, column) ->
            if (column.isBlank()) {
                warnings.add("Variable '$variable' has no column mapping")
            }
        }

        // Check for undefined variables (in body but not in variableColumns)
        val definedVariables = template.variableColumns.keys
        variables.forEach { variable ->
            if (!definedVariables.contains(variable)) {
                warnings.add("Variable '$variable' is in template body but not mapped to any column")
            }
        }

        // Check for circular references
        variables.forEach { variable ->
            if (template.body.contains("{{$variable}}") && 
                template.variableColumns[variable] == variable) {
                errors.add("Circular reference detected: '$variable' maps to itself")
            }
        }

        return VariableValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            variableCount = variables.size,
            unmappedVariables = variables.filter { !definedVariables.contains(it) },
        )
    }

    /**
     * Quick validation check
     */
    fun isValid(template: SavedTemplate): Boolean {
        return validate(template).isValid
    }

    /**
     * Check if template has variables
     */
    fun hasVariables(template: SavedTemplate): Boolean {
        return variableExtractor.hasVariables(template.body)
    }

    /**
     * Get all variables from template
     */
    fun getVariables(template: SavedTemplate): List<String> {
        return variableExtractor.extractVariables(template.body)
    }

    // ========== Validation Result Classes ==========

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val variableCount: Int,
        val estimatedLength: Int,
    )

    data class BodyValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val variableCount: Int = 0,
    )

    data class NameValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
    )

    data class VariableValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val variableCount: Int,
        val unmappedVariables: List<String>,
    )
}