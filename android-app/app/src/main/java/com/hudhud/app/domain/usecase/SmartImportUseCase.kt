package com.hudhud.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.hudhud.app.data.ai.AutoLinker
import com.hudhud.app.data.ai.BatchSizeAnalyzer
import com.hudhud.app.data.ai.ColumnAnalyzer
import com.hudhud.app.data.ai.MessageValidator
import com.hudhud.app.data.ai.MostCommonValueFinder
import com.hudhud.app.data.ai.PreferenceLearner
import com.hudhud.app.data.ai.SmartWarnings
import com.hudhud.app.data.ai.TemplateMatcher
import com.hudhud.app.data.document.ExcelSchemaExtractor
import com.hudhud.app.data.document.FileValidator
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.ImportBatchEntity
import com.hudhud.app.data.repository.ImportBatchRepository
import com.hudhud.app.data.repository.TemplateRepository
import com.hudhud.app.domain.model.AnalysisStatistics
import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.FileSchema
import com.hudhud.app.domain.model.MappingResult
import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.SmartAnalysisResult
import com.hudhud.app.domain.model.TemplateMatchResult
import com.hudhud.app.domain.template.ColumnTemplateMapper
import com.hudhud.app.domain.template.MessageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * UseCase لتنسيق عملية الاستيراد الذكية الكاملة.
 * يجمع بين جميع مكونات الذكاء الاصطناعي والمعالجة.
 */
class SmartImportUseCase(context: Context) {

    // Components
    private val database = AppDatabase.getInstance(context)
    private val importBatchRepository = ImportBatchRepository(database)
    private val templateRepository = TemplateRepository(database)
    
    private val fileValidator = FileValidator(context)
    private val schemaExtractor = ExcelSchemaExtractor(context)
    private val columnAnalyzer = ColumnAnalyzer()
    private val templateMatcher = TemplateMatcher()
    private val columnTemplateMapper = ColumnTemplateMapper()
    private val messageGenerator = MessageGenerator()
    private val messageValidator = MessageValidator()
    private val smartWarnings = SmartWarnings()
    private val batchSizeAnalyzer = BatchSizeAnalyzer()
    private val preferenceLearner = PreferenceLearner(context)
    private val mostCommonValueFinder = MostCommonValueFinder()

    /**
     * الخطوة 1: التحقق من الملف
     */
    suspend fun validateFile(uri: Uri): FileValidationResult = withContext(Dispatchers.IO) {
        val validation = fileValidator.validate(uri)
        
        FileValidationResult(
            isValid = validation.isValid,
            fileName = validation.fileName,
            fileSize = validation.fileSize,
            extension = validation.extension,
            error = validation.error,
        )
    }

    /**
     * الخطوة 2: استخراج مخطط الملف
     */
    suspend fun extractFileSchema(uri: Uri): FileSchema = withContext(Dispatchers.IO) {
        schemaExtractor.extractSchema(uri)
    }

    /**
     * الخطوة 3: تحليل الأعمدة تلقائياً
     */
    fun analyzeColumns(columns: List<String>, sampleValues: Map<String, List<String>>): List<ColumnInfo> {
        return columns.map { columnName ->
            columnAnalyzer.analyzeColumn(columnName, sampleValues[columnName] ?: emptyList())
        }
    }

    /**
     * الخطوة 4: اقتراح القوالب المناسبة
     */
    suspend fun suggestTemplates(
        fileColumns: List<ColumnInfo>,
    ): List<TemplateMatchResult> = withContext(Dispatchers.IO) {
        val templates = templateRepository.observeAll().first()
        val usageHistory = preferenceLearner.getRecentTemplates().associateWith { id ->
            preferenceLearner.getTemplateUsageCount(id)
        }
        templateMatcher.findBestTemplates(fileColumns, templates, usageHistory)
    }

    /**
     * الخطوة 5: إنشاء ربط تلقائي
     */
    fun createAutoMapping(
        template: SavedTemplate,
        fileColumns: List<ColumnInfo>,
    ): MappingResult {
        val result = columnTemplateMapper.createAutoMapping(template, fileColumns)
        
        // تعلم من القرار
        preferenceLearner.recordTemplateSelection(template.id, template.name)
        if (result.phoneColumn != null) {
            preferenceLearner.recordPhoneColumnSelection(
                fileType = "excel",
                columnName = result.phoneColumn
            )
        }
        result.variableMappings.forEach { (variable, column) ->
            preferenceLearner.recordVariableColumnSelection("excel", variable, column)
        }
        
        return result
    }

    /**
     * الخطوة 6: توليد الرسائل
     */
    fun generateMessages(
        template: SavedTemplate,
        dataRows: List<Map<String, String>>,
        phoneColumn: String,
        variableMappings: Map<String, String>,
        useSmartFill: Boolean = false,
    ): List<MessageDraft> {
        return if (useSmartFill) {
            messageGenerator.generateMessagesWithSmartFill(
                template = template,
                dataRows = dataRows,
                phoneColumn = phoneColumn,
                variableMappings = variableMappings,
                useSmartFill = true,
            )
        } else {
            messageGenerator.generateMessages(
                template = template,
                dataRows = dataRows,
                phoneColumn = phoneColumn,
                variableMappings = variableMappings,
            )
        }
    }

    /**
     * الخطوة 7: التحقق من الرسائل
     */
    fun validateMessages(messages: List<MessageDraft>): SmartAnalysisResult {
        return messageValidator.validateMessages(messages)
    }

    /**
     * الخطوة 8: توليد التحذيرات
     */
    fun generateWarnings(messages: List<MessageDraft>): List<com.hudhud.app.domain.model.SmartSuggestion> {
        val statistics = messageValidator.validateMessages(messages).statistics
        return smartWarnings.generateWarnings(messages, statistics)
    }

    /**
     * الخطوة 9: تحليل حجم الدفعة
     */
    fun analyzeBatchSize(messages: List<MessageDraft>): BatchSizeAnalyzer.BatchAnalysis {
        return batchSizeAnalyzer.analyze(messages)
    }

    /**
     * حفظ الدفعة والربط
     */
    suspend fun saveBatch(
        name: String,
        fileName: String,
        fileUri: String,
        importMode: String,
        template: SavedTemplate?,
        phoneColumn: String,
        variableMappings: Map<String, String>,
    ): Long = withContext(Dispatchers.IO) {
        // إنشاء الدفعة
        val batchId = importBatchRepository.createBatch(
            name = name,
            fileName = fileName,
            fileUri = fileUri,
            importMode = importMode,
            templateId = template?.id,
            templateName = template?.name,
            templateBodySnapshot = template?.body,
            templateVariablesSnapshot = template?.let {
                com.hudhud.app.domain.template.VariableExtractor().extractVariables(it.body).joinToString(",")
            },
        )

        // حفظ الربط
        importBatchRepository.saveMapping(
            batchId = batchId,
            phoneColumn = phoneColumn,
            variableMappings = variableMappings,
        )

        // حفظ قرار الربط للتعلم
        template?.let {
            preferenceLearner.recordMappingDecision(it.id, variableMappings)
        }

        batchId
    }

    /**
     * الحصول على إحصائيات الدفعة
     */
    suspend fun getBatchStatistics(batchId: Long): BatchStatistics? = withContext(Dispatchers.IO) {
        val batch = importBatchRepository.getBatchById(batchId) ?: return@withContext null
        
        BatchStatistics(
            batchId = batch.id,
            name = batch.name,
            fileName = batch.fileName,
            importMode = batch.importMode,
            totalRows = batch.totalRows,
            validRows = batch.validRows,
            errorRows = batch.errorRows,
            status = batch.status,
            createdAt = batch.createdAt,
        )
    }

    /**
     * Result classes
     */
    data class FileValidationResult(
        val isValid: Boolean,
        val fileName: String?,
        val fileSize: Long?,
        val extension: String?,
        val error: String?,
    )

    data class BatchStatistics(
        val batchId: Long,
        val name: String,
        val fileName: String,
        val importMode: String,
        val totalRows: Int,
        val validRows: Int,
        val errorRows: Int,
        val status: String,
        val createdAt: Long,
    )
}