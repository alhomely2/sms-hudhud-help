package com.hudhud.app.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hudhud.app.R
import com.hudhud.app.databinding.ItemMessagePreviewBinding
import com.hudhud.app.domain.model.MessageDraft

/**
 * Adapter for displaying message previews in a RecyclerView.
 */
class MessagePreviewAdapter : ListAdapter<MessageDraft, MessagePreviewAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessagePreviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        private val binding: ItemMessagePreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageDraft) {
            binding.rowNumberText.text = message.sourceRow.toString()
            binding.phoneText.text = message.phone
            binding.messageText.text = message.text

            // Show status indicator
            if (message.isValid) {
                binding.statusIndicator.setImageResource(R.drawable.ic_check)
                binding.statusIndicator.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.hudhud_success)
                )
                binding.errorText.visibility = android.view.View.GONE
            } else {
                binding.statusIndicator.setImageResource(R.drawable.ic_close)
                binding.statusIndicator.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.hudhud_error)
                )
                
                // Show errors
                if (message.errors.isNotEmpty()) {
                    binding.errorText.visibility = android.view.View.VISIBLE
                    binding.errorText.text = message.errors.joinToString(", ")
                } else {
                    binding.errorText.visibility = android.view.View.GONE
                }
            }
        }
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<MessageDraft>() {
        override fun areItemsTheSame(oldItem: MessageDraft, newItem: MessageDraft): Boolean {
            return oldItem.sourceRow == newItem.sourceRow
        }

        override fun areContentsTheSame(oldItem: MessageDraft, newItem: MessageDraft): Boolean {
            return oldItem == newItem
        }
    }
}