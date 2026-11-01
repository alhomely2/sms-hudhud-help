package com.hudhud.app.domain.ready

import java.text.Normalizer
import java.util.Locale

data class ReadyColumnMapping(
    val phoneColumnIndex: Int,
    val messageColumnIndex: Int,
    val phoneColumnName: String,
    val messageColumnName: String,
)

class ReadyColumnMappingException(message: String) : Exception(message)

class ReadyColumnMappingResolver {
    fun resolve(
        headers: List<String>,
        phoneColumnName: String? = null,
        messageColumnName: String? = null,
    ): ReadyColumnMapping {
        val phoneIndex = findColumn(
            headers = headers,
            requestedName = phoneColumnName,
            aliases = PHONE_ALIASES,
            label = "رقم الهاتف",
        )
        val messageIndex = findColumn(
            headers = headers,
            requestedName = messageColumnName,
            aliases = MESSAGE_ALIASES,
            label = "نص الرسالة",
        )
        if (phoneIndex == messageIndex) {
            throw ReadyColumnMappingException("يجب اختيار عمودين مختلفين للهاتف والرسالة.")
        }

        return ReadyColumnMapping(
            phoneColumnIndex = phoneIndex,
            messageColumnIndex = messageIndex,
            phoneColumnName = headers[phoneIndex],
            messageColumnName = headers[messageIndex],
        )
    }

    fun hasKnownColumns(headers: List<String>): Boolean =
        findAliasIndex(headers, PHONE_ALIASES) != null &&
            findAliasIndex(headers, MESSAGE_ALIASES) != null

    private fun findColumn(
        headers: List<String>,
        requestedName: String?,
        aliases: Set<String>,
        label: String,
    ): Int {
        val matches = if (requestedName.isNullOrBlank()) {
            headers.indices.filter { normalize(headers[it]) in aliases }
        } else {
            val requested = normalize(requestedName)
            headers.indices.filter { normalize(headers[it]) == requested }
        }

        return when (matches.size) {
            1 -> matches.single()
            0 -> throw ReadyColumnMappingException(
                "لم يتم العثور على عمود $label${requestedName?.let { ": $it" }.orEmpty()}.",
            )
            else -> throw ReadyColumnMappingException("يوجد أكثر من عمود مطابق لـ $label.")
        }
    }

    private fun findAliasIndex(headers: List<String>, aliases: Set<String>): Int? {
        val matches = headers.indices.filter { normalize(headers[it]) in aliases }
        return matches.singleOrNull()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filter(Char::isLetterOrDigit)

    private companion object {
        val PHONE_ALIASES = setOf(
            "mobileno",
            "mobilenumber",
            "mobile",
            "phoneno",
            "phonenumber",
            "phone",
            "رقمالهاتف",
            "الهاتف",
            "رقمالجوال",
            "الجوال",
        )
        val MESSAGE_ALIASES = setOf(
            "smstxt",
            "sms",
            "message",
            "messagetext",
            "text",
            "نصالرسالة",
            "الرسالة",
            "محتوىالرسالة",
        )
    }
}
