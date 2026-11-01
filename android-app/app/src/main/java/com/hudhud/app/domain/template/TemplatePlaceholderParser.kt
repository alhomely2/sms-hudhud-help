package com.hudhud.app.domain.template

/**
 * Single source of truth for `{{field}}` placeholders.
 *
 * The editor, the renderer and validation all read placeholders through this
 * object, so a column name written in Arabic behaves exactly like a Latin one.
 * Names may contain any character except braces and line breaks.
 */
object TemplatePlaceholderParser {
    private val placeholderPattern = Regex("""\{\{\s*([^{}\r\n]+?)\s*\}\}""")

    /** Placeholder names in body order, trimmed, without duplicates. */
    fun names(template: String): List<String> =
        placeholderPattern.findAll(template)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /**
     * Replaces every placeholder using [valueFor]; unresolved names are kept
     * verbatim and reported back so the caller can surface a clear error.
     */
    fun replace(
        template: String,
        valueFor: (String) -> String?,
    ): Pair<String, List<String>> {
        val missing = linkedSetOf<String>()
        val text = placeholderPattern.replace(template) { match ->
            val name = match.groupValues[1].trim()
            valueFor(name)?.takeIf { it.isNotBlank() } ?: run {
                missing += name
                match.value
            }
        }
        return text to missing.toList()
    }

    /** The exact token the editor must insert for [name]. */
    fun placeholderFor(name: String): String = "{{${name.trim()}}}"

    /** Names must stay parseable: no braces and no line breaks. */
    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name.none { it == '{' || it == '}' || it == '\n' || it == '\r' }
}
