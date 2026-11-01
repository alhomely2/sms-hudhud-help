package com.hudhud.app.ui.preview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hudhud.app.R
import com.hudhud.app.data.ai.BatchSizeAnalyzer
import com.hudhud.app.data.ai.MessageValidator
import com.hudhud.app.data.ai.SmartWarnings
import com.hudhud.app.data.document.ExcelSchemaExtractor
import com.hudhud.app.data.local.ImportBatchEntity
import com.hudhud.app.data.repository.ImportBatchRepository
import com.hudhud.app.data.repository.TemplateRepository
import com.hudhud.app.databinding.ActivityMessagePreviewBinding
import com.hudhud.app.domain.model.AnalysisStatistics
import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.SmartSuggestion
import com.hudhud.app.domain.template.MessageGenerator
import com.hudhud.app.ui.home.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory

/**
 * Activity for previewing generated messages before sending.
 * Shows message list, validation results, and warnings.
 */
class MessagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessagePreviewBinding
    private lateinit var importBatchRepository: ImportBatchRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var schemaExtractor: ExcelSchemaExtractor
    private lateinit var messageGenerator: MessageGenerator
    private lateinit var messageValidator: MessageValidator
    private lateinit var smartWarnings: SmartWarnings
    private lateinit var batchSizeAnalyzer: BatchSizeAnalyzer
    private lateinit var messageAdapter: MessagePreviewAdapter

    private var batchId: Long = 0
    private var fileUri: String = ""
    private var importMode: String = ImportBatchEntity.MODE_RAW_DATA
    private var templateId: Long = 0

    private var generatedMessages: List<MessageDraft> = emptyList()
    private var analysisStatistics: AnalysisStatistics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get extras
        batchId = intent.getLongExtra(EXTRA_BATCH_ID, 0)
        fileUri = intent.getStringExtra(EXTRA_FILE_URI) ?: ""
        importMode = intent.getStringExtra(EXTRA_IMPORT_MODE) ?: ImportBatchEntity.MODE_RAW_DATA
        templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, 0)

        if (batchId == 0L || fileUri.isEmpty()) {
            finish()
            return
        }

        // Initialize components
        importBatchRepository = ImportBatchRepository(
            com.hudhud.app.data.local.AppDatabase.getInstance(this)
        )
        templateRepository = TemplateRepository(
            com.hudhud.app.data.local.AppDatabase.getInstance(this)
        )
        schemaExtractor = ExcelSchemaExtractor(this)
        messageGenerator = MessageGenerator()
        messageValidator = MessageValidator()
        smartWarnings = SmartWarnings()
        batchSizeAnalyzer = BatchSizeAnalyzer()

        setupUI()
        generateMessages()
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener { finish() }

        // Setup RecyclerView
        messageAdapter = MessagePreviewAdapter()
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MessagePreviewActivity)
            adapter = messageAdapter
        }

        // Send button
        binding.sendButton.setOnClickListener {
            sendMessages()
        }

        // Edit mapping button
        binding.editMappingButton.setOnClickListener {
            finish() // Go back to mapping
        }

        // Refresh button
        binding.refreshButton.setOnClickListener {
            generateMessages()
        }
    }

    private fun generateMessages() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentCard.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Get batch and mapping
                val batch = withContext(Dispatchers.IO) {
                    importBatchRepository.getBatchById(batchId)
                }

                if (batch == null) {
                    Toast.makeText(this@MessagePreviewActivity, R.string.preview_batch_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                val mappingConfig = withContext(Dispatchers.IO) {
                    importBatchRepository.getMappingConfig(batchId)
                }

                if (mappingConfig == null) {
                    Toast.makeText(this@MessagePreviewActivity, R.string.preview_mapping_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                // Get template if needed
                val template = if (importMode == ImportBatchEntity.MODE_RAW_DATA && templateId > 0) {
                    withContext(Dispatchers.IO) {
                        templateRepository.getById(templateId)
                    }
                } else null

                // Read file data
                val dataRows = withContext(Dispatchers.IO) {
                    readFileData(Uri.parse(fileUri))
                }

                // Generate messages
                if (importMode == ImportBatchEntity.MODE_RAW_DATA && template != null) {
                    generatedMessages = messageGenerator.generateMessages(
                        template = template,
                        dataRows = dataRows,
                        phoneColumn = mappingConfig.phoneColumn,
                        variableMappings = mappingConfig.variableMappings.associate { it.templateVariable to it.fileColumn },
                    )
                } else {
                    // Ready text mode - just use phone and message columns
                    generatedMessages = dataRows.mapIndexed { index, row ->
                        MessageDraft(
                            sourceRow = index + 1,
                            phone = row[mappingConfig.phoneColumn] ?: "",
                            text = row["message"] ?: row.values.firstOrNull() ?: "",
                            errors = if (row[mappingConfig.phoneColumn].isNullOrBlank()) {
                                listOf("Missing phone")
                            } else emptyList()
                        )
                    }
                }

                // Validate messages
                val validationResult = messageValidator.validateMessages(generatedMessages)
                analysisStatistics = validationResult.statistics

                // Display results
                displayResults(validationResult)

            } catch (e: Exception) {
                Toast.makeText(this@MessagePreviewActivity, R.string.preview_generate_error, Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.contentCard.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun readFileData(uri: Uri): List<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                
                // Get headers
                val headers = sheet.firstOrNull()?.let { row ->
                    (0 until row.lastCellNum.toInt()).map { index ->
                        row.getCell(index)?.stringCellValue ?: "Column_$index"
                    }
                } ?: emptyList()

                // Get data rows
                val rows = mutableListOf<Map<String, String>>()
                val iterator = sheet.iterator()
                var isFirstRow = true
                
                while (iterator.hasNext()) {
                    val row = iterator.next()
                    if (isFirstRow) {
                        isFirstRow = false
                        continue // Skip header
                    }
                    
                    val rowData = mutableMapOf<String, String>()
                    headers.forEachIndexed { index, header ->
                        val cell = row.getCell(index)
                        rowData[header] = cell?.stringCellValue ?: ""
                    }
                    rows.add(rowData)
                }
                
                rows
            } ?: emptyList()
        }
    }

    private fun displayResults(validationResult: com.hudhud.app.domain.model.SmartAnalysisResult) {
        // Update adapter
        messageAdapter.submitList(generatedMessages)

        // Update statistics
        val stats = validationResult.statistics
        binding.totalCountText.text = getString(R.string.preview_total, stats.totalCount)
        binding.validCountText.text = getString(R.string.preview_valid, stats.validCount)
        binding.errorCountText.text = getString(R.string.preview_errors, stats.errorCount)

        // Show/hide error count based on errors
        binding.errorCountText.visibility = if (stats.errorCount > 0) View.VISIBLE else View.GONE

        // Progress bar
        val progress = if (stats.totalCount > 0) {
            (stats.validCount * 100 / stats.totalCount)
        } else 0
        binding.progressIndicator.progress = progress

        // Warnings
        val warnings = smartWarnings.generateWarnings(generatedMessages, stats)
        displayWarnings(warnings)

        // Batch analysis
        val batchAnalysis = batchSizeAnalyzer.analyze(generatedMessages)
        binding.batchInfoText.text = getString(
            R.string.preview_batch_info,
            batchAnalysis.category.name,
            batchAnalysis.estimatedTimeMinutes
        )

        // Enable/disable send button
        binding.sendButton.isEnabled = stats.validCount > 0
    }

    private fun displayWarnings(warnings: List<SmartSuggestion>) {
        if (warnings.isEmpty()) {
            binding.warningsCard.visibility = View.GONE
            return
        }

        binding.warningsCard.visibility = View.VISIBLE
        
        val warningText = StringBuilder()
        warnings.take(5).forEach { warning ->
            warningText.append("• ${warning.title}\n")
        }
        
        if (warnings.size > 5) {
            warningText.append("... and ${warnings.size - 5} more")
        }
        
        binding.warningsText.text = warningText.toString()
    }

    private fun sendMessages() {
        if (generatedMessages.isEmpty()) {
            Toast.makeText(this, R.string.preview_generate_error, Toast.LENGTH_SHORT).show()
            return
        }

        binding.sendButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = com.hudhud.app.data.local.AppDatabase.getInstance(this@MessagePreviewActivity)
                    val campaignDao = db.campaignDao()
                    val outgoingRepo = com.hudhud.app.data.local.OutgoingMessageRepository(db.outgoingMessageDao())
                    val batch = importBatchRepository.getBatchById(batchId)

                    val stats = analysisStatistics
                    val validCount = stats?.validCount ?: generatedMessages.count { it.isValid }
                    val errorCount = stats?.errorCount ?: generatedMessages.count { !it.isValid }
                    val totalCount = stats?.totalCount ?: generatedMessages.size

                    val campaignName = batch?.name ?: batch?.fileName ?: getString(R.string.campaign_name, "حملة مستوردة")
                    val campaignStatus = if (errorCount > 0) {
                        com.hudhud.app.data.local.CampaignEntity.STATUS_NEEDS_REVIEW
                    } else {
                        com.hudhud.app.data.local.CampaignEntity.STATUS_READY_LOCAL
                    }

                    val campaignId = campaignDao.insertCampaign(
                        com.hudhud.app.data.local.CampaignEntity(
                            name = campaignName,
                            status = campaignStatus,
                            totalMessages = totalCount,
                            validMessages = validCount,
                            reviewMessages = errorCount,
                            importMode = importMode,
                            batchId = if (batchId > 0) batchId else null,
                        ),
                    )

                    val messageEntities = generatedMessages.map { draft ->
                        com.hudhud.app.data.local.CampaignMessageEntity(
                            campaignId = campaignId,
                            batchId = if (batchId > 0) batchId else null,
                            sourceRow = draft.sourceRow,
                            phone = draft.phone,
                            text = draft.text,
                            status = if (draft.isValid) {
                                com.hudhud.app.data.local.CampaignMessageEntity.STATUS_READY
                            } else {
                                com.hudhud.app.data.local.CampaignMessageEntity.STATUS_NEEDS_REVIEW
                            },
                            errors = draft.errors.joinToString("\n"),
                            sendStatus = com.hudhud.app.data.local.CampaignMessageEntity.SEND_STATUS_PREPARED,
                        )
                    }
                    campaignDao.insertMessages(messageEntities)

                    // Update import batch statistics and completion status
                    if (batchId > 0) {
                        importBatchRepository.updateBatchStatistics(batchId, totalCount, validCount, errorCount)
                        importBatchRepository.updateBatchStatus(batchId, com.hudhud.app.data.local.ImportBatchEntity.STATUS_COMPLETED)
                    }

                    // Enqueue valid messages for transmission
                    val activeProfile = com.hudhud.app.data.local.AppSettingsStore(this@MessagePreviewActivity).getActiveProfile()
                    val validMessages = messageEntities.filter { it.status == com.hudhud.app.data.local.CampaignMessageEntity.STATUS_READY }
                    if (validMessages.isNotEmpty()) {
                        outgoingRepo.queueCampaign(
                            campaignId = campaignId,
                            siteProfileId = activeProfile.id,
                            messages = validMessages,
                        )
                    }
                }

                Toast.makeText(this@MessagePreviewActivity, R.string.ready_campaign_saved, Toast.LENGTH_LONG).show()

                val intent = Intent(this@MessagePreviewActivity, com.hudhud.app.ui.campaigns.CampaignsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                binding.sendButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MessagePreviewActivity, R.string.preview_generate_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_BATCH_ID = "batch_id"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_IMPORT_MODE = "import_mode"
        const val EXTRA_TEMPLATE_ID = "template_id"
    }
}