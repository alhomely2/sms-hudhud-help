package com.hudhud.app.domain.template

import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.MessageTemplate

class TemplateMessageProcessor(
    private val renderer: TemplateTextRenderer = TemplateTextRenderer(),
) {
    fun process(
        headers: List<String>,
        rows: List<List<String?>>,
        template: MessageTemplate,
    ): List<MessageDraft> {
        val phoneColumnIndex = headers.indexOf(template.phoneColumn)
        val fixedValues = template.fixedValues.filterValues { it.isNotBlank() }
        // A fixed value is resolved from the template, so it never needs a document column.
        val variableColumnIndexes = template.variableColumns
            .filterKeys { it !in fixedValues }
            .mapValues { (_, column) -> headers.indexOf(column) }
        val missingMappings = variableColumnIndexes.filterValues { it < 0 }.keys
        val missingPhoneColumn = phoneColumnIndex < 0

        return rows.mapIndexed { rowIndex, row ->
            val phone = row.getOrNull(phoneColumnIndex).orEmpty().trim()
            val rendered = renderer.render(template.body) { variable ->
                fixedValues[variable] ?: run {
                    val columnIndex = variableColumnIndexes[variable] ?: -1
                    row.getOrNull(columnIndex).orEmpty().trim()
                }
            }
            val errors = buildList {
                if (missingPhoneColumn) add("عمود رقم الهاتف غير موجود")
                if (phone.isBlank()) add("رقم الهاتف فارغ")
                if (missingMappings.isNotEmpty()) {
                    add("حقول القالب غير مرتبطة: ${missingMappings.joinToString()}")
                }
                if (rendered.missingVariables.isNotEmpty()) {
                    add("بيانات القالب ناقصة: ${rendered.missingVariables.joinToString()}")
                }
                if (rendered.text.isBlank()) add("نص الرسالة فارغ")
            }

            MessageDraft(
                sourceRow = rowIndex + 1,
                phone = phone,
                text = rendered.text,
                errors = errors,
            )
        }
    }
}
