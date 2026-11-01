package com.hudhud.app.domain.document

import java.text.Normalizer

class PdfReportDetector {
    fun detect(firstPageText: String, fileName: String? = null): PdfReportTemplate? {
        val normalizedText = normalizeForMatching(firstPageText)
        val normalizedFileName = normalizeForMatching(fileName ?: "")

        val templateOrder = PdfTemplateCatalog.all
        val directMatch = templateOrder.firstOrNull { template ->
            val signature = normalizeForMatching(template.signature)
            signature in normalizedText || signature in normalizedFileName ||
                anySignatureWordMatches(signature, normalizedText) ||
                anySignatureWordMatches(signature, normalizedFileName)
        }
        if (directMatch != null) return directMatch

        val aliasFallbacks = listOf(
            "فاتورة" to MonthlyInvoiceTemplate,
            "فواتير" to MonthlyInvoiceTemplate,
            "كشفالتحصيل" to CollectionDetailLandscapeTemplate,
            "كشفتحصيل" to CollectionAmountsPortraitTemplate,
            "تقريرتفصيل" to PaymentDetailsTemplate,
            "تقريرالمشتركين" to DisconnectionReportTemplate,
            "المشتركينالمفعلين" to DisconnectionReportTemplate,
        )

        return aliasFallbacks.firstOrNull { (keyword, _) ->
            normalizedText.contains(keyword) || normalizedFileName.contains(keyword)
        }?.second
    }

    private fun anySignatureWordMatches(signature: String, candidate: String): Boolean {
        if (candidate.isBlank()) return false
        val words = signature.split(Regex("\\s+"))
            .map { normalizeForMatching(it) }
            .filter { it.isNotEmpty() }
        return words.any { word -> candidate.contains(word) }
    }

    internal fun normalizeForMatching(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace('\u06CC', '\u064A')
            .replace('\u06A9', '\u0643')
            .replace('\u0640', ' ')
            .replace(Regex("[\\p{Punct}]"), " ")
            .filter { character -> character.isLetterOrDigit() || character.isWhitespace() }
            .replace(Regex("\\s+"), "")
}
