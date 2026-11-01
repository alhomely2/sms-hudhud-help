package com.hudhud.app.domain.document

object CollectionDetailLandscapeTemplate : PdfReportTemplate {
    override val id = "collection_detail_landscape"
    override val displayName = "كشف تحصيل تفصيلي - أفقي"
    override val signature = "كشف التحصيل مع تفصيل"
    override val phoneColumnId = "phone"
    override val columns = listOf(
        PdfColumnDefinition("row_number", "م"),
        PdfColumnDefinition("subscriber_number", "رقم المشترك"),
        PdfColumnDefinition("subscriber_name", "اسم المشترك"),
        PdfColumnDefinition("address", "العنوان"),
        PdfColumnDefinition("route_number", "رقم خط السير"),
        PdfColumnDefinition("phone", "رقم الهاتف"),
        PdfColumnDefinition("meter_number", "رقم العداد"),
        PdfColumnDefinition("current_consumption", "الاستهلاك الحالي"),
        PdfColumnDefinition("monthly_consumption", "الاستهلاك الشهري"),
        PdfColumnDefinition("consumption_value", "قيمة الاستهلاك"),
        PdfColumnDefinition("previous_balance", "الرصيد السابق"),
        PdfColumnDefinition("current_payment", "التسديد الحالي"),
        PdfColumnDefinition("adjustments", "التسويات"),
        PdfColumnDefinition("arrears", "المتأخرات"),
        PdfColumnDefinition("monthly_sales", "المبيع الشهري"),
        PdfColumnDefinition("service_fees", "رسوم الخدمات"),
        PdfColumnDefinition("total_payments", "إجمالي التسديدات"),
        PdfColumnDefinition("current_balance", "الرصيد الحالي"),
        PdfColumnDefinition("evaluation", "التقييم"),
    )
}
