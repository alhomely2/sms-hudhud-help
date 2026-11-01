package com.hudhud.app.domain.ready

import com.hudhud.app.domain.model.MessageDraft

class ReadyMessageProcessor {
    fun process(
        rows: List<List<String?>>,
        phoneColumnIndex: Int,
        messageColumnIndex: Int,
    ): List<MessageDraft> {
        require(phoneColumnIndex >= 0) { "Phone column index must not be negative" }
        require(messageColumnIndex >= 0) { "Message column index must not be negative" }
        require(phoneColumnIndex != messageColumnIndex) { "Phone and message columns must differ" }

        return rows.mapIndexed { index, row ->
            val phone = row.getOrNull(phoneColumnIndex).orEmpty().trim()
            val message = row.getOrNull(messageColumnIndex).orEmpty().trim()
            val errors = buildList {
                if (phone.isBlank()) add("رقم الهاتف فارغ")
                if (message.isBlank()) add("نص الرسالة فارغ")
            }

            MessageDraft(
                sourceRow = index + 1,
                phone = phone,
                text = message,
                errors = errors,
            )
        }
    }
}
