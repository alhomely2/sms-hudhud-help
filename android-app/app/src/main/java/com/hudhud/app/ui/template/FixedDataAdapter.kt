package com.hudhud.app.ui.template

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hudhud.app.databinding.ItemTemplateFixedFieldBinding
import com.hudhud.app.domain.model.FixedData

/**
 * Adapter for displaying and editing fixed data items in the template editor.
 * Fixed data is shared between all clients (e.g., company name, address).
 */
class FixedDataAdapter(
    private val onFixedDataChanged: (Int, FixedData) -> Unit,
    private val onRemoveFixedData: (Int) -> Unit
) : RecyclerView.Adapter<FixedDataAdapter.ViewHolder>() {

    private var items: List<FixedData> = emptyList()

    fun submitList(newItems: List<FixedData>) {
        val diffResult = DiffUtil.calculateDiff(FixedDataDiffCallback(items, newItems))
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateFixedFieldBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemTemplateFixedFieldBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentPosition = -1

        init {
            // Name input listener
            binding.fixedFieldNameInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentPosition in items.indices) {
                        val current = items[currentPosition]
                        onFixedDataChanged(currentPosition, current.copy(name = s?.toString() ?: ""))
                    }
                }
            })

            // Value input listener
            binding.fixedFieldValueInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentPosition in items.indices) {
                        val current = items[currentPosition]
                        onFixedDataChanged(currentPosition, current.copy(value = s?.toString() ?: ""))
                    }
                }
            })

            // Remove button
            binding.removeFixedFieldButton.setOnClickListener {
                if (currentPosition in items.indices) {
                    onRemoveFixedData(currentPosition)
                }
            }
        }

        fun bind(item: FixedData, position: Int) {
            currentPosition = position
            
            // Only set text if different to avoid infinite loop
            if (binding.fixedFieldNameInput.text?.toString() != item.name) {
                binding.fixedFieldNameInput.setText(item.name)
            }
            if (binding.fixedFieldValueInput.text?.toString() != item.value) {
                binding.fixedFieldValueInput.setText(item.value)
            }
        }
    }

    private class FixedDataDiffCallback(
        private val oldList: List<FixedData>,
        private val newList: List<FixedData>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = 
            oldItemPosition == newItemPosition
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}