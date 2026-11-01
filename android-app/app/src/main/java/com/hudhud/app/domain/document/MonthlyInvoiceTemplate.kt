package com.hudhud.app.domain.document

object MonthlyInvoiceTemplate : PdfReportTemplate {
    override val id = "monthly_invoice"
    override val displayName = "فاتورة شهرية"
    override val signature = "فاتورة كهرباء"
    override val phoneColumnId = "phone"
    override val columns = listOf(
        PdfColumnDefinition("subscriber_number", "رقم المشترك"),
        PdfColumnDefinition("subscriber_name", "اسم المشترك"),
        PdfColumnDefinition("address", "العنوان"),
        PdfColumnDefinition("phone", "هاتف المشترك"),
        PdfColumnDefinition("meter_number", "رقم العداد"),
        PdfColumnDefinition("route_number", "رقم خط السير"),
        PdfColumnDefinition("tariff", "التعرفة"),
        PdfColumnDefinition("multiplier", "معامل الضرب"),
        PdfColumnDefinition("current_consumption", "الاستهلاك الحالي"),
        PdfColumnDefinition("previous_consumption", "الاستهلاك السابق"),
        PdfColumnDefinition("consumption_value", "قيمة الاستهلاك"),
        PdfColumnDefinition("previous_balance", "الرصيد السابق"),
        PdfColumnDefinition("service_fees", "رسوم الخدمات"),
        PdfColumnDefinition("adjustments", "التسويات"),
        PdfColumnDefinition("losses", "الفاقد"),
        PdfColumnDefinition("arrears", "المتأخرات"),
        PdfColumnDefinition("total_due", "المبلغ المستحق"),
        PdfColumnDefinition("amount_required", "المبلغ المطلوب"),
    )
}
