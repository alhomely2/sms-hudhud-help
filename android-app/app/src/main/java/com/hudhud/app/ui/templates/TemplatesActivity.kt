package com.hudhud.app.ui.templates

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hudhud.app.R
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.TemplateRepository
import com.hudhud.app.databinding.ActivityTemplatesBinding
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.ui.shared.BottomNavBar
import com.hudhud.app.ui.shared.BottomNavTab
import com.hudhud.app.ui.template.TemplateEditorActivity
import com.hudhud.app.ui.template.TemplateEditorV2Activity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The single place where templates are created, edited, duplicated or deleted.
 *
 * Every card opens the editor for that record, so saving again updates the same
 * template instead of adding a copy.
 */
class TemplatesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTemplatesBinding
    private lateinit var repository: TemplateRepository
    private var templates: List<SavedTemplate> = emptyList()

    // Using the new V2 template editor with step-by-step workflow
    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val wasUpdate = result.data
            ?.getBooleanExtra(TemplateEditorV2Activity.EXTRA_WAS_UPDATE, false) == true
        Snackbar.make(
            binding.root,
            if (wasUpdate) R.string.template_saved_updated else R.string.template_saved_new,
            Snackbar.LENGTH_SHORT,
        ).setAnchorView(binding.bottomNav.root).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BottomNavBar.bind(this, binding.bottomNav, BottomNavTab.TEMPLATES)
        repository = TemplateRepository(AppDatabase.getInstance(this).templateDao())

        binding.newTemplateButton.setOnClickListener { openEditor(null) }
        binding.clearSearchButton.setOnClickListener {
            binding.templateSearchInput.setText("")
        }
        binding.templateSearchInput.doAfterTextChanged { renderTemplates(it?.toString().orEmpty()) }

        lifecycleScope.launch {
            repository.observeTemplates().collectLatest { loaded ->
                templates = loaded
                renderTemplates(binding.templateSearchInput.text?.toString().orEmpty())
            }
        }

        consumeStartCreateRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeStartCreateRequest(intent)
    }

    private fun consumeStartCreateRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_CREATE, false) != true) return
        intent.removeExtra(EXTRA_START_CREATE)
        openEditor(null)
    }

    private fun openEditor(templateId: Long?) {
        // Use the new V2 template editor with step-by-step workflow
        editorLauncher.launch(TemplateEditorV2Activity.intent(this, templateId))
    }

    private fun renderTemplates(rawQuery: String) {
        val query = rawQuery.trim()
        val visible = if (query.isEmpty()) {
            templates
        } else {
            templates.filter { template ->
                template.name.contains(query, ignoreCase = true) ||
                    template.body.contains(query, ignoreCase = true) ||
                    template.fixedValues.any { (name, value) ->
                        name.contains(query, ignoreCase = true) ||
                            value.contains(query, ignoreCase = true)
                    }
            }
        }

        binding.templatesSearchLayout.visibility =
            if (templates.size >= 2) View.VISIBLE else View.GONE
        binding.emptyTemplatesCard.visibility =
            if (templates.isEmpty()) View.VISIBLE else View.GONE
        binding.noResultsCard.visibility =
            if (templates.isNotEmpty() && visible.isEmpty()) View.VISIBLE else View.GONE
        binding.templatesCountText.visibility =
            if (templates.isEmpty()) View.GONE else View.VISIBLE
        binding.templatesCountText.text = getString(R.string.templates_count, templates.size)

        binding.templatesContainer.removeAllViews()
        visible.forEach { template ->
            binding.templatesContainer.addView(
                TemplateCardView(this).apply {
                    bind(
                        template = template,
                        onOpen = { openEditor(template.id) },
                        onEdit = { openEditor(template.id) },
                        onDuplicate = { duplicateTemplate(template) },
                        onDelete = { confirmDelete(template) },
                    )
                },
                ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
                },
            )
        }
    }

    private fun duplicateTemplate(template: SavedTemplate) {
        lifecycleScope.launch {
            repository.duplicateTemplate(
                templateId = template.id,
                copyName = getString(R.string.template_copy_name, template.name),
            )
            Snackbar.make(
                binding.root,
                getString(R.string.template_duplicated, template.name),
                Snackbar.LENGTH_SHORT,
            ).setAnchorView(binding.bottomNav.root).show()
        }
    }

    private fun confirmDelete(template: SavedTemplate) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.template_delete_title)
            .setMessage(getString(R.string.template_delete_body, template.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.template_delete_action) { _, _ ->
                deleteTemplate(template)
            }
            .show()
    }

    private fun deleteTemplate(template: SavedTemplate) {
        lifecycleScope.launch {
            repository.deleteTemplate(template.id)
            Snackbar.make(
                binding.root,
                getString(R.string.template_deleted, template.name),
                Snackbar.LENGTH_LONG,
            )
                .setAnchorView(binding.bottomNav.root)
                .setAction(R.string.template_undo) {
                    lifecycleScope.launch { repository.restoreTemplate(template) }
                }
                .show()
        }
    }

    companion object {
        const val EXTRA_START_CREATE = "com.hudhud.app.extra.START_CREATE"
    }
}
