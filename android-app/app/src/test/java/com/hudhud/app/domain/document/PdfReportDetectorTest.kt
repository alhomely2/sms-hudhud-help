package com.hudhud.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReportDetectorTest {
    private val detector = PdfReportDetector()

    @Test
    fun `detects all five independent report layouts`() {
        val cases = listOf(
            "ﻓﺎﺗـﻮﺭﺓ ﻛﻬﺮﺑــﺎء" to MonthlyInvoiceTemplate,
            "كشف التحصيل مع تفصيل متأخرات ومبيعات المشتركين" to
                CollectionDetailLandscapeTemplate,
            "كشف تحصيل المبالغ التي على المشتركين" to
                CollectionAmountsPortraitTemplate,
            "تقرير تفصيل تسديدات المشتركين" to PaymentDetailsTemplate,
            "تقرير بالمشتركين المفعلين" to DisconnectionReportTemplate,
        )

        cases.forEach { (text, expected) ->
            assertEquals(expected, detector.detect(text))
        }
    }

    @Test
    fun `detects report headings extracted from the provided samples`() {
        val cases = listOf(
            "فاتورة كهرباء شهر أكتوبر" to MonthlyInvoiceTemplate,
            "كشف التحصيل مع تفصيــل متأخرات ومبيعات المشتركين للفترة أكتوبر" to
                CollectionDetailLandscapeTemplate,
            "كشف تحصيل المبالغ التي على المشتركين للفترة أكتوبر" to
                CollectionAmountsPortraitTemplate,
            "تقرير تفصيل تسديدات المشتركين حسب تاريخ التحصيل" to
                PaymentDetailsTemplate,
            "تقرير بالمشتركين المفعلين الغير مسددين" to
                DisconnectionReportTemplate,
        )

        cases.forEach { (text, expected) ->
            assertEquals(expected, detector.detect(text))
        }
    }

    @Test
    fun `detects invoice documents by filename when the OCR text is garbled`() {
        assertEquals(
            MonthlyInvoiceTemplate,
            detector.detect("مخطوطة غير واضحة في الصفحة الأولى", "فواتير عينه.pdf"),
        )
    }

    @Test
    fun `does not combine collection documents with different layouts`() {
        val landscape = detector.detect("كشف التحصيل مع تفصيل متأخرات ومبيعات المشتركين")
        val portrait = detector.detect("كشف تحصيل المبالغ التي على المشتركين")

        assertNotEquals(landscape?.id, portrait?.id)
        assertEquals(
            "collection_detail_landscape",
            landscape?.id,
        )
        assertEquals(
            "collection_amounts_portrait",
            portrait?.id,
        )
    }

    @Test
    fun `does not infer an unknown report as a known template`() {
        assertNull(detector.detect("مستند غير معروف"))
    }

    @Test
    fun `payment report has no phone column while other samples do`() {
        assertNull(PaymentDetailsTemplate.phoneColumnId)
        assertTrue(
            PdfTemplateCatalog.all
                .filterNot { it == PaymentDetailsTemplate }
                .all { it.phoneColumnId != null },
        )
    }

    @Test
    fun `every report has an independent id and column schema`() {
        assertEquals(
            PdfTemplateCatalog.all.size,
            PdfTemplateCatalog.all.map { it.id }.distinct().size,
        )
        assertTrue(PdfTemplateCatalog.all.all { it.columns.isNotEmpty() })
    }
}
