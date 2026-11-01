package com.hudhud.app.data.ai

import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.ColumnType
import java.util.regex.Pattern

/**
 * Analyzes columns in a file and detects their types automatically.
 * Uses pattern matching and heuristics to identify column content.
 */
class ColumnAnalyzer {

    // Detection patterns
    private val phonePatterns = listOf(
        Pattern.compile("^\\+[0-9]{9,15}$"),           // +966501234567
        Pattern.compile("^00[0-9]{9,15}$"),            // 00966501234567
        Pattern.compile("^0[0-9]{9,14}$"),             // 966501234567 or 0501234567
        Pattern.compile("^5[0-9]{8}$"),                // Saudi mobile (05xxxxxxxx)
        Pattern.compile("^\\+?[1-9]\\d{6,14}$"),       // General international
    )

    private val emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private val amountPatterns = listOf(
        Pattern.compile("^\\d+(\\.\\d{1,2})?$"),       // 100.50
        Pattern.compile("^\\d{1,3}(,\\d{3})*(\\.\\d+)?$"), // 1,000.50
        Pattern.compile("^[\\d\\s]+(\\.\\d+)?$"),      // 1000 50
    )

    private val datePatterns = listOf(
        Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$"),     // 2024-01-15
        Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$"),     // 15/01/2024
        Pattern.compile("^\\d{2}-\\d{2}-\\d{4}$"),     // 15-01-2024
        Pattern.compile("^\\d{1,2}\\s+(يناير|فبراير|مارس|أبريل|مايو|يونيو|يوليو|أغسطس|سبتمبر|أكتوبر|نوفمبر|ديسمبر)\\s+\\d{4}$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\d{1,2}\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{4}$", Pattern.CASE_INSENSITIVE),
    )

    // Keywords for name detection (English and Arabic)
    private val nameKeywords = listOf(
        "name", "fname", "first_name", "firstname", "full_name", "fullname",
        "last_name", "lastname", "surname", "customer", "client", "person",
        "member", "user", "owner", "contact", "اسم", "الاسم", "مستخدم", "عميل"
    )

    // Keywords for phone column detection
    private val phoneKeywords = listOf(
        "phone", "mobile", "cell", "tel", "telephone", "contact_no", "contact_number",
        "رقم_الهاتف", "الهاتف", "موبايل", "جوال"
    )

    // Keywords for amount detection
    private val amountKeywords = listOf(
        "amount", "price", "cost", "total", "balance", "due", "invoice_amount",
        "payment", "fee", "قيمة", "مبلغ", "سعر", "إجمالي", "رصيد"
    )

    // Keywords for date detection
    private val dateKeywords = listOf(
        "date", "due_date", "duedate", "birth_date", "created_at", "updated_at",
        "تاريخ", "تاريخ_الاستحقاق", "تاريخ_الميلاد"
    )

    // Keywords for message/text detection
    private val messageKeywords = listOf(
        "message", "text", "body", "content", "sms", "note", "notes",
        "message_text", "نص", "رسالة", "محتوى"
    )

    /**
     * Analyze a column and detect its type
     * @param columnName The column header name
     * @param sampleValues Sample values from the column (first 10-20 rows)
     * @return ColumnInfo with detected type and confidence
     */
    fun analyzeColumn(columnName: String, sampleValues: List<String>): ColumnInfo {
        val normalizedName = columnName.lowercase().trim().replace("_", " ").replace("-", " ")

        // Check for keywords in column name first (high confidence)
        val nameBasedType = detectTypeFromName(normalizedName)
        val nameConfidence = if (nameBasedType != ColumnType.UNKNOWN) 0.85f else 0f

        // Analyze sample values
        val (valueBasedType, valueConfidence) = detectTypeFromValues(sampleValues)

        // Combine results - use the higher confidence
        val finalType: ColumnType
        val finalConfidence: Float

        if (nameBasedType == valueBasedType) {
            // Both agree - high confidence
            finalType = nameBasedType
            finalConfidence = maxOf(nameConfidence, valueConfidence)
        } else if (nameConfidence > valueConfidence) {
            finalType = nameBasedType
            finalConfidence = nameConfidence * 0.9f // Slight discount for not confirming from values
        } else {
            finalType = valueBasedType
            finalConfidence = valueConfidence * 0.8f
        }

        // Calculate valid count
        val validCount = sampleValues.count { isValidValue(it, finalType) }

        return ColumnInfo(
            name = columnName,
            displayName = formatDisplayName(columnName),
            type = finalType,
            confidence = finalConfidence,
            sampleValues = sampleValues.take(5),
            validCount = validCount,
            totalCount = sampleValues.size,
        )
    }

    /**
     * Analyze all columns in a file
     * @param columns Map of column name to sample values
     * @return List of ColumnInfo for each column
     */
    fun analyzeAllColumns(columns: Map<String, List<String>>): List<ColumnInfo> {
        return columns.map { (columnName, values) ->
            analyzeColumn(columnName, values)
        }
    }

    /**
     * Detect column type from header name using keywords
     */
    private fun detectTypeFromName(normalizedName: String): ColumnType {
        return when {
            phoneKeywords.any { normalizedName.contains(it) } -> ColumnType.PHONE
            messageKeywords.any { normalizedName.contains(it) } -> ColumnType.TEXT
            nameKeywords.any { normalizedName.contains(it) } -> ColumnType.NAME
            amountKeywords.any { normalizedName.contains(it) } -> ColumnType.AMOUNT
            dateKeywords.any { normalizedName.contains(it) } -> ColumnType.DATE
            emailPattern.matcher(normalizedName).matches() -> ColumnType.EMAIL
            else -> ColumnType.UNKNOWN
        }
    }

    /**
     * Detect column type from sample values
     */
    private fun detectTypeFromValues(values: List<String>): Pair<ColumnType, Float> {
        if (values.isEmpty() || values.all { it.isBlank() }) {
            return Pair(ColumnType.UNKNOWN, 0f)
        }

        val nonEmptyValues = values.filter { it.isNotBlank() }
        if (nonEmptyValues.isEmpty()) {
            return Pair(ColumnType.UNKNOWN, 0f)
        }

        // Check each type
        val phoneMatch = nonEmptyValues.count { matchesPhone(it) }.toFloat() / nonEmptyValues.size
        if (phoneMatch > 0.7f) return Pair(ColumnType.PHONE, phoneMatch)

        val emailMatch = nonEmptyValues.count { matchesEmail(it) }.toFloat() / nonEmptyValues.size
        if (emailMatch > 0.7f) return Pair(ColumnType.EMAIL, emailMatch)

        val amountMatch = nonEmptyValues.count { matchesAmount(it) }.toFloat() / nonEmptyValues.size
        if (amountMatch > 0.7f) return Pair(ColumnType.AMOUNT, amountMatch)

        val dateMatch = nonEmptyValues.count { matchesDate(it) }.toFloat() / nonEmptyValues.size
        if (dateMatch > 0.7f) return Pair(ColumnType.DATE, dateMatch)

        // Check for long text (likely message)
        val textMatch = nonEmptyValues.count { it.length > 20 }.toFloat() / nonEmptyValues.size
        if (textMatch > 0.5f) return Pair(ColumnType.TEXT, textMatch * 0.8f)

        // Check for name-like values (short text, not numbers)
        val nameMatch = nonEmptyValues.count { matchesName(it) }.toFloat() / nonEmptyValues.size
        if (nameMatch > 0.7f) return Pair(ColumnType.NAME, nameMatch)

        return Pair(ColumnType.UNKNOWN, 0f)
    }

    private fun matchesPhone(value: String): Boolean {
        val cleaned = value.replace(Regex("[\\s\\-()]"), "")
        return phonePatterns.any { it.matcher(cleaned).matches() }
    }

    private fun matchesEmail(value: String): Boolean {
        return emailPattern.matcher(value.trim()).matches()
    }

    private fun matchesAmount(value: String): Boolean {
        return amountPatterns.any { it.matcher(value.trim()).matches() }
    }

    private fun matchesDate(value: String): Boolean {
        return datePatterns.any { it.matcher(value.trim()).matches() }
    }

    private fun matchesName(value: String): Boolean {
        val trimmed = value.trim()
        // Names are typically 2-50 characters, contain letters and spaces, no numbers
        return trimmed.length in 2..50 &&
                trimmed.any { it.isLetter() } &&
                !trimmed.any { it.isDigit() }
    }

    private fun isValidValue(value: String, type: ColumnType): Boolean {
        return when (type) {
            ColumnType.PHONE -> matchesPhone(value)
            ColumnType.EMAIL -> matchesEmail(value)
            ColumnType.AMOUNT -> matchesAmount(value)
            ColumnType.DATE -> matchesDate(value)
            ColumnType.NAME -> matchesName(value)
            ColumnType.TEXT -> value.isNotBlank() && value.length >= 3
            else -> value.isNotBlank()
        }
    }

    private fun formatDisplayName(name: String): String {
        val normalized = name.trim()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")

        if (normalized.isEmpty()) return normalized

        val hasArabicScript = normalized.any { ch ->
            ch in '\u0600'..'\u06FF' ||
                ch in '\u0750'..'\u077F' ||
                ch in '\u08A0'..'\u08FF' ||
                ch in '\uFB50'..'\uFDFF' ||
                ch in '\uFE70'..'\uFEFF'
        }

        if (hasArabicScript) return normalized

        return normalized.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}