package com.hudhud.app.domain.document

object PaymentDetailsTemplate : PdfReportTemplate {
    override val id = "payment_details"
    override val displayName = "كشف تفاصيل التسديد"
    override val signature = "تقرير تفصيل تسديدات المشتركين"
    override val phoneColumnId: String? = null
    override val columns = listOf(
        PdfColumnDefinition("row_number", "م"),
        PdfColumnDefinition("subscriber_number", "رقم المشترك"),
        PdfColumnDefinition("subscriber_name", "اسم المشترك"),
        PdfColumnDefinition("address", "العنوان"),
        PdfColumnDefinition("receipt_number_auto", "رقم السند الآلي"),
        PdfColumnDefinition("receipt_number_manual", "رقم السند اليدوي"),
        PdfColumnDefinition("collector_name", "اسم المتحصل"),
        PdfColumnDefinition("collection_date", "تاريخ التحصيل"),
        PdfColumnDefinition("collected_amount", "المبلغ المتحصل"),
        PdfColumnDefinition("description", "البيان"),
        PdfColumnDefinition("reference_number", "رقم المرجع"),
        PdfColumnDefinition("reference_name", "اسم المرجع"),
        PdfColumnDefinition("type", "النوع"),
    )
}
