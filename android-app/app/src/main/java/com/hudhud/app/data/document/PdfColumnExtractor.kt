package com.hudhud.app.data.document

import android.content.Context
import com.hudhud.app.domain.document.PdfReportDetector
import com.hudhud.app.domain.document.PdfReportTemplate
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.io.StringWriter

data class ExtractedPdfColumns(
    val template: PdfReportTemplate,
    val pageCount: Int,
)

class PdfColumnExtractor(
    private val detector: PdfReportDetector = PdfReportDetector(),
) {
    fun extract(
        context: Context,
        input: InputStream,
        fileName: String? = null,
    ): ExtractedPdfColumns {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(input).use { document ->
            require(document.numberOfPages > 0) { "ملف PDF لا يحتوي صفحات" }

            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = 1
            }
            val firstPageText = StringWriter().also { writer ->
                stripper.writeText(document, writer)
            }.toString()
            val template = detector.detect(firstPageText, fileName)
                ?: throw UnsupportedPdfTemplateException()

            return ExtractedPdfColumns(
                template = template,
                pageCount = document.numberOfPages,
            )
        }
    }
}

class UnsupportedPdfTemplateException : Exception(
    "لم يتم التعرف على نوع هذا المستند. لم تُدمج بياناته مع قالب آخر.",
)
