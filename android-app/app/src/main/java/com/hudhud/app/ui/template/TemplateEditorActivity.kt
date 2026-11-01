package com.hudhud.app.ui.template

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.hudhud.app.R
import com.hudhud.app.data.document.XlsxReadyMessageExtractor
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.TemplateRepository
import com.hudhud.app.databinding.ActivityTemplateEditorBinding
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.template.TemplatePlaceholderParser
import com.hudhud.app.domain.template.TemplateValidationError
import com.hudhud.app.domain.template.TemplateValidator
import com.hudhud.app.ui.shared.SpreadsheetDocumentPicker
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Writes a template body and its placeholders.
 *
 * The same screen creates and edits; when launched with a template id, saving
 * updates that record instead of inserting a new one. A data file is optional:
 * it is only used to import exact spreadsheet header names.
 */
class TemplateEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTemplateEditorBinding
    private lateinit var repository: TemplateRepository
    private val workbookExtractor = XlsxReadyMessageExtractor()

    private var templateId: Long? = null
    private var loadedTemplate: SavedTemplate? = null
    private var importedFields: List<String> = emptyList()
    private var saved = false

    private var initialName = ""
    private var initialBody = ""
    private var initialPhone = ""

    /** Values inserted into every message, keyed by placeholder name. */
    private val fixedValues = linkedMapOf<String, String>()
    private var initialFixedValues: Map<String, String> = emptyMap()

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (saved || !isDirty()) {
                finish()
                return
            }
            MaterialAlertDialogBuilder(this@TemplateEditorActivity)
                .setTitle(R.string.template_unsaved_title)
                .setMessage(R.string.template_unsaved_body)
                .setNegativeButton(R.string.template_unsaved_keep, null)
                .setPositiveButton(R.string.template_unsaved_discard) { _, _ -> finish() }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TemplateRepository(AppDatabase.getInstance(this).templateDao())
        templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, 0L).takeIf { it > 0L }

        val isEditing = templateId != null
        val title = getString(
            if (isEditing) R.string.edit_template_path else R.string.create_template_path,
        )
        this.title = title
        binding.editorTitle.text = title

        val picker = SpreadsheetDocumentPicker(
            activity = this,
            onPicked = ::importFieldsFrom,
            onFailure = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            },
        )
        binding.importFieldsButton.setOnClickListener { picker.launch() }
        binding.addVariableButton.setOnClickListener { showAddVariableDialog() }
        binding.addFixedValueButton.setOnClickListener { showFixedValueDialog() }
        binding.templateBodyInput.doAfterTextChanged { refreshVariables() }
        binding.saveTemplateButton.setOnClickListener { save() }
        onBackPressedDispatcher.addCallback(this, backCallback)

        templateId?.let(::loadTemplate)
    }

    private fun loadTemplate(id: Long) {
        lifecycleScope.launch {
            val template = repository.loadTemplate(id)
            if (template == null) {
                Toast.makeText(
                    this@TemplateEditorActivity,
                    R.string.template_not_found,
                    Toast.LENGTH_LONG,
                ).show()
                finish()
                return@launch
            }
            loadedTemplate = template
            initialName = template.name
            initialBody = template.body
            initialPhone = template.phoneColumn
            initialFixedValues = template.fixedValues
            fixedValues.clear()
            fixedValues.putAll(template.fixedValues)
            binding.templateNameInput.setText(template.name)
            binding.templateBodyInput.setText(template.body)
            binding.phoneColumnInput.setText(template.phoneColumn, false)
            renderFixedValues()
            refreshVariables()
        }
    }

    private fun currentBody(): String = binding.templateBodyInput.text?.toString().orEmpty()

    private fun currentPhone(): String =
        binding.phoneColumnInput.text?.toString()?.trim().orEmpty()

    private fun isDirty(): Boolean =
        binding.templateNameInput.text?.toString().orEmpty() != initialName ||
            currentBody() != initialBody ||
            currentPhone() != initialPhone ||
            fixedValues != initialFixedValues

    private fun refreshVariables() {
        binding.variableChips.removeAllViews()
        val names = TemplatePlaceholderParser.names(currentBody())
        binding.variablesEmptyText.isVisible = names.isEmpty()
        names.forEach { name ->
            binding.variableChips.addView(
                buildChip(name) { insertPlaceholder(name) },
            )
        }
    }

    private fun renderImportedFields() {
        binding.importedFieldsLabel.isVisible = importedFields.isNotEmpty()
        binding.importedFieldsChips.isVisible = importedFields.isNotEmpty()
        binding.importedFieldsChips.removeAllViews()
        importedFields.forEach { name ->
            binding.importedFieldsChips.addView(
                buildChip(name) { insertPlaceholder(name) },
            )
        }
    }

    private fun buildChip(name: String, onClick: () -> Unit): Chip = Chip(this).apply {
        text = name
        isCheckable = false
        isClickable = true
        contentDescription = getString(R.string.template_insert_field, name)
        setOnClickListener { onClick() }
    }

    private fun insertPlaceholder(name: String) {
        val input = binding.templateBodyInput
        val token = TemplatePlaceholderParser.placeholderFor(name)
        val current = input.text ?: return
        val start = input.selectionStart.coerceIn(0, current.length)
        val end = input.selectionEnd.coerceIn(start, current.length)
        current.replace(start, end, token)
        input.setSelection((start + token.length).coerceAtMost(current.length))
        input.requestFocus()
        refreshVariables()
    }

    private fun showAddVariableDialog() {
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.template_variable_name_title)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val input = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        inputLayout.addView(input)

        val container = FrameLayout(this).apply {
            val horizontal = resources.getDimensionPixelSize(R.dimen.space_lg)
            setPadding(
                horizontal,
                resources.getDimensionPixelSize(R.dimen.space_sm),
                horizontal,
                0,
            )
            addView(inputLayout)
        }

        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_add_variable)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.template_add_variable, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() -> inputLayout.error =
                        getString(R.string.template_variable_name_title)

                    !TemplatePlaceholderParser.isValidName(name) -> inputLayout.error =
                        getString(R.string.template_variable_name_invalid)

                    name in TemplatePlaceholderParser.names(currentBody()) -> inputLayout.error =
                        getString(R.string.template_variable_name_duplicate)

                    else -> {
                        insertPlaceholder(name)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun renderFixedValues() {
        binding.fixedValuesContainer.removeAllViews()
        binding.fixedValuesEmptyText.isVisible = fixedValues.isEmpty()
        fixedValues.forEach { (name, value) ->
            binding.fixedValuesContainer.addView(createFixedValueRow(name, value))
        }
    }

    private fun createFixedValueRow(name: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val background = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
            setBackgroundResource(background.resourceId)
            contentDescription = getString(R.string.template_fixed_edit, name)
            setOnClickListener { showFixedValueDialog(name) }
        }
        row.addView(
            TextView(this).apply {
                text = getString(
                    R.string.template_fixed_item,
                    TemplatePlaceholderParser.placeholderFor(name),
                    value,
                )
                setTextAppearance(R.style.TextAppearance_Hudhud_CardBody)
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            },
        )
        row.addView(
            ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@TemplateEditorActivity,
                        R.color.hudhud_on_surface_muted,
                    ),
                )
                contentDescription = getString(R.string.template_fixed_delete, name)
                val borderless = TypedValue()
                theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless,
                    borderless,
                    true,
                )
                setBackgroundResource(borderless.resourceId)
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.touch_target),
                    resources.getDimensionPixelSize(R.dimen.touch_target),
                )
                setOnClickListener { confirmRemoveFixedValue(name) }
            },
        )
        return row
    }

    private fun confirmRemoveFixedValue(name: String) {
        // Removing the fixed value turns the placeholder back into a document column.
        val stillUsed = name in TemplatePlaceholderParser.names(currentBody())
        if (!stillUsed) {
            fixedValues.remove(name)
            renderFixedValues()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_fixed_delete_title)
            .setMessage(getString(R.string.template_fixed_delete_body, name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.template_fixed_delete_action) { _, _ ->
                fixedValues.remove(name)
                renderFixedValues()
            }
            .show()
    }

    private fun showFixedValueDialog(existingName: String? = null) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = resources.getDimensionPixelSize(R.dimen.space_lg)
            setPadding(
                horizontal,
                resources.getDimensionPixelSize(R.dimen.space_sm),
                horizontal,
                0,
            )
        }

        val nameLayout = TextInputLayout(this).apply {
            hint = getString(R.string.template_fixed_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val nameInput = TextInputEditText(nameLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            hint = getString(R.string.template_fixed_name_hint)
            setText(existingName.orEmpty())
            isEnabled = existingName == null
        }
        nameLayout.addView(nameInput)
        container.addView(nameLayout)

        val valueLayout = TextInputLayout(this).apply {
            hint = getString(R.string.template_fixed_value)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
            }
        }
        val valueInput = TextInputEditText(valueLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.template_fixed_value_hint)
            setText(fixedValues[existingName].orEmpty())
        }
        valueLayout.addView(valueInput)
        container.addView(valueLayout)

        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (existingName == null) {
                    R.string.template_fixed_dialog_add_title
                } else {
                    R.string.template_fixed_dialog_edit_title
                },
            )
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                if (existingName == null) {
                    R.string.template_add_fixed
                } else {
                    R.string.template_fixed_save
                },
                null,
            )
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val value = valueInput.text?.toString()?.trim().orEmpty()
                nameLayout.error = null
                valueLayout.error = null
                if (existingName == null) {
                    when {
                        name.isEmpty() -> {
                            nameLayout.error = getString(R.string.template_fixed_name_required)
                            return@setOnClickListener
                        }

                        !TemplatePlaceholderParser.isValidName(name) -> {
                            nameLayout.error = getString(R.string.template_variable_name_invalid)
                            return@setOnClickListener
                        }

                        name in fixedValues -> {
                            nameLayout.error = getString(R.string.template_fixed_name_duplicate)
                            return@setOnClickListener
                        }
                    }
                }
                if (value.isEmpty()) {
                    valueLayout.error = getString(R.string.template_fixed_value_required)
                    return@setOnClickListener
                }
                fixedValues[existingName ?: name] = value
                renderFixedValues()
                if (existingName == null &&
                    name !in TemplatePlaceholderParser.names(currentBody())
                ) {
                    insertPlaceholder(name)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
        if (existingName == null) nameInput.requestFocus() else valueInput.requestFocus()
    }

    private fun importFieldsFrom(uri: Uri, fileName: String) {
        if (!fileName.endsWith(".xlsx", ignoreCase = true)) {
            Toast.makeText(this, R.string.template_import_failed, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                val structure = withContext(Dispatchers.IO) {
                    val stream = contentResolver.openInputStream(uri)
                    if (stream == null) throw IOException()
                    stream.use(workbookExtractor::inspect)
                }
                importedFields = structure.headers.filter { it.isNotBlank() }
                binding.phoneColumnInput.setAdapter(
                    ArrayAdapter(
                        this@TemplateEditorActivity,
                        android.R.layout.simple_list_item_1,
                        importedFields,
                    ),
                )
                renderImportedFields()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(
                    this@TemplateEditorActivity,
                    R.string.template_import_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun save() {
        val name = binding.templateNameInput.text?.toString().orEmpty()
        val body = currentBody()
        val validation = TemplateValidator.validate(name, body)

        binding.nameLayout.error =
            if (validation.has(TemplateValidationError.NAME_REQUIRED)) {
                getString(R.string.required_fields)
            } else {
                null
            }
        binding.bodyLayout.error =
            if (validation.has(TemplateValidationError.BODY_REQUIRED)) {
                getString(R.string.required_fields)
            } else {
                null
            }
        if (!validation.isValid) return

        val phoneColumn = currentPhone()
        val existingMapping = loadedTemplate?.variableColumns.orEmpty()
        val fixedSnapshot = fixedValues.toMap()
        // A fixed value is filled from the template itself, so it is not a document column.
        val variableColumns = validation.placeholders
            .filterNot { it in fixedSnapshot }
            .associateWith { placeholder -> existingMapping[placeholder] ?: placeholder }

        binding.saveTemplateButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val id = templateId
                if (id == null) {
                    repository.createTemplate(
                        name = name,
                        body = body,
                        phoneColumn = phoneColumn,
                        variableColumns = variableColumns,
                        fixedValues = fixedSnapshot,
                    )
                    deliverResult(wasUpdate = false)
                } else {
                    val updated = repository.updateTemplate(
                        templateId = id,
                        name = name,
                        body = body,
                        phoneColumn = phoneColumn,
                        variableColumns = variableColumns,
                        fixedValues = fixedSnapshot,
                    )
                    if (!updated) {
                        binding.saveTemplateButton.isEnabled = true
                        Toast.makeText(
                            this@TemplateEditorActivity,
                            R.string.template_not_found,
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                    deliverResult(wasUpdate = true)
                }
            } catch (error: SQLiteException) {
                binding.saveTemplateButton.isEnabled = true
                Toast.makeText(
                    this@TemplateEditorActivity,
                    error.message ?: getString(R.string.required_fields),
                    Toast.LENGTH_LONG,
                ).show()
            } catch (error: IllegalArgumentException) {
                binding.saveTemplateButton.isEnabled = true
                Toast.makeText(
                    this@TemplateEditorActivity,
                    error.message ?: getString(R.string.required_fields),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun deliverResult(wasUpdate: Boolean) {
        saved = true
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_WAS_UPDATE, wasUpdate),
        )
        finish()
    }

    companion object {
        private const val EXTRA_TEMPLATE_ID = "com.hudhud.app.extra.TEMPLATE_ID"
        const val EXTRA_WAS_UPDATE = "com.hudhud.app.extra.WAS_UPDATE"

        fun intent(context: Context, templateId: Long? = null): Intent =
            Intent(context, TemplateEditorActivity::class.java).apply {
                if (templateId != null) putExtra(EXTRA_TEMPLATE_ID, templateId)
            }
    }
}
