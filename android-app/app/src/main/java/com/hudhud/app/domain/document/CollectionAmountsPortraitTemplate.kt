package com.hudhud.app.domain.document

object CollectionAmountsPortraitTemplate : PdfReportTemplate {
    override val id = "collection_amounts_portrait"
    override val displayName = "كشف تحصيل مبالغ - عمودي"
    override val signature = "كشف تحصيل المبالغ التي على المشتركين"
    override val phoneColumnId = "phone"
    override val columns = listOf(
        PdfColumnDefinition("row_number", "م"),
        PdfColumnDefinition("subscriber_number", "رقم المشترك"),
        PdfColumnDefinition("subscriber_name", "اسم المشترك"),
        PdfColumnDefinition("address", "العنوان"),
        PdfColumnDefinition("route_number", "رقم خط السير"),
        PdfColumnDefinition("phone", "رقم الهاتف"),
        PdfColumnDefinition("meter_number", "رقم العداد"),
        PdfColumnDefinition("meter_phase", "طور العداد"),
        PdfColumnDefinition("previous_arrears", "المتأخرات السابقة"),
        PdfColumnDefinition("losses", "الفاقد"),
        PdfColumnDefinition("period_consumption", "استهلاك الفترة"),
        PdfColumnDefinition("period_sales", "مبيع الفترة الحالية"),
        PdfColumnDefinition("total_due", "إجمالي المبلغ المستحق"),
        PdfColumnDefinition("paid_amount", "المبلغ المدفوع"),
        PdfColumnDefinition("tariff", "التعرفة"),
    )
}
