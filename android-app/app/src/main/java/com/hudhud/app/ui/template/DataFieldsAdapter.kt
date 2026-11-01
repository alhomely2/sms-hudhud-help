package com.hudhud.app.ui.template

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hudhud.app.R
import com.hudhud.app.databinding.ItemTemplateFieldBinding
import com.hudhud.app.domain.model.ColumnInfo

/**
 * Adapter for displaying document fields (columns) in the template editor.
 * Shows each column with checkbox for selection and add button.
 */
class DataFieldsAdapter(
    private val onFieldSelected: (ColumnInfo) -> Unit,
    private val onAddField: (ColumnInfo) -> Unit
) : RecyclerView.Adapter<DataFieldsAdapter.ViewHolder>() {

    private var fields: List<ColumnInfo> = emptyList()
    private var sampleData: Map<String, String> = emptyMap()
    private val selectedFields = mutableSetOf<String>()

    fun submitList(newFields: List<ColumnInfo>, sample: Map<String, String> = emptyMap()) {
        val diffResult = DiffUtil.calculateDiff(FieldDiffCallback(fields, newFields))
        fields = newFields
        sampleData = sample
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateSelectedFields(selected: List<String>) {
        selectedFields.clear()
        selectedFields.addAll(selected)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateFieldBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(fields[position])
    }

    override fun getItemCount(): Int = fields.size

    inner class ViewHolder(
        private val binding: ItemTemplateFieldBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(field: ColumnInfo) {
            binding.fieldName.text = field.displayName.ifBlank { field.name }

            // Show sample data
            val sample = sampleData[field.name]
            binding.fieldSample.text = if (sample.isNullOrEmpty()) {
                binding.root.context.getString(R.string.sample_data, "-")
            } else {
                binding.root.context.getString(R.string.sample_data, sample)
            }

            // Check if already selected
            val isSelected = selectedFields.contains(field.name)
            binding.fieldCheckbox.isChecked = isSelected

            // Update button state
            binding.addFieldButton.isEnabled = !isSelected
            binding.addFieldButton.text = if (isSelected) {
                binding.root.context.getString(R.string.template_selected_action)
            } else {
                binding.root.context.getString(R.string.add)
            }

            // Click listeners
            binding.fieldCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (!selectedFields.contains(field.name)) {
                        onFieldSelected(field)
                        selectedFields.add(field.name)
                        notifyItemChanged(adapterPosition)
                    }
                } else if (selectedFields.contains(field.name)) {
                    selectedFields.remove(field.name)
                    notifyItemChanged(adapterPosition)
                }
            }

            binding.addFieldButton.setOnClickListener {
                if (!isSelected) {
                    onAddField(field)
                    selectedFields.add(field.name)
                    notifyItemChanged(adapterPosition)
                }
            }

            binding.root.setOnClickListener {
                if (binding.fieldCheckbox.isChecked) {
                    binding.fieldCheckbox.isChecked = false
                } else {
                    binding.fieldCheckbox.isChecked = true
                }
            }
        }
    }

    private class FieldDiffCallback(
        private val oldList: List<ColumnInfo>,
        private val newList: List<ColumnInfo>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].name == newList[newItemPosition].name
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}