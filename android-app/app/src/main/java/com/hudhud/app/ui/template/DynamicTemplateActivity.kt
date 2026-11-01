package com.hudhud.app.ui.template

import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hudhud.app.R
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.TemplateRepository
import com.hudhud.app.data.local.CampaignEntity
import com.hudhud.app.databinding.ActivityDynamicTemplateBinding
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.template.TemplatePlaceholderParser
import com.hudhud.app.ui.shared.NoMatchingTemplateDialog
import com.hudhud.app.ui.shared.SpreadsheetDocumentPicker
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hudhud.app.data.document.TemplateWorkbookException
import com.hudhud.app.data.document.TemplateWorkbookSummary
import com.hudhud.app.data.document.TemplateWorkbookMatch
import com.hudhud.app.data.document.TemplateWorkbookMatcher
import com.hudhud.app.data.document.XlsxTemplateSchemaReader
import com.hudhud.app.data.local.CampaignStorage
import com.hudhud.app.data.local.TemplateCampaignRepository
import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.MessageTemplate
import com.hudhud.app.ui.campaigns.CampaignsActivity
import java.io.IOException
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.openxml4j.exceptions.OpenXML4JException
import org.xml.sax.SAXException
import javax.xml.parsers.ParserConfigurationException

class DynamicTemplateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDynamicTemplateBinding
    private var templates: List<SavedTemplate> = emptyList()
    private var selectedTemplateId: Long? = null
    private var selectedWorkbookUri: Uri? = null
    private var selectedWorkbookName: String = ""
    private var latestPreview: TemplateCampaignPreview? = null
    private val schemaReader = XlsxTemplateSchemaReader()
    private var compatiblePreviews: Map<Long, TemplateWorkbookMatch> = emptyMap()
    private var campaignRepository: TemplateCampaignRepository? = null
    private var activeDraft: CampaignEntity? = null
    private var previewPage = 0
    private var loadingPreview = false
    private var templatesLoaded = false
    private var matchingWorkbook = false
    private var preparingDraft = false
    private var documentGeneration = 0L

    /** Workbook we already offered to create a template for, so the prompt is not repeated. */
    private var promptedWorkbookUri: Uri? = null

    private data class TemplateCampaignPreview(
        val templateId: Long?,
        val summary: TemplateWorkbookSummary,
        val campaignId: Long,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDynamicTemplateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        campaignRepository = TemplateCampaignRepository(
            CampaignStorage(AppDatabase.getInstance(this).campaignDao()),
        )
        title = getString(R.string.dynamic_path)
        binding.chooseFileButton.setText(R.string.template_campaign_choose_excel)

        val picker = SpreadsheetDocumentPicker(
            activity = this,
            onPicked = { uri, fileName ->
                handleSelectedDocument(uri, fileName)
            },
            onFailure = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            },
        )
        binding.chooseFileButton.setOnClickListener { picker.launch() }
        binding.previewCampaignButton.visibility = View.GONE
        binding.addTemplateCampaignButton.setOnClickListener { confirmAddCampaign() }
        binding.previousPreviewPageButton.setOnClickListener {
            if (previewPage > 0) {
                previewPage--
                loadPreviewPage()
            }
        }
        binding.nextPreviewPageButton.setOnClickListener {
            val pageCount = latestPreview?.summary?.totalRows?.let(::pageCount) ?: 0
            if (previewPage + 1 < pageCount) {
                previewPage++
                loadPreviewPage()
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleCampaignBack()
            }
        })

        val repository = TemplateRepository(AppDatabase.getInstance(this).templateDao())
        lifecycleScope.launch {
            repository.observeTemplates().collectLatest { templates ->
                this@DynamicTemplateActivity.templates = templates
                templatesLoaded = true
                if (selectedTemplateId != null && selectedTemplateId !in templates.map { it.id }) {
                    selectedTemplateId = null
                    latestPreview = null
                }
                if (selectedWorkbookUri != null) {
                    matchWorkbookTemplates()
                } else {
                    renderTemplates()
                }
            }
        }
        val resumeDraftId = intent.getLongExtra(EXTRA_RESUME_DRAFT_ID, 0L)
        if (resumeDraftId > 0L) {
            resumeDraft(resumeDraftId)
        } else {
            handleIncomingShare(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val resumeDraftId = intent.getLongExtra(EXTRA_RESUME_DRAFT_ID, 0L)
        if (resumeDraftId > 0L) {
            resumeDraft(resumeDraftId)
        } else {
            handleIncomingShare(intent)
        }
    }

    private fun handleCampaignBack() {
        val draft = activeDraft
        if (draft == null) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_campaign_discard_draft_title)
            .setMessage(R.string.template_campaign_discard_draft_body)
            .setNegativeButton(R.string.template_campaign_keep_review_action, null)
            .setPositiveButton(R.string.template_campaign_discard_draft_action) { _, _ ->
                activeDraft = null
                lifecycleScope.launch {
                    campaignRepository?.discardDraft(draft.id)
                    finish()
                }
            }
            .show()
    }

    private fun handleSelectedDocument(uri: Uri, fileName: String) {
        documentGeneration++
        matchingWorkbook = false
        preparingDraft = false
        discardActiveDraft()
        binding.selectedFileText.text = fileName
        selectedWorkbookUri = null
        selectedWorkbookName = ""
        compatiblePreviews = emptyMap()
        selectedTemplateId = null
        latestPreview = null
        promptedWorkbookUri = null
        binding.documentProfileText.visibility = View.GONE
        clearPreview()
        when {
            fileName.endsWith(".pdf", ignoreCase = true) -> {
                binding.documentProfileText.visibility = View.VISIBLE
                binding.documentProfileText.text =
                    getString(R.string.template_campaign_pdf_deferred)
            }
            fileName.endsWith(".xlsx", ignoreCase = true) -> inspectWorkbook(uri, fileName)
            else -> {
                binding.documentProfileText.visibility = View.VISIBLE
                binding.documentProfileText.text =
                    getString(R.string.template_campaign_xlsx_required)
            }
        }
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri == null) {
            binding.documentProfileText.visibility = View.VISIBLE
            binding.documentProfileText.text = getString(R.string.template_campaign_shared_file_invalid)
            return
        }
        val name = try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            } ?: uri.lastPathSegment ?: getString(R.string.no_file_selected)
        } catch (error: SecurityException) {
            binding.documentProfileText.visibility = View.VISIBLE
            binding.documentProfileText.text = getString(
                R.string.template_campaign_failed,
                error.message ?: getString(R.string.template_campaign_shared_file_invalid),
            )
            return
        }
        handleSelectedDocument(uri, name)
    }

    private fun resumeDraft(campaignId: Long) {
        lifecycleScope.launch {
            val draft = campaignRepository?.loadDraft(campaignId)
            if (draft == null) {
                Toast.makeText(this@DynamicTemplateActivity, R.string.campaign_draft_missing, Toast.LENGTH_LONG)
                    .show()
                finish()
                return@launch
            }
            activeDraft = draft
            selectedWorkbookUri = null
            selectedWorkbookName = draft.name
            selectedTemplateId = null
            binding.selectedFileText.text = draft.name
            binding.documentProfileText.visibility = View.VISIBLE
            binding.documentProfileText.text = getString(R.string.campaign_draft_title)
            latestPreview = TemplateCampaignPreview(
                templateId = null,
                summary = TemplateWorkbookSummary(
                    matchedSheets = 1,
                    totalRows = draft.totalMessages,
                    validRows = draft.validMessages,
                    invalidRows = draft.reviewMessages,
                ),
                campaignId = draft.id,
            )
            showPreview(latestPreview!!)
            renderTemplates()
        }
    }

    private fun discardActiveDraft() {
        val draft = activeDraft ?: return
        activeDraft = null
        lifecycleScope.launch { campaignRepository?.discardDraft(draft.id) }
    }

    private fun inspectWorkbook(uri: Uri, fileName: String) {
        selectedWorkbookUri = uri
        selectedWorkbookName = fileName
        selectedTemplateId = null
        compatiblePreviews = emptyMap()
        latestPreview = null
        binding.documentProfileText.visibility = View.VISIBLE
        binding.documentProfileText.text = fileName
        binding.dynamicPreviewContainer.removeAllViews()
        binding.dynamicPreviewContainer.visibility = View.GONE
        binding.dynamicPreviewStatus.visibility = View.VISIBLE
        binding.dynamicPreviewStatus.text = getString(R.string.template_campaign_matching)
        binding.addTemplateCampaignButton.isEnabled = false
        binding.chooseFileButton.isEnabled = false
        renderTemplates()
        if (templatesLoaded) matchWorkbookTemplates()
    }

    private fun matchWorkbookTemplates() {
        val uri = selectedWorkbookUri ?: return
        if (!templatesLoaded || matchingWorkbook || preparingDraft) return
        val generation = documentGeneration

        selectedTemplateId = null
        compatiblePreviews = emptyMap()
        latestPreview = null
        binding.dynamicPreviewContainer.removeAllViews()
        binding.dynamicPreviewContainer.visibility = View.GONE
        binding.dynamicPreviewStatus.visibility = View.VISIBLE
        binding.dynamicPreviewStatus.text = getString(R.string.template_campaign_matching)
        renderTemplates()

        if (templates.isEmpty()) {
            binding.dynamicPreviewStatus.text =
                getString(R.string.template_campaign_no_saved_templates)
            promptMissingTemplate(uri)
            return
        }

        val candidates = templates.filter { it.phoneColumn.isNotBlank() }
        if (candidates.isEmpty()) {
            binding.dynamicPreviewStatus.text =
                getString(R.string.template_campaign_no_matching_template)
            promptMissingTemplate(uri)
            return
        }

        matchingWorkbook = true
        lifecycleScope.launch {
            try {
                val matches = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException(getString(R.string.ready_workbook_read_failed))
                    val schemas = input.use {
                        schemaReader.read(it, candidates.map { template -> template.phoneColumn }.toSet())
                    }
                    TemplateWorkbookMatcher.match(schemas, candidates)
                }
                if (generation != documentGeneration) return@launch

                compatiblePreviews = matches.associateBy { it.template.id }
                val validMatches = matches.filter(TemplateWorkbookMatch::isMatch)
                when (validMatches.size) {
                    0 -> {
                        binding.dynamicPreviewStatus.text =
                            getString(R.string.template_campaign_no_matching_template)
                        val missingColumns = matches
                            .flatMap(TemplateWorkbookMatch::missingColumns)
                            .plus(matches.flatMap(TemplateWorkbookMatch::ambiguousColumns))
                            .distinct()
                        val message = if (missingColumns.isEmpty()) {
                            null
                        } else {
                            getString(
                                R.string.template_campaign_missing_columns,
                                missingColumns.joinToString("، "),
                            )
                        }
                        promptMissingTemplate(uri, message)
                    }
                    1 -> {
                        selectedTemplateId = validMatches.single().template.id
                        binding.documentProfileText.text = getString(
                            R.string.template_campaign_template_matched,
                            selectedTemplate()?.name.orEmpty(),
                        )
                        matchingWorkbook = false
                        prepareDraft(validMatches.single().template)
                    }
                    else -> {
                        binding.dynamicPreviewStatus.text =
                            getString(R.string.template_campaign_choose_matching_template)
                    }
                }
                renderTemplates()
            } catch (error: TemplateWorkbookException) {
                showPreviewError(error.message)
            } catch (error: IOException) {
                showWorkbookError(error.message)
            } catch (error: SecurityException) {
                showWorkbookError(error.message)
            } catch (error: OpenXML4JException) {
                showWorkbookError(error.message)
            } catch (error: SAXException) {
                showWorkbookError(error.message)
            } catch (error: ParserConfigurationException) {
                showWorkbookError(error.message)
            } finally {
                if (generation == documentGeneration) {
                    matchingWorkbook = false
                    binding.chooseFileButton.isEnabled = true
                    updateCampaignActions()
                }
            }
        }
    }

    private fun promptMissingTemplate(uri: Uri, message: String? = null) {
        if (promptedWorkbookUri == uri) return
        promptedWorkbookUri = uri
        NoMatchingTemplateDialog.show(this, message)
    }

    private fun showPreview(preview: TemplateCampaignPreview) {
        binding.dynamicPreviewStatus.visibility = View.VISIBLE
        binding.dynamicPreviewStatus.text = getString(
            R.string.template_campaign_preview_summary,
            preview.summary.matchedSheets,
            preview.summary.totalRows,
            preview.summary.validRows,
            preview.summary.invalidRows,
        )
        previewPage = 0
        loadPreviewPage()
        if (preview.summary.totalRows == 0) {
            binding.dynamicPreviewStatus.text = getString(R.string.ready_preview_no_rows)
        }
    }

    private fun prepareDraft(template: SavedTemplate) {
        val uri = selectedWorkbookUri ?: return
        if (preparingDraft) return
        val generation = documentGeneration
        discardActiveDraft()
        latestPreview = null
        binding.dynamicPreviewContainer.removeAllViews()
        binding.dynamicPreviewContainer.visibility = View.GONE
        binding.previewPagination.visibility = View.GONE
        preparingDraft = true
        binding.dynamicPreviewStatus.visibility = View.VISIBLE
        binding.dynamicPreviewStatus.text = getString(R.string.template_campaign_preview_loading)
        binding.addTemplateCampaignButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val draft = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException(getString(R.string.ready_workbook_read_failed))
                    input.use {
                        val repository = campaignRepository
                            ?: throw IllegalStateException("تعذر الوصول إلى مستودع الحملات.")
                        repository.prepareDraftFromWorkbook(
                            name = selectedWorkbookName,
                            input = it,
                            template = template.toMessageTemplate(),
                        )
                    }
                }
                if (generation != documentGeneration) {
                    campaignRepository?.discardDraft(draft.id)
                    return@launch
                }
                activeDraft = draft
                latestPreview = TemplateCampaignPreview(
                    templateId = template.id,
                    summary = TemplateWorkbookSummary(
                        matchedSheets = compatiblePreviews[template.id]?.matchedSheets?.size ?: 1,
                        totalRows = draft.totalMessages,
                        validRows = draft.validMessages,
                        invalidRows = draft.reviewMessages,
                    ),
                    campaignId = draft.id,
                )
                showPreview(latestPreview!!)
            } catch (error: TemplateWorkbookException) {
                showPreviewError(error.message)
            } catch (error: IOException) {
                showWorkbookError(error.message)
            } catch (error: SecurityException) {
                showWorkbookError(error.message)
            } catch (error: OpenXML4JException) {
                showWorkbookError(error.message)
            } catch (error: SAXException) {
                showWorkbookError(error.message)
            } catch (error: ParserConfigurationException) {
                showWorkbookError(error.message)
            } catch (error: IllegalStateException) {
                showPreviewError(error.message)
            } finally {
                if (generation == documentGeneration) {
                    preparingDraft = false
                    updateCampaignActions()
                }
            }
        }
    }

    private fun loadPreviewPage() {
        val preview = latestPreview ?: return
        if (loadingPreview) return
        loadingPreview = true
        lifecycleScope.launch {
            try {
                val messages = campaignRepository?.loadDraftMessages(
                    campaignId = preview.campaignId,
                    page = previewPage,
                    pageSize = PREVIEW_PAGE_SIZE,
                ) ?: throw IllegalStateException("تعذر تحميل رسائل مسودة الحملة.")
                if (latestPreview?.campaignId != preview.campaignId) return@launch
                binding.dynamicPreviewContainer.removeAllViews()
                messages.forEach { message ->
                    binding.dynamicPreviewContainer.addView(
                        createPreviewCard(
                            MessageDraft(
                                sourceRow = message.sourceRow,
                                phone = message.phone,
                                text = message.text,
                                errors = message.errors.split("\n").filter(String::isNotBlank),
                            ),
                        ),
                    )
                }
                binding.dynamicPreviewContainer.visibility =
                    if (messages.isEmpty()) View.GONE else View.VISIBLE
                val pageCount = pageCount(preview.summary.totalRows)
                binding.previewPagination.visibility =
                    if (pageCount > 1) View.VISIBLE else View.GONE
                binding.previewPageText.text = getString(
                    R.string.template_campaign_page,
                    previewPage + 1,
                    pageCount,
                )
                binding.previousPreviewPageButton.isEnabled = previewPage > 0
                binding.nextPreviewPageButton.isEnabled = previewPage + 1 < pageCount
            } catch (error: IllegalStateException) {
                showPreviewError(error.message)
            } finally {
                loadingPreview = false
                updateCampaignActions()
            }
        }
    }

    private fun pageCount(totalRows: Int): Int =
        (totalRows + PREVIEW_PAGE_SIZE - 1) / PREVIEW_PAGE_SIZE

    private fun confirmAddCampaign() {
        val preview = latestPreview
        val template = selectedTemplate()
        if (preview == null ||
            activeDraft?.id != preview.campaignId ||
            preview.templateId != template?.id
        ) {
            binding.dynamicPreviewStatus.visibility = View.VISIBLE
            binding.dynamicPreviewStatus.text =
                getString(R.string.template_campaign_preview_loading)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_campaign_confirm_title)
            .setMessage(
                if (template == null) {
                    getString(R.string.campaign_draft_confirm_body, preview.summary.totalRows)
                } else {
                    getString(
                        R.string.template_campaign_confirm_body,
                        preview.summary.totalRows,
                        template.name,
                    )
                },
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.template_campaign_confirm_action) { _, _ ->
                saveCampaign(preview.campaignId)
            }
            .show()
    }

    private fun saveCampaign(campaignId: Long) {
        binding.dynamicPreviewStatus.visibility = View.VISIBLE
        binding.dynamicPreviewStatus.text = getString(R.string.template_campaign_confirming)
        binding.addTemplateCampaignButton.isEnabled = false

        lifecycleScope.launch {
            try {
                campaignRepository?.confirmDraft(campaignId)
                    ?: throw IllegalStateException("تعذر الوصول إلى مستودع الحملات.")
                activeDraft = null
                Toast.makeText(
                    this@DynamicTemplateActivity,
                    R.string.template_campaign_saved,
                    Toast.LENGTH_LONG,
                ).show()
                startActivity(
                    Intent(this@DynamicTemplateActivity, CampaignsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                finish()
            } catch (error: TemplateWorkbookException) {
                showPreviewError(error.message)
            } catch (error: IllegalStateException) {
                showPreviewError(error.message)
            } finally {
                updateCampaignActions()
            }
        }
    }

    private fun createPreviewCard(draft: MessageDraft): View {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.radius_card)
            cardElevation = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.template_default_stroke)
            setStrokeColor(
                ContextCompat.getColor(
                    this@DynamicTemplateActivity,
                    if (draft.isValid) R.color.hudhud_outline else R.color.hudhud_warn,
                ),
            )
            setCardBackgroundColor(
                ContextCompat.getColor(this@DynamicTemplateActivity, R.color.hudhud_surface),
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
            )
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.template_campaign_row, draft.sourceRow, draft.phone)
            setTextAppearance(R.style.TextAppearance_Hudhud_CardTitle)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.template_campaign_message, draft.text)
            setTextAppearance(R.style.TextAppearance_Hudhud_CardBody)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        })
        if (!draft.isValid) {
            content.addView(TextView(this).apply {
                text = draft.errors.joinToString("، ")
                setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
                setTextColor(ContextCompat.getColor(this@DynamicTemplateActivity, R.color.hudhud_warn))
            })
        }
        card.addView(content)
        return card.apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
            }
        }
    }

    private fun showWorkbookError(message: String?) {
        discardActiveDraft()
        selectedWorkbookUri = null
        compatiblePreviews = emptyMap()
        selectedTemplateId = null
        latestPreview = null
        binding.dynamicPreviewContainer.removeAllViews()
        binding.dynamicPreviewContainer.visibility = View.GONE
        binding.previewPagination.visibility = View.GONE
        binding.documentProfileText.visibility = View.VISIBLE
        binding.documentProfileText.text =
            message ?: getString(R.string.ready_workbook_read_failed)
        updateCampaignActions()
    }

    private fun showPreviewError(message: String?) {
        latestPreview = null
        binding.dynamicPreviewStatus.visibility = View.VISIBLE
        binding.dynamicPreviewStatus.text = getString(
            R.string.template_campaign_failed,
            message ?: getString(R.string.ready_workbook_read_failed),
        )
    }

    private fun clearPreview() {
        latestPreview = null
        binding.dynamicPreviewStatus.visibility = View.GONE
        binding.dynamicPreviewContainer.removeAllViews()
        binding.dynamicPreviewContainer.visibility = View.GONE
        binding.previewPagination.visibility = View.GONE
        updateCampaignActions()
    }

    private fun selectedTemplate(): SavedTemplate? =
        templates.firstOrNull { it.id == selectedTemplateId }

    private fun updateCampaignActions() {
        val hasSelection = activeDraft != null ||
            (selectedWorkbookUri != null && selectedTemplate() != null)
        binding.previewCampaignButton.isEnabled = false
        binding.addTemplateCampaignButton.isEnabled =
            hasSelection &&
                activeDraft != null &&
                !matchingWorkbook &&
                !preparingDraft &&
                !loadingPreview &&
                latestPreview?.summary?.totalRows?.let { it > 0 } == true &&
                latestPreview?.templateId == selectedTemplateId &&
                !matchingWorkbook
    }

    private fun SavedTemplate.fixedValuesSummary(): String =
        fixedValues.entries.joinToString(" · ") { (name, value) ->
            "${TemplatePlaceholderParser.placeholderFor(name)} = $value"
        }

    private fun SavedTemplate.toMessageTemplate() = MessageTemplate(
        name = name,
        body = body,
        phoneColumn = phoneColumn,
        variableColumns = variableColumns,
        fixedValues = fixedValues,
    )

    private fun renderTemplates() {
        binding.templatesContainer.removeAllViews()
        val matchingTemplates = compatiblePreviews.values
            .filter(TemplateWorkbookMatch::isMatch)
            .map(TemplateWorkbookMatch::template)
        val showTemplateChoices = selectedWorkbookUri != null && matchingTemplates.size > 1
        val showNoTemplates = selectedWorkbookUri != null && templates.isEmpty()
        binding.savedTemplatesTitle.visibility =
            if (showTemplateChoices) View.VISIBLE else View.GONE
        binding.templatesContainer.visibility =
            if (showTemplateChoices) View.VISIBLE else View.GONE
        binding.emptyTemplatesText.visibility =
            if (showNoTemplates) View.VISIBLE else View.GONE
        binding.selectedTemplateText.visibility =
            if (selectedTemplateId == null) View.GONE else View.VISIBLE

        matchingTemplates.forEach { template ->
            val selected = template.id == selectedTemplateId
            val card = MaterialCardView(this).apply {
                radius = resources.getDimension(R.dimen.radius_card)
                cardElevation = 0f
                strokeWidth = resources.getDimensionPixelSize(
                    if (selected) R.dimen.template_selected_stroke else R.dimen.template_default_stroke,
                )
                setStrokeColor(
                    ContextCompat.getColor(
                        this@DynamicTemplateActivity,
                        if (selected) R.color.hudhud_primary else R.color.hudhud_outline,
                    ),
                )
                setCardBackgroundColor(ContextCompat.getColor(this@DynamicTemplateActivity, R.color.hudhud_surface))
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.space_md),
                    resources.getDimensionPixelSize(R.dimen.space_md),
                    resources.getDimensionPixelSize(R.dimen.space_md),
                    resources.getDimensionPixelSize(R.dimen.space_md),
                )
            }
            content.addView(TextView(this).apply {
                text = template.name
                setTextAppearance(R.style.TextAppearance_Hudhud_CardTitle)
            })
            content.addView(TextView(this).apply {
                text = template.body
                setTextAppearance(R.style.TextAppearance_Hudhud_CardBody)
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                params.topMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
                layoutParams = params
            })
            if (template.fixedValues.isNotEmpty()) {
                content.addView(TextView(this).apply {
                    text = getString(R.string.template_details_fixed, template.fixedValuesSummary())
                    setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    params.topMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
                    layoutParams = params
                })
            }
            content.addView(MaterialButton(this).apply {
                text = getString(
                    if (selected) R.string.template_selected_action else R.string.template_select_action,
                )
                isEnabled = !selected
                setOnClickListener {
                    selectedTemplateId = template.id
                    renderTemplates()
                    binding.documentProfileText.text = getString(
                        R.string.template_campaign_template_matched,
                        template.name,
                    )
                    prepareDraft(template)
                }
            })
            card.addView(content)
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
            }
            binding.templatesContainer.addView(card, params)
        }

        templates.firstOrNull { it.id == selectedTemplateId }?.let { selected ->
            binding.selectedTemplateText.text = getString(
                R.string.template_selected,
                selected.name,
                selected.phoneColumn.ifBlank { getString(R.string.template_phone_column) },
                selected.variableColumns.keys.joinToString().ifBlank { "—" },
                selected.body,
                selected.fixedValuesSummary()
                    .ifBlank { getString(R.string.template_details_none) },
            )
        }
        updateCampaignActions()
    }

    companion object {
        const val PREVIEW_PAGE_SIZE = 20
        const val EXTRA_RESUME_DRAFT_ID = "com.hudhud.app.extra.RESUME_DRAFT_ID"

        fun resumeIntent(context: android.content.Context, campaignId: Long): Intent =
            Intent(context, DynamicTemplateActivity::class.java)
                .putExtra(EXTRA_RESUME_DRAFT_ID, campaignId)
    }
}
