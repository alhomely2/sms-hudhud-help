package com.hudhud.app.data.document

import com.hudhud.app.domain.model.MessageTemplate
import com.hudhud.app.domain.model.SavedTemplate

data class TemplateWorkbookSheetSchema(
    val name: String,
    val columns: List<String>,
)

data class TemplateWorkbookMatch(
    val template: SavedTemplate,
    val matchedSheets: List<String>,
    val missingColumns: Set<String>,
    val ambiguousColumns: Set<String>,
) {
    val isMatch: Boolean get() = matchedSheets.isNotEmpty()
}

object TemplateWorkbookMatcher {
    fun match(
        sheets: List<TemplateWorkbookSheetSchema>,
        templates: List<SavedTemplate>,
    ): List<TemplateWorkbookMatch> = templates.mapNotNull { template ->
        if (template.phoneColumn.isBlank() ||
            sheets.none { template.phoneColumn in it.columns }
        ) {
            return@mapNotNull null
        }

        val requiredColumns = buildList {
            add(template.phoneColumn)
            addAll(template.toMessageTemplate().documentColumns())
        }.distinct()
        val phoneSheets = sheets.filter { template.phoneColumn in it.columns }
        val matchedSheets = phoneSheets
            .filter { sheet ->
                requiredColumns.all { column -> sheet.columns.count { it == column } == 1 }
            }
            .map(TemplateWorkbookSheetSchema::name)
        val missingColumns = if (matchedSheets.isNotEmpty()) {
            emptySet()
        } else {
            val closestSheet = phoneSheets.maxBy { sheet ->
                requiredColumns.count(sheet.columns::contains)
            }
            requiredColumns.filterNot(closestSheet.columns::contains).toSet()
        }
        val ambiguousColumns = if (matchedSheets.isNotEmpty()) {
            emptySet()
        } else {
            phoneSheets.flatMap { sheet ->
                requiredColumns.filter { column -> sheet.columns.count { it == column } > 1 }
            }.toSet()
        }
        TemplateWorkbookMatch(template, matchedSheets, missingColumns, ambiguousColumns)
    }

    private fun SavedTemplate.toMessageTemplate() = MessageTemplate(
        name = name,
        body = body,
        phoneColumn = phoneColumn,
        variableColumns = variableColumns,
        fixedValues = fixedValues,
    )
}
