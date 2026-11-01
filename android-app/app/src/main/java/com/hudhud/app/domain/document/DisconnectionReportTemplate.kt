package com.hudhud.app.domain.document

object DisconnectionReportTemplate : PdfReportTemplate {
    override val id = "disconnection_report"
    override val displayName = "كشف الفصل"
    override val signature = "تقرير بالمشتركين المفعلين"
    override val phoneColumnId = "phone"
    override val columns = listOf(
        PdfColumnDefinition("row_number", "م"),
        PdfColumnDefinition("subscriber_number", "رقم المشترك"),
        PdfColumnDefinition("subscriber_name", "اسم المشترك"),
        PdfColumnDefinition("address", "العنوان"),
        PdfColumnDefinition("neighborhood", "الجوار"),
        PdfColumnDefinition("phone", "رقم الهاتف"),
        PdfColumnDefinition("route_number", "خط السير"),
        PdfColumnDefinition("meter_number", "رقم العداد"),
        PdfColumnDefinition("arrears", "المتأخرات"),
        PdfColumnDefinition("sales", "المبيع"),
        PdfColumnDefinition("total_due", "المبلغ المستحق"),
        PdfColumnDefinition("paid_amount", "المبلغ المدفوع"),
        PdfColumnDefinition("remaining_amount", "المبلغ المتبقي"),
        PdfColumnDefinition("adjustment_count", "عدد التسويات"),
        PdfColumnDefinition("adjustment_amount", "مبلغ التسويات"),
        PdfColumnDefinition("status", "الحالة"),
        PdfColumnDefinition("tariff", "التعرفة"),
    )
}
