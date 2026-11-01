package com.hudhud.app.ui.document

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hudhud.app.R
import com.hudhud.app.data.document.PdfColumnExtractor
import com.hudhud.app.data.document.UnsupportedPdfTemplateException
import com.hudhud.app.databinding.ActivityPdfColumnsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfColumnsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPdfColumnsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfColumnsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent.getStringExtra(EXTRA_DOCUMENT_URI)?.let(Uri::parse)
        val documentName = intent.getStringExtra(EXTRA_DOCUMENT_NAME).orEmpty()
        binding.documentNameText.text = documentName
        title = getString(R.string.document_columns_title)

        if (uri == null) {
            showError(getString(R.string.document_missing))
            return
        }

        lifecycleScope.launch {
            binding.templateSummaryText.text = getString(R.string.extracting_columns)
            try {
                val result = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: error("تعذر فتح الملف")
                    input.use { PdfColumnExtractor().extract(this@PdfColumnsActivity, it) }
                }

                binding.templateNameText.text = result.template.displayName
                binding.templateSummaryText.text = getString(
                    R.string.document_page_count,
                    result.pageCount,
                )
                binding.phoneColumnText.text = result.template.phoneColumnId?.let {
                    val phoneLabel = result.template.columns.first { column -> column.id == it }.label
                    getString(R.string.phone_column_found, phoneLabel)
                } ?: getString(R.string.phone_column_missing)
                binding.extractedColumnsText.text = result.template.columns
                    .mapIndexed { index, column -> "${index + 1}. ${column.label}" }
                    .joinToString(separator = "\n")
                if (intent.getBooleanExtra(EXTRA_RETURN_FIELD_SCHEMA, false)) {
                    setResult(
                        RESULT_OK,
                        Intent().apply {
                            putStringArrayListExtra(
                                EXTRA_FIELD_IDS,
                                ArrayList(result.template.columns.map { it.id }),
                            )
                            putStringArrayListExtra(
                                EXTRA_FIELD_LABELS,
                                ArrayList(result.template.columns.map { it.label }),
                            )
                            putExtra(
                                EXTRA_SELECTED_DOCUMENT_NAME,
                                intent.getStringExtra(EXTRA_DOCUMENT_NAME).orEmpty(),
                            )
                            putExtra(EXTRA_TEMPLATE_NAME, result.template.displayName)
                            putExtra(EXTRA_PHONE_COLUMN_ID, result.template.phoneColumnId)
                        },
                    )
                    finish()
                }
            } catch (error: UnsupportedPdfTemplateException) {
                showError(error.message ?: getString(R.string.unknown_pdf_template))
            } catch (error: java.io.IOException) {
                showError(getString(R.string.pdf_read_failed))
            } catch (error: IllegalArgumentException) {
                showError(error.message ?: getString(R.string.pdf_read_failed))
            }
        }
    }

    private fun showError(message: String) {
        binding.templateNameText.visibility = View.GONE
        binding.phoneColumnText.visibility = View.GONE
        binding.extractedColumnsText.visibility = View.GONE
        binding.templateSummaryText.text = message
    }

    companion object {
        const val EXTRA_DOCUMENT_URI = "com.hudhud.app.document.URI"
        const val EXTRA_DOCUMENT_NAME = "com.hudhud.app.document.NAME"
        const val EXTRA_RETURN_FIELD_SCHEMA = "com.hudhud.app.document.RETURN_FIELD_SCHEMA"
        const val EXTRA_FIELD_IDS = "com.hudhud.app.document.FIELD_IDS"
        const val EXTRA_FIELD_LABELS = "com.hudhud.app.document.FIELD_LABELS"
        const val EXTRA_TEMPLATE_NAME = "com.hudhud.app.document.TEMPLATE_NAME"
        const val EXTRA_PHONE_COLUMN_ID = "com.hudhud.app.document.PHONE_COLUMN_ID"
        const val EXTRA_SELECTED_DOCUMENT_NAME = "com.hudhud.app.document.SELECTED_DOCUMENT_NAME"
    }
}
