package com.hudhud.app.domain.template

data class RenderedTemplateText(
    val text: String,
    val missingVariables: List<String>,
)

class TemplateTextRenderer {
    fun render(
        template: String,
        valueFor: (String) -> String?,
    ): RenderedTemplateText {
        val (text, missingVariables) = TemplatePlaceholderParser.replace(template, valueFor)
        return RenderedTemplateText(text, missingVariables)
    }
}
