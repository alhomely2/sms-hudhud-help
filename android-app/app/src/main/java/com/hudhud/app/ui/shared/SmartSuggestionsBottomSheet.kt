package com.hudhud.app.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hudhud.app.databinding.BottomSheetSmartSuggestionsBinding
import com.hudhud.app.domain.model.SmartSuggestion

/**
 * Bottom sheet for displaying smart AI suggestions.
 * Shows column detection, template matching, and other AI recommendations.
 */
class SmartSuggestionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSmartSuggestionsBinding? = null
    private val binding get() = _binding!!

    private var suggestions: List<SmartSuggestion> = emptyList()
    private var onSuggestionClickListener: ((SmartSuggestion) -> Unit)? = null

    companion object {
        const val TAG = "SmartSuggestionsSheet"

        fun newInstance(
            suggestions: List<SmartSuggestion>,
            onSuggestionClick: (SmartSuggestion) -> Unit,
        ): SmartSuggestionsBottomSheet {
            return SmartSuggestionsBottomSheet().apply {
                this.suggestions = suggestions
                this.onSuggestionClickListener = onSuggestionClick
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetSmartSuggestionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // Count by type
        val columnSuggestions = suggestions.count { it.type.name.contains("COLUMN") }
        val templateSuggestions = suggestions.count { it.type.name.contains("TEMPLATE") }
        val variableSuggestions = suggestions.count { it.type.name.contains("VARIABLE") }
        val warningSuggestions = suggestions.count { it.type.name.contains("WARNING") || it.type.name.contains("DUPLICATE") || it.type.name.contains("INVALID") }

        // Update header
        binding.suggestionsTitle.text = "اقتراحات ذكية"
        binding.suggestionsSubtitle.text = buildString {
            if (columnSuggestions > 0) append("أعمدة: $columnSuggestions")
            if (templateSuggestions > 0) {
                if (isNotEmpty()) append(" • ")
                append("قوالب: $templateSuggestions")
            }
            if (variableSuggestions > 0) {
                if (isNotEmpty()) append(" • ")
                append("ربط: $variableSuggestions")
            }
            if (warningSuggestions > 0) {
                if (isNotEmpty()) append(" • ")
                append("تحذيرات: $warningSuggestions")
            }
        }

        // Setup RecyclerView
        val adapter = SmartSuggestionAdapter(suggestions) { suggestion ->
            onSuggestionClickListener?.invoke(suggestion)
            dismiss()
        }
        
        binding.suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        // Dismiss button
        binding.dismissButton.setOnClickListener {
            dismiss()
        }

        // Apply all button
        val autoApplicableSuggestions = suggestions.filter { it.isAutoApplied }
        if (autoApplicableSuggestions.isNotEmpty()) {
            binding.applyAllButton.visibility = View.VISIBLE
            binding.applyAllButton.text = "تطبيق ${autoApplicableSuggestions.size} اقتراح تلقائي"
            binding.applyAllButton.setOnClickListener {
                autoApplicableSuggestions.forEach { suggestion ->
                    onSuggestionClickListener?.invoke(suggestion)
                }
                dismiss()
            }
        } else {
            binding.applyAllButton.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for displaying smart suggestions
 */
class SmartSuggestionAdapter(
    private val suggestions: List<SmartSuggestion>,
    private val onItemClick: (SmartSuggestion) -> Unit,
) : androidx.recyclerview.widget.RecyclerView.Adapter<SmartSuggestionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.hudhud.app.databinding.ItemSmartSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount() = suggestions.size

    inner class ViewHolder(
        private val binding: com.hudhud.app.databinding.ItemSmartSuggestionBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: SmartSuggestion) {
            binding.suggestionTitle.text = suggestion.title
            binding.suggestionDescription.text = suggestion.description
            
            // Confidence indicator
            val confidenceColor = when {
                suggestion.isHighConfidence -> com.hudhud.app.R.color.hudhud_success
                suggestion.isMediumConfidence -> com.hudhud.app.R.color.hudhud_warning
                else -> com.hudhud.app.R.color.hudhud_error
            }
            binding.confidenceIndicator.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(binding.root.context, confidenceColor)
            )
            
            // Confidence text
            binding.confidenceText.text = suggestion.confidencePercent
            binding.confidenceText.setTextColor(
                androidx.core.content.ContextCompat.getColor(binding.root.context, confidenceColor)
            )

            // Auto-applied badge
            if (suggestion.isAutoApplied) {
                binding.autoAppliedBadge.visibility = View.VISIBLE
            } else {
                binding.autoAppliedBadge.visibility = View.GONE
            }

            // Type icon
            val iconRes = when {
                suggestion.type.name.contains("COLUMN") -> com.hudhud.app.R.drawable.ic_table
                suggestion.type.name.contains("TEMPLATE") -> com.hudhud.app.R.drawable.ic_message
                suggestion.type.name.contains("WARNING") -> com.hudhud.app.R.drawable.ic_chevron
                else -> com.hudhud.app.R.drawable.ic_message
            }
            binding.typeIcon.setImageResource(iconRes)

            // Click listener
            binding.root.setOnClickListener {
                onItemClick(suggestion)
            }
        }
    }
}