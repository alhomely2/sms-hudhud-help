package com.hudhud.app.ui.mapping

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hudhud.app.R
import com.hudhud.app.data.ai.ColumnAnalyzer
import com.hudhud.app.data.ai.TemplateMatcher
import com.hudhud.app.data.document.ExcelSchemaExtractor
import com.hudhud.app.data.local.ImportBatchEntity
import com.hudhud.app.data.repository.ImportBatchRepository
import com.hudhud.app.data.repository.TemplateRepository
import com.hudhud.app.databinding.ActivityColumnMappingBinding
import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.FileSchema
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.TemplateMatchResult
import com.hudhud.app.domain.template.ColumnTemplateMapper
import com.hudhud.app.ui.preview.MessagePreviewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for mapping template variables to file columns.
 * Handles both automatic AI suggestions and manual mapping.
 */
class ColumnMappingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColumnMappingBinding
    private lateinit var importBatchRepository: ImportBatchRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var schemaExtractor: ExcelSchemaExtractor
    private lateinit var columnAnalyzer: ColumnAnalyzer
    private lateinit var templateMatcher: TemplateMatcher
    private lateinit var columnTemplateMapper: ColumnTemplateMapper

    private var batchId: Long = 0
    private var fileUri: String = ""
    private var importMode: String = ImportBatchEntity.MODE_RAW_DATA
    
    private var fileSchema: FileSchema? = null
    private var availableTemplates: List<SavedTemplate> = emptyList()
    private var selectedTemplate: SavedTemplate? = null
    private var templateMatchResults: List<TemplateMatchResult> = emptyList()
    
    // Current mappings
    private var phoneColumn: String = ""
    private var variableMappings: Map<String, String> = emptyMap()
    
    // Column lists
    private var columnNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityColumnMappingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get extras
        batchId = intent.getLongExtra(EXTRA_BATCH_ID, 0)
        fileUri = intent.getStringExtra(EXTRA_FILE_URI) ?: ""
        importMode = intent.getStringExtra(EXTRA_IMPORT_MODE) ?: ImportBatchEntity.MODE_RAW_DATA

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
        columnAnalyzer = ColumnAnalyzer()
        templateMatcher = TemplateMatcher()
        columnTemplateMapper = ColumnTemplateMapper()

        setupUI()
        loadData()
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener { finish() }

        // Template selection
        binding.templateSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position <= templateMatchResults.size) {
                    selectedTemplate = templateMatchResults[position - 1].template
                    applyAutoMapping()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        // Phone column selection
        binding.phoneColumnSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < columnNames.size) {
                    phoneColumn = columnNames[position]
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        // Continue button
        binding.continueButton.setOnClickListener {
            saveMappingAndProceed()
        }

        // Auto-map button
        binding.autoMapButton.setOnClickListener {
            applyAutoMapping()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                // Load file schema
                fileSchema = withContext(Dispatchers.IO) {
                    schemaExtractor.extractSchema(Uri.parse(fileUri))
                }

                // Get column names
                columnNames = fileSchema?.columns?.map { it.name } ?: emptyList()
                
                // Setup column spinners
                setupColumnSpinners()

                // For RAW_DATA mode, load templates
                if (importMode == ImportBatchEntity.MODE_RAW_DATA) {
                    loadTemplates()
                } else {
                    // For READY_TEXT, just show column mapping
                    showReadyTextMapping()
                }

            } catch (e: Exception) {
                Toast.makeText(this@ColumnMappingActivity, R.string.mapping_load_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupColumnSpinners() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, columnNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.phoneColumnSpinner.adapter = adapter
        binding.messageColumnSpinner.adapter = adapter

        // Auto-select phone column if detected
        val phoneColumn = fileSchema?.columns?.find { it.type == com.hudhud.app.domain.model.ColumnType.PHONE }
        phoneColumn?.let {
            val index = columnNames.indexOf(it.name)
            if (index >= 0) {
                binding.phoneColumnSpinner.setSelection(index)
            }
        }
    }

    private fun loadTemplates() {
        lifecycleScope.launch {
            try {
                availableTemplates = templateRepository.observeAll().first()
                
                if (availableTemplates.isEmpty()) {
                    showNoTemplatesMessage()
                    return@launch
                }

                // Find matching templates
                fileSchema?.let { schema ->
                    templateMatchResults = templateMatcher.findBestTemplates(
                        schema.columns,
                        availableTemplates
                    )
                }

                displayTemplateSuggestions()

            } catch (e: Exception) {
                Toast.makeText(this@ColumnMappingActivity, R.string.mapping_template_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayTemplateSuggestions() {
        val templateNames = mutableListOf(getString(R.string.mapping_select_template))
        templateNames.addAll(templateMatchResults.map { 
            "${it.template.name} (${it.matchPercent})" 
        })

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, templateNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.templateSpinner.adapter = adapter

        // Show top suggestion if high confidence
        if (templateMatchResults.isNotEmpty() && templateMatchResults.first().score > 0.7f) {
            binding.templateSpinner.setSelection(1)
            selectedTemplate = templateMatchResults.first().template
            applyAutoMapping()
        }
    }

    private fun applyAutoMapping() {
        selectedTemplate?.let { template ->
            fileSchema?.let { schema ->
                val mappingResult = columnTemplateMapper.createAutoMapping(template, schema.columns)
                
                // Update phone column
                mappingResult.phoneColumn?.let { phone ->
                    val index = columnNames.indexOf(phone)
                    if (index >= 0) {
                        binding.phoneColumnSpinner.setSelection(index)
                    }
                }

                // Update variable mappings display
                variableMappings = mappingResult.variableMappings
                displayVariableMappings(mappingResult)

                // Show AI suggestions
                if (mappingResult.confidence < 0.8f) {
                    binding.aiSuggestionText.visibility = View.VISIBLE
                    binding.aiSuggestionText.text = getString(
                        R.string.mapping_ai_confidence,
                        (mappingResult.confidence * 100).toInt()
                    )
                } else {
                    binding.aiSuggestionText.visibility = View.GONE
                }
            }
        }
    }

    private fun displayVariableMappings(mappingResult: com.hudhud.app.domain.model.MappingResult) {
        val mappingsText = StringBuilder()
        mappingResult.variableMappings.forEach { (variable, column) ->
            mappingsText.append("$variable → $column\n")
        }
        
        if (mappingsText.isEmpty()) {
            binding.mappingsText.text = getString(R.string.mapping_no_variables)
        } else {
            binding.mappingsText.text = mappingsText.toString()
        }
    }

    private fun showReadyTextMapping() {
        binding.templateSection.visibility = View.GONE
        binding.autoMapButton.visibility = View.GONE
        binding.instructionsText.text = getString(R.string.mapping_ready_text_instructions)
    }

    private fun showNoTemplatesMessage() {
        binding.templateSpinner.adapter = ArrayAdapter(
            this, 
            android.R.layout.simple_spinner_item, 
            listOf(getString(R.string.mapping_no_templates))
        )
        Toast.makeText(this, R.string.mapping_create_template_first, Toast.LENGTH_LONG).show()
    }

    private fun saveMappingAndProceed() {
        // Validate
        if (importMode == ImportBatchEntity.MODE_RAW_DATA && selectedTemplate == null) {
            Toast.makeText(this, R.string.mapping_select_template_first, Toast.LENGTH_SHORT).show()
            return
        }

        if (phoneColumn.isEmpty()) {
            Toast.makeText(this, R.string.mapping_select_phone_first, Toast.LENGTH_SHORT).show()
            return
        }

        // Save mapping
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    importBatchRepository.saveMapping(
                        batchId = batchId,
                        phoneColumn = phoneColumn,
                        variableMappings = variableMappings,
                    )
                }

                // Navigate to preview
                val intent = Intent(this@ColumnMappingActivity, MessagePreviewActivity::class.java).apply {
                    putExtra(MessagePreviewActivity.EXTRA_BATCH_ID, batchId)
                    putExtra(MessagePreviewActivity.EXTRA_FILE_URI, fileUri)
                    putExtra(MessagePreviewActivity.EXTRA_IMPORT_MODE, importMode)
                    selectedTemplate?.let {
                        putExtra(MessagePreviewActivity.EXTRA_TEMPLATE_ID, it.id)
                    }
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@ColumnMappingActivity, R.string.mapping_save_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_BATCH_ID = "batch_id"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_IMPORT_MODE = "import_mode"
    }
}