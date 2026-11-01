package com.hudhud.app.ui.template

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hudhud.app.R
import com.hudhud.app.data.document.DocumentDataExtractor
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.TemplateRepository
import com.hudhud.app.databinding.ActivityTemplateEditorV2Binding
import com.hudhud.app.domain.model.DocumentData
import com.hudhud.app.domain.model.FixedData
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.TemplateField
import com.hudhud.app.domain.template.TemplatePlaceholderParser
import com.hudhud.app.domain.template.TemplateValidationError
import com.hudhud.app.domain.template.TemplateValidator
import com.hudhud.app.ui.shared.SpreadsheetDocumentPicker
import kotlinx.coroutines.launch

/**
 * New template editor with step-by-step workflow:
 * Step 1: Basic info (name, message body, select document)
 * Step 2: Select data from document (variable and fixed data)
 * Step 3: Preview and save
 */
class TemplateEditorV2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityTemplateEditorV2Binding
    private lateinit var repository: TemplateRepository
    private lateinit var documentExtractor: DocumentDataExtractor

    private var templateId: Long? = null
    private var loadedTemplate: SavedTemplate? = null

    // Current step (1, 2, or 3)
    private var currentStep = 1

    // Document data
    private var selectedDocumentUri: Uri? = null
    private var selectedFileName: String = ""
    private var documentData: DocumentData? = null

    // User selections
    private val selectedFields = mutableListOf<TemplateField>()
    private val fixedDataList = mutableListOf<FixedData>()

    // Initial values for dirty checking
    private var initialName = ""
    private var initialBody = ""
    private var saved = false

    // Adapters
    private lateinit var dataFieldsAdapter: DataFieldsAdapter
    private lateinit var fixedDataAdapter: FixedDataAdapter

    // Document picker
    private val documentPicker = SpreadsheetDocumentPicker(
        activity = this,
        onPicked = { uri, fileName -> onDocumentSelected(uri, fileName) },
        onFailure = { message -> showError(message) }
    )

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                saved -> finish()
                !isDirty() -> finish()
                currentStep > 1 -> goToStep(currentStep - 1)
                else -> showDiscardDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateEditorV2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TemplateRepository(AppDatabase.getInstance(this).templateDao())
        documentExtractor = DocumentDataExtractor(this)

        templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, 0L).takeIf { it > 0L }

        setupUI()
        setupAdapters()
        setupListeners()

        onBackPressedDispatcher.addCallback(this, backCallback)

        templateId?.let { loadTemplate(it) }
        updateStepUI()
    }

    private fun setupUI() {
        val isEditing = templateId != null
        val title = getString(
            if (isEditing) R.string.edit_template_path else R.string.create_template_path,
        )
        binding.editorTitle.text = title
    }

    private fun setupAdapters() {
        // Data fields adapter (for variable columns)
        dataFieldsAdapter = DataFieldsAdapter(
            onFieldSelected = { field -> onFieldSelected(field) },
            onAddField = { field -> addFieldToTemplate(field) }
        )

        binding.dataFieldsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TemplateEditorV2Activity)
            adapter = dataFieldsAdapter
        }

        // Fixed data adapter
        fixedDataAdapter = FixedDataAdapter(
            onFixedDataChanged = { index, fixedData ->
                if (index < fixedDataList.size) {
                    fixedDataList[index] = fixedData
                }
            },
            onRemoveFixedData = { index ->
                if (index < fixedDataList.size) {
                    fixedDataList.removeAt(index)
                    fixedDataAdapter.submitList(fixedDataList.toList())
                }
            }
        )

        binding.fixedDataRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TemplateEditorV2Activity)
            adapter = fixedDataAdapter
        }
    }

    private fun setupListeners() {
        binding.templateNameInput.doAfterTextChanged {
            validateStep1()
            updateMessageBodyPreview()
        }
        binding.templateBodyInput.doAfterTextChanged {
            validateStep1()
            updateMessageBodyPreview()
        }

        binding.selectDocumentButton.setOnClickListener { documentPicker.launch() }
        binding.changeDocumentButton.setOnClickListener { documentPicker.launch() }

        // Keep the editing surface visible while the document fields are shown below it.
        binding.nextToStep2Button.visibility = View.GONE

        // Step 2 listeners
        binding.addFixedDataButton.setOnClickListener { addFixedData() }
        binding.backToStep1Button.setOnClickListener { goToStep(1) }
        binding.nextToStep3Button.setOnClickListener { goToStep(3) }

        // Step 3 listeners
        binding.backToStep2Button.setOnClickListener { goToStep(2) }
        binding.saveTemplateButton.setOnClickListener { saveTemplate() }
    }

    private fun onDocumentSelected(uri: Uri, fileName: String) {
        selectedDocumentUri = uri
        selectedFileName = fileName

        binding.loadingContainer.isVisible = true
        binding.step1Container.isVisible = false

        lifecycleScope.launch {
            try {
                documentData = documentExtractor.extractDocument(uri, fileName)
                
                binding.loadingContainer.isVisible = false
                binding.step1Container.isVisible = true

                if (documentData?.isValid == true) {
                    showDocumentSelected()
                    updateDataFields()
                    currentStep = 2
                    updateStepUI()
                } else {
                    showError(documentData?.error ?: getString(R.string.template_import_failed))
                }
            } catch (e: Exception) {
                binding.loadingContainer.isVisible = false
                binding.step1Container.isVisible = true
                showError(getString(R.string.template_import_failed))
            }
        }
    }

    private fun showDocumentSelected() {
        binding.selectDocumentButton.isVisible = false
        binding.selectedDocumentCard.isVisible = false
        binding.selectedFileName.text = selectedFileName
        binding.selectedFileInfo.text = documentData?.getInfoString() ?: ""
        validateStep1()
    }

    private fun updateDataFields() {
        documentData?.let { data ->
            dataFieldsAdapter.submitList(data.allColumns, data.sampleData)
            fixedDataAdapter.submitList(fixedDataList.toList())
        }
    }

    private fun validateStep1() {
        // Validation is handled by auto-advance
        // No need to enable/disable button since we're auto-advancing
    }

    private fun onFieldSelected(field: com.hudhud.app.domain.model.ColumnInfo) {
        if (selectedFields.any { it.name == field.name }) {
            Toast.makeText(this, getString(R.string.template_variable_name_duplicate), Toast.LENGTH_SHORT).show()
            return
        }

        addFieldToTemplate(field)
    }

    private fun addFieldToTemplate(field: com.hudhud.app.domain.model.ColumnInfo) {
        // Check if already added
        if (selectedFields.any { it.name == field.name }) {
            Toast.makeText(this, getString(R.string.template_variable_name_duplicate), Toast.LENGTH_SHORT).show()
            return
        }

        // Show dialog to add field with options
        showAddFieldDialog(field)
    }

    private fun showAddFieldDialog(field: com.hudhud.app.domain.model.ColumnInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_field, null)
        val beforeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.beforeTextInput)
        val afterInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.afterTextInput)
        val newLineCheck = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.newLineCheckBox)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add) + " " + field.displayName.ifBlank { field.name })
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                val templateField = TemplateField(
                    name = field.name,
                    beforeText = beforeInput.text?.toString() ?: "",
                    afterText = afterInput.text?.toString() ?: "",
                    newLineAfter = newLineCheck.isChecked
                )
                selectedFields.add(templateField)
                insertFieldIntoMessage(templateField)
                Toast.makeText(this, getString(R.string.field_added, field.displayName.ifBlank { field.name }), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun insertFieldIntoMessage(field: TemplateField) {
        val input = binding.templateBodyInput
        val token = "{{${field.name}}}"
        val current = input.text ?: return
        val start = input.selectionStart.coerceIn(0, current.length)
        
        // Add text before if specified
        val insertText = if (field.beforeText.isNotEmpty()) {
            "${field.beforeText}$token"
        } else {
            token
        }
        
        val end = input.selectionEnd.coerceIn(start, current.length)
        current.replace(start, end, insertText)
        
        // Add new line after if specified
        if (field.newLineAfter) {
            current.insert((start + insertText.length).coerceAtMost(current.length), "\n")
        }
        
        input.setSelection(current.length)
        input.requestFocus()
        
        // Update the selected fields list in adapter
        dataFieldsAdapter.updateSelectedFields(selectedFields.map { it.name })
    }

    private fun addFixedData() {
        val newFixed = FixedData(name = "", value = "")
        fixedDataList.add(newFixed)
        fixedDataAdapter.submitList(fixedDataList.toList())
    }

    private fun updateMessageBodyPreview() {
        // Update preview in step 3 if we're there
        if (currentStep == 3) {
            updatePreview()
        }
    }

    private fun goToStep(step: Int) {
        if (step < 1 || step > 3) return
        
        // Validate before moving to next step
        if (step > currentStep && !validateCurrentStep()) {
            return
        }

        currentStep = step
        updateStepUI()
        
        if (step == 3) {
            updatePreview()
        }
    }

    private fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            1 -> {
                val name = binding.templateNameInput.text?.toString()?.trim() ?: ""
                val body = binding.templateBodyInput.text?.toString()?.trim() ?: ""
                
                if (name.isEmpty()) {
                    binding.nameLayout.error = getString(R.string.enter_template_name)
                    return false
                }
                if (body.isEmpty()) {
                    binding.bodyLayout.error = getString(R.string.enter_template_body)
                    return false
                }
                if (selectedDocumentUri == null) {
                    showError(getString(R.string.select_document_first))
                    return false
                }
                true
            }
            2 -> true
            3 -> true
            else -> true
        }
    }

    private fun updateStepUI() {
        // Update step indicators
        binding.step1Indicator.setBackgroundResource(
            if (currentStep >= 1) R.drawable.bg_step_active else R.drawable.bg_step_inactive
        )
        binding.step2Indicator.setBackgroundResource(
            if (currentStep >= 2) R.drawable.bg_step_active else R.drawable.bg_step_inactive
        )
        binding.step3Indicator.setBackgroundResource(
            if (currentStep >= 3) R.drawable.bg_step_active else R.drawable.bg_step_inactive
        )

        // Update step labels
        val activeColor = getColor(R.color.hudhud_primary)
        val inactiveColor = getColor(R.color.hudhud_on_surface_muted)

        binding.step1Label.setTextColor(if (currentStep >= 1) activeColor else inactiveColor)
        binding.step2Label.setTextColor(if (currentStep >= 2) activeColor else inactiveColor)
        binding.step3Label.setTextColor(if (currentStep >= 3) activeColor else inactiveColor)

        // Keep the template name/body visible while the extracted document columns are
        // shown below it. The document picker and selected file summary should remain
        // hidden once a valid document has been loaded, so the authoring area stays in view.
        binding.step1Container.isVisible = true
        binding.step2Container.isVisible = currentStep >= 2 && documentData?.isValid == true
        binding.step3Container.isVisible = currentStep >= 3
    }

    private fun updatePreview() {
        val name = binding.templateNameInput.text?.toString()?.trim() ?: ""
        val body = binding.templateBodyInput.text?.toString()?.trim() ?: ""

        binding.previewTemplateName.text = name
        binding.previewMessageBody.text = body

        // Show selected fields chips
        binding.selectedFieldsChipGroup.removeAllViews()
        selectedFields.forEach { field ->
            val chip = Chip(this).apply {
                text = field.name
                isCheckable = false
            }
            binding.selectedFieldsChipGroup.addView(chip)
        }
        
        binding.selectedFieldsChipGroup.isVisible = selectedFields.isNotEmpty()

        // Show fixed values chips
        binding.fixedValuesChipGroup.removeAllViews()
        fixedDataList.filter { it.name.isNotEmpty() && it.value.isNotEmpty() }.forEach { fixed ->
            val chip = Chip(this).apply {
                text = "${fixed.name}: ${fixed.value}"
                isCheckable = false
            }
            binding.fixedValuesChipGroup.addView(chip)
        }
        
        val hasFixedData = fixedDataList.any { it.name.isNotEmpty() && it.value.isNotEmpty() }
        binding.fixedValuesLabel.isVisible = hasFixedData
        binding.fixedValuesChipGroup.isVisible = hasFixedData
    }

    private fun isDirty(): Boolean {
        val name = binding.templateNameInput.text?.toString()?.trim() ?: ""
        val body = binding.templateBodyInput.text?.toString()?.trim() ?: ""
        return name != initialName || body != initialBody
    }

    private fun showDiscardDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_unsaved_title)
            .setMessage(R.string.template_unsaved_body)
            .setNegativeButton(R.string.template_unsaved_keep, null)
            .setPositiveButton(R.string.template_unsaved_discard) { _, _ -> finish() }
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun loadTemplate(id: Long) {
        lifecycleScope.launch {
            val template = repository.loadTemplate(id)
            if (template == null) {
                showError(getString(R.string.template_not_found))
                finish()
                return@launch
            }

            loadedTemplate = template
            initialName = template.name
            initialBody = template.body

            binding.templateNameInput.setText(template.name)
            binding.templateBodyInput.setText(template.body)
            
            selectedFields.clear()
            selectedFields.addAll(template.selectedFields)
            
            fixedDataList.clear()
            fixedDataList.addAll(template.fixedData)

            // Load document info if available
            if (template.sourceFileName.isNotEmpty()) {
                selectedFileName = template.sourceFileName
                binding.selectedDocumentCard.isVisible = true
                binding.selectedFileName.text = template.sourceFileName
            }

            validateStep1()
            updatePreview()
        }
    }

    private fun saveTemplate() {
        val name = binding.templateNameInput.text?.toString()?.trim() ?: ""
        val body = binding.templateBodyInput.text?.toString()?.trim() ?: ""
        
        val validation = TemplateValidator.validate(name, body)
        
        if (!validation.isValid) {
            if (validation.has(TemplateValidationError.NAME_REQUIRED)) {
                binding.nameLayout.error = getString(R.string.required_fields)
            }
            if (validation.has(TemplateValidationError.BODY_REQUIRED)) {
                binding.bodyLayout.error = getString(R.string.required_fields)
            }
            return
        }

        binding.saveTemplateButton.isEnabled = false

        lifecycleScope.launch {
            try {
                // Filter out empty fixed data
                val validFixedData = fixedDataList.filter { it.name.isNotEmpty() && it.value.isNotEmpty() }
                
                // Build variable columns map from selected fields
                val variableColumns = selectedFields.associate { field -> 
                    "{{${field.name}}}" to field.name
                }

                if (templateId == null) {
                    // Create new template
                    repository.createTemplate(
                        name = name,
                        body = body,
                        phoneColumn = documentData?.variableColumns?.firstOrNull { 
                            it.type == com.hudhud.app.domain.model.ColumnType.PHONE 
                        }?.name ?: "",
                        variableColumns = variableColumns,
                        fixedValues = validFixedData.associate { "{{${it.name}}}" to it.value },
                        selectedFields = selectedFields,
                        fixedData = validFixedData,
                        sourceFileName = selectedFileName,
                        documentHeaders = documentData?.getAllColumnNames() ?: emptyList(),
                        documentSample = documentData?.sampleData ?: emptyMap()
                    )
                } else {
                    // Update existing template
                    repository.updateTemplate(
                        templateId = templateId!!,
                        name = name,
                        body = body,
                        phoneColumn = documentData?.variableColumns?.firstOrNull { 
                            it.type == com.hudhud.app.domain.model.ColumnType.PHONE 
                        }?.name ?: "",
                        variableColumns = variableColumns,
                        fixedValues = validFixedData.associate { "{{${it.name}}}" to it.value },
                        selectedFields = selectedFields,
                        fixedData = validFixedData,
                        sourceFileName = selectedFileName,
                        documentHeaders = documentData?.getAllColumnNames() ?: emptyList(),
                        documentSample = documentData?.sampleData ?: emptyMap()
                    )
                }

                saved = true
                setResult(RESULT_OK, Intent().putExtra(EXTRA_WAS_UPDATE, templateId != null))
                finish()

            } catch (e: Exception) {
                binding.saveTemplateButton.isEnabled = true
                showError(e.message ?: getString(R.string.required_fields))
            }
        }
    }

    companion object {
        private const val EXTRA_TEMPLATE_ID = "com.hudhud.app.extra.TEMPLATE_ID"
        const val EXTRA_WAS_UPDATE = "com.hudhud.app.extra.WAS_UPDATE"

        fun intent(context: Context, templateId: Long? = null): Intent =
            Intent(context, TemplateEditorV2Activity::class.java).apply {
                if (templateId != null) putExtra(EXTRA_TEMPLATE_ID, templateId)
            }
    }
}