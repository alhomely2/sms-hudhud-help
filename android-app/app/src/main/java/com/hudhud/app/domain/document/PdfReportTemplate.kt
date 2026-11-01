package com.hudhud.app.domain.document

interface PdfReportTemplate {
    val id: String
    val displayName: String
    val signature: String
    val columns: List<PdfColumnDefinition>
    val phoneColumnId: String?
}

object PdfTemplateCatalog {
    val all: List<PdfReportTemplate>
        get() = listOf(
            MonthlyInvoiceTemplate,
            CollectionDetailLandscapeTemplate,
            CollectionAmountsPortraitTemplate,
            PaymentDetailsTemplate,
            DisconnectionReportTemplate,
        )
}
