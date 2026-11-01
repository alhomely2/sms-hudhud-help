package com.hudhud.app.ui.ready

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hudhud.app.R
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.ReadyCampaignRepository
import com.hudhud.app.data.document.ReadyWorkbookPreview
import com.hudhud.app.data.document.ReadyWorkbookStructureException
import com.hudhud.app.data.document.XlsxReadyMessageExtractor
import com.hudhud.app.databinding.ActivityReadyMessagesBinding
import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.ready.ReadyColumnMappingException
import com.hudhud.app.ui.document.PdfColumnsActivity
import com.hudhud.app.ui.shared.SpreadsheetDocumentPicker
import com.hudhud.app.ui.shared.BottomNavBar
import com.hudhud.app.ui.shared.BottomNavTab
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.openxml4j.exceptions.OpenXML4JException
import org.xml.sax.SAXException
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

class ReadyMessagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReadyMessagesBinding
    private val extractor = XlsxReadyMessageExtractor()
    private var selectedWorkbookUri: Uri? = null
    private var availableHeaders: List<String> = emptyList()
    private var lastPreview: ReadyWorkbookPreview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadyMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.ready_path)
        BottomNavBar.bind(this, binding.bottomNav, BottomNavTab.HOME) {
            confirmCreateCampaign()
        }

        val picker = SpreadsheetDocumentPicker(
            activity = this,
            onPicked = { uri, fileName ->
                binding.selectedFileText.text = fileName
                clearPreview()
                lastPreview = null
                if (fileName.endsWith(".pdf", ignoreCase = true)) {
                    selectedWorkbookUri = null
                    startActivity(
                        Intent(this, PdfColumnsActivity::class.java)
                            .putExtra(PdfColumnsActivity.EXTRA_DOCUMENT_URI, uri.toString())
                            .putExtra(PdfColumnsActivity.EXTRA_DOCUMENT_NAME, fileName),
                    )
                } else if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                    inspectWorkbook(uri)
                } else {
                    selectedWorkbookUri = null
                    Toast.makeText(
                        this,
                        R.string.preview_unavailable,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onFailure = { message ->
                selectedWorkbookUri = null
                binding.previewMessagesButton.isEnabled = false
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            },
        )
        binding.chooseFileButton.setOnClickListener { picker.launch() }
        binding.previewMessagesButton.setOnClickListener { previewMessages() }
        binding.phoneColumnInput.setOnItemClickListener { _, _, _, _ -> mappingChanged() }
        binding.messageColumnInput.setOnItemClickListener { _, _, _, _ -> mappingChanged() }
    }

    private fun inspectWorkbook(uri: Uri) {
        selectedWorkbookUri = null
        lastPreview = null
        availableHeaders = emptyList()
        binding.previewMessagesButton.isEnabled = false
        binding.phoneColumnInput.setText("", false)
        binding.messageColumnInput.setText("", false)
        binding.phoneColumnInput.setAdapter(null)
        binding.messageColumnInput.setAdapter(null)
        binding.previewStatusText.visibility = View.VISIBLE
        binding.previewStatusText.text = getString(R.string.ready_workbook_reading)

        lifecycleScope.launch {
            try {
                val structure = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException(getString(R.string.ready_workbook_read_failed))
                    input.use(extractor::inspect)
                }
                availableHeaders = structure.headers
                selectedWorkbookUri = uri
                val adapter = ArrayAdapter(
                    this@ReadyMessagesActivity,
                    android.R.layout.simple_list_item_1,
                    structure.headers,
                )
                binding.phoneColumnInput.setAdapter(adapter)
                binding.messageColumnInput.setAdapter(adapter)
                val suggestedMapping = structure.suggestedMapping
                if (suggestedMapping != null) {
                    binding.phoneColumnInput.setText(suggestedMapping.phoneColumnName, false)
                    binding.messageColumnInput.setText(suggestedMapping.messageColumnName, false)
                    binding.previewStatusText.text = getString(
                        R.string.ready_workbook_found,
                        structure.sheetName,
                        suggestedMapping.phoneColumnName,
                        suggestedMapping.messageColumnName,
                    )
                } else {
                    structure.headers.getOrNull(0)?.let {
                        binding.phoneColumnInput.setText(it, false)
                    }
                    structure.headers.getOrNull(1)?.let {
                        binding.messageColumnInput.setText(it, false)
                    }
                    binding.previewStatusText.text = getString(
                        R.string.ready_workbook_manual_mapping,
                        structure.sheetName,
                    )
                }
                updatePreviewButton()
            } catch (error: ReadyWorkbookStructureException) {
                showWorkbookError(error.message ?: getString(R.string.ready_workbook_not_found))
            } catch (error: IOException) {
                showWorkbookError(error.message ?: getString(R.string.ready_workbook_read_failed))
            } catch (error: OpenXML4JException) {
                showWorkbookError(error.message ?: getString(R.string.ready_workbook_read_failed))
            } catch (error: SAXException) {
                showWorkbookError(error.message ?: getString(R.string.ready_workbook_read_failed))
            } catch (error: ParserConfigurationException) {
                showWorkbookError(error.message ?: getString(R.string.ready_workbook_read_failed))
            }
        }
    }

    private fun previewMessages() {
        val uri = selectedWorkbookUri ?: return
        val phoneColumn = binding.phoneColumnInput.text?.toString().orEmpty()
        val messageColumn = binding.messageColumnInput.text?.toString().orEmpty()
        if (phoneColumn.isBlank() || messageColumn.isBlank()) {
            Toast.makeText(this, R.string.required_fields, Toast.LENGTH_SHORT).show()
            return
        }
        if (phoneColumn == messageColumn) {
            Toast.makeText(this, R.string.ready_distinct_columns, Toast.LENGTH_SHORT).show()
            return
        }

        binding.previewMessagesButton.isEnabled = false
        binding.previewStatusText.visibility = View.VISIBLE
        binding.previewStatusText.text = getString(R.string.ready_preview_loading)
        binding.previewMessagesContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException(getString(R.string.ready_workbook_read_failed))
                    input.use {
                        extractor.extract(
                            input = it,
                            phoneColumnName = phoneColumn,
                            messageColumnName = messageColumn,
                        )
                    }
                }
                lastPreview = preview
                renderPreview(preview)
            } catch (error: ReadyColumnMappingException) {
                binding.previewStatusText.text =
                    error.message ?: getString(R.string.ready_workbook_not_found)
            } catch (error: IOException) {
                binding.previewStatusText.text =
                    error.message ?: getString(R.string.ready_workbook_read_failed)
            } catch (error: OpenXML4JException) {
                binding.previewStatusText.text =
                    error.message ?: getString(R.string.ready_workbook_read_failed)
            } catch (error: SAXException) {
                binding.previewStatusText.text =
                    error.message ?: getString(R.string.ready_workbook_read_failed)
            } catch (error: ParserConfigurationException) {
                binding.previewStatusText.text =
                    error.message ?: getString(R.string.ready_workbook_read_failed)
            } finally {
                updatePreviewButton()
            }
        }
    }

    private fun mappingChanged() {
        lastPreview = null
        clearPreview()
        updatePreviewButton()
    }

    private fun confirmCreateCampaign() {
        val preview = lastPreview
        if (selectedWorkbookUri == null || preview == null || preview.totalRows == 0) {
            Toast.makeText(this, R.string.ready_campaign_preview_required, Toast.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ready_campaign_confirm_title)
            .setMessage(getString(R.string.ready_campaign_confirm_body, preview.totalRows))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ready_campaign_confirm_action) { _, _ ->
                saveCampaign()
            }
            .show()
    }

    private fun saveCampaign() {
        val uri = selectedWorkbookUri ?: return
        val preview = lastPreview ?: return
        val phoneColumn = binding.phoneColumnInput.text?.toString().orEmpty()
        val messageColumn = binding.messageColumnInput.text?.toString().orEmpty()
        binding.previewStatusText.visibility = View.VISIBLE
        binding.previewStatusText.text = getString(R.string.ready_campaign_saving)
        binding.bottomNav.navSend.isEnabled = false

        lifecycleScope.launch {
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException(getString(R.string.ready_workbook_read_failed))
                val repository = ReadyCampaignRepository(
                    AppDatabase.getInstance(this@ReadyMessagesActivity).campaignDao(),
                )
                input.use {
                    repository.createFromWorkbook(
                        name = binding.selectedFileText.text.toString(),
                        input = it,
                        phoneColumnName = phoneColumn,
                        messageColumnName = messageColumn,
                    )
                }
                Toast.makeText(
                    this@ReadyMessagesActivity,
                    R.string.ready_campaign_saved,
                    Toast.LENGTH_LONG,
                ).show()
                startActivity(
                    Intent(
                        this@ReadyMessagesActivity,
                        com.hudhud.app.ui.campaigns.CampaignsActivity::class.java,
                    ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                finish()
            } catch (error: ReadyColumnMappingException) {
                showCampaignSaveError(error.message)
            } catch (error: IOException) {
                showCampaignSaveError(error.message)
            } catch (error: OpenXML4JException) {
                showCampaignSaveError(error.message)
            } catch (error: SAXException) {
                showCampaignSaveError(error.message)
            } catch (error: ParserConfigurationException) {
                showCampaignSaveError(error.message)
            } catch (error: IllegalStateException) {
                showCampaignSaveError(error.message)
            } finally {
                binding.bottomNav.navSend.isEnabled = true
                if (lastPreview === preview) {
                    binding.previewStatusText.text = getString(R.string.ready_preview_only)
                }
            }
        }
    }

    private fun showCampaignSaveError(message: String?) {
        binding.previewStatusText.text = getString(
            R.string.ready_campaign_failed,
            message ?: getString(R.string.ready_workbook_read_failed),
        )
    }

    private fun renderPreview(preview: ReadyWorkbookPreview) {
        binding.previewMessagesContainer.removeAllViews()
        binding.previewStatusText.text = if (preview.totalRows == 0) {
            getString(R.string.ready_preview_no_rows)
        } else {
            getString(
                R.string.ready_preview_summary,
                preview.totalRows,
                preview.validRows,
                preview.invalidRows,
            ) + "\n" + getString(R.string.ready_preview_only)
        }
        if (preview.totalRows == 0) {
            binding.previewMessagesContainer.visibility = View.GONE
            return
        }

        preview.messages.forEach { message ->
            binding.previewMessagesContainer.addView(createMessageCard(message))
        }
        binding.previewMessagesContainer.visibility = View.VISIBLE
        if (preview.previewLimitReached) {
            binding.previewMessagesContainer.addView(
                TextView(this).apply {
                    text = getString(
                        R.string.ready_preview_more,
                        preview.messages.size,
                        preview.totalRows,
                    )
                    setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.space_sm),
                        resources.getDimensionPixelSize(R.dimen.space_sm),
                        resources.getDimensionPixelSize(R.dimen.space_sm),
                        resources.getDimensionPixelSize(R.dimen.space_md),
                    )
                },
            )
        }
    }

    private fun createMessageCard(message: MessageDraft): View {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.radius_card)
            cardElevation = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.template_default_stroke)
            setStrokeColor(
                ContextCompat.getColor(
                    this@ReadyMessagesActivity,
                    if (message.isValid) R.color.hudhud_outline else R.color.hudhud_warn,
                ),
            )
            setCardBackgroundColor(
                ContextCompat.getColor(this@ReadyMessagesActivity, R.color.hudhud_surface),
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
            text = getString(R.string.ready_preview_row, message.sourceRow, message.phone)
            setTextAppearance(R.style.TextAppearance_Hudhud_CardTitle)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.ready_preview_message, message.text)
            setTextAppearance(R.style.TextAppearance_Hudhud_CardBody)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
            }
        })
        if (!message.isValid) {
            content.addView(TextView(this).apply {
                text = getString(
                    R.string.ready_preview_errors,
                    message.errors.joinToString("، "),
                )
                setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
                setTextColor(
                    ContextCompat.getColor(this@ReadyMessagesActivity, R.color.hudhud_warn),
                )
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

    private fun clearPreview() {
        binding.previewMessagesContainer.removeAllViews()
        binding.previewMessagesContainer.visibility = View.GONE
        binding.previewStatusText.visibility = View.GONE
    }

    private fun updatePreviewButton() {
        binding.previewMessagesButton.isEnabled =
            selectedWorkbookUri != null &&
                availableHeaders.isNotEmpty() &&
                binding.phoneColumnInput.text?.isNotBlank() == true &&
                binding.messageColumnInput.text?.isNotBlank() == true &&
                binding.phoneColumnInput.text?.toString() !=
                binding.messageColumnInput.text?.toString()
    }

    private fun showWorkbookError(message: String) {
        selectedWorkbookUri = null
        availableHeaders = emptyList()
        binding.previewMessagesButton.isEnabled = false
        binding.previewStatusText.visibility = View.VISIBLE
        binding.previewStatusText.text = message
    }
}
