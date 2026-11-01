package com.hudhud.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.hudhud.app.data.remote.RemoteEndpoint
import com.hudhud.app.domain.settings.SiteProfile
import com.hudhud.app.domain.settings.SiteProfileValidator
import com.hudhud.app.domain.settings.ThrottlePreset

/**
 * Stores app configuration settings and site connection profiles.
 *
 * Fully supports isolated per-site configurations while preserving backwards
 * compatibility with previous single-profile settings.
 */
class AppSettingsStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    init {
        ensureProfilesInitialized()
        removeDeprecatedProductionPlaceholder()
    }

    var delayBetweenMessagesMs: Long
        get() = getActiveProfile().delayBetweenMessagesMs
        set(value) {
            val active = getActiveProfile()
            saveProfile(active.copy(delayBetweenMessagesMs = value.coerceIn(MIN_DELAY_MS, SiteProfile.MAX_DELAY_MS)))
        }

    var batchSize: Int
        get() = getActiveProfile().batchSize
        set(value) {
            val active = getActiveProfile()
            saveProfile(active.copy(batchSize = value.coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)))
        }

    var maxRetryAttempts: Int
        get() = getActiveProfile().maxRetryAttempts
        set(value) {
            val active = getActiveProfile()
            saveProfile(active.copy(maxRetryAttempts = value.coerceIn(MIN_RETRIES, MAX_RETRIES)))
        }

    fun getAllProfiles(): List<SiteProfile> {
        val raw = prefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
        val list = deserializeProfiles(raw).filterNot(::isDeprecatedProductionPlaceholder)
        return if (list.isNotEmpty()) list else initialProfiles()
    }

    fun getProfile(id: String): SiteProfile? {
        return getAllProfiles().firstOrNull { it.id == id }
    }

    fun getActiveProfile(): SiteProfile {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, SiteProfile.DEFAULT_STAGING_ID)
        val all = getAllProfiles()
        return all.firstOrNull { it.id == activeId && it.isEnabled && isValidProfile(it) }
            ?: all.firstOrNull { it.isEnabled && isValidProfile(it) }
            ?: initialDefaultStagingProfile()
    }

    fun setActiveProfileId(profileId: String): Boolean {
        val profile = getProfile(profileId) ?: return false
        if (!profile.isEnabled || !isValidProfile(profile)) return false
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
        syncLegacyKeys(getActiveProfile())
        return true
    }

    fun saveProfile(profile: SiteProfile): Boolean {
        if (!isValidProfile(profile)) return false

        val profiles = getAllProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        val saved = prefs.edit()
            .putString(KEY_PROFILES_JSON, serializeProfiles(profiles))
            .commit()
        if (saved && profile.id == getActiveProfile().id) {
            syncLegacyKeys(profile)
        }
        return saved
    }

    private fun isValidProfile(profile: SiteProfile): Boolean =
        profile.id.isNotBlank() &&
            SiteProfileValidator.validateName(profile.name) is SiteProfileValidator.ValidationResult.Valid &&
            SiteProfileValidator.validateBaseUrl(profile.baseUrl) is SiteProfileValidator.ValidationResult.Valid &&
            SiteProfileValidator.validateDelayMs(profile.delayBetweenMessagesMs) is SiteProfileValidator.ValidationResult.Valid &&
            SiteProfileValidator.validateBatchSize(profile.batchSize) is SiteProfileValidator.ValidationResult.Valid &&
            SiteProfileValidator.validateMaxRetries(profile.maxRetryAttempts) is SiteProfileValidator.ValidationResult.Valid &&
            SiteProfileValidator.validateConnectTimeout(profile.connectTimeoutSeconds) is SiteProfileValidator.ValidationResult.Valid &&
            SiteProfileValidator.validateReadTimeout(profile.readTimeoutSeconds) is SiteProfileValidator.ValidationResult.Valid &&
            SiteProfileValidator.validateCallTimeout(profile.callTimeoutSeconds) is SiteProfileValidator.ValidationResult.Valid

    fun deleteProfile(profileId: String): Boolean {
        val profiles = getAllProfiles().toMutableList()
        if (profiles.size <= 1) return false // Prevent deleting the last remaining profile
        if (profileId == getActiveProfile().id) return false // Prevent deleting currently active profile

        val removed = profiles.removeAll { it.id == profileId }
        if (!removed) return false

        return prefs.edit()
            .putString(KEY_PROFILES_JSON, serializeProfiles(profiles))
            .commit()
    }

    fun resetProfileToDefaults(profileId: String): SiteProfile? {
        val current = getProfile(profileId) ?: return null
        val reset = current.copy(
            delayBetweenMessagesMs = DEFAULT_DELAY_MS,
            batchSize = DEFAULT_BATCH_SIZE,
            maxRetryAttempts = DEFAULT_MAX_RETRIES,
            preset = ThrottlePreset.BALANCED,
            connectTimeoutSeconds = SiteProfile.DEFAULT_CONNECT_TIMEOUT_SEC,
            readTimeoutSeconds = SiteProfile.DEFAULT_READ_TIMEOUT_SEC,
            callTimeoutSeconds = SiteProfile.DEFAULT_CALL_TIMEOUT_SEC,
            isContractApproved = false,
        )
        return if (saveProfile(reset)) reset else null
    }

    fun resetToDefaults(): Boolean = resetProfileToDefaults(getActiveProfile().id) != null

    fun consumeMigrationWarning(): Boolean {
        if (!prefs.getBoolean(KEY_MIGRATION_WARNING, false)) return false
        prefs.edit().remove(KEY_MIGRATION_WARNING).apply()
        return true
    }

    private fun ensureProfilesInitialized() {
        if (!prefs.contains(KEY_PROFILES_JSON)) {
            val defaults = initialProfiles()
            prefs.edit()
                .putString(KEY_PROFILES_JSON, serializeProfiles(defaults))
                .putString(KEY_ACTIVE_PROFILE_ID, SiteProfile.DEFAULT_STAGING_ID)
                .apply()
            syncLegacyKeys(defaults.first())
        }
    }

    private fun removeDeprecatedProductionPlaceholder() {
        val raw = prefs.getString(KEY_PROFILES_JSON, null) ?: return
        val profiles = deserializeProfiles(raw)
        val retained = profiles.filterNot(::isDeprecatedProductionPlaceholder)
        if (retained.size == profiles.size) return

        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, SiteProfile.DEFAULT_STAGING_ID)
        val editor = prefs.edit().putString(KEY_PROFILES_JSON, serializeProfiles(retained))
        if (profiles.any { isDeprecatedProductionPlaceholder(it) && it.id == activeId }) {
            editor.putString(KEY_ACTIVE_PROFILE_ID, SiteProfile.DEFAULT_STAGING_ID)
        }
        if (editor.commit() && profiles.any { isDeprecatedProductionPlaceholder(it) && it.id == activeId }) {
            retained.firstOrNull { it.id == SiteProfile.DEFAULT_STAGING_ID }?.let(::syncLegacyKeys)
        }
    }

    private fun isDeprecatedProductionPlaceholder(profile: SiteProfile): Boolean =
        profile.id == DEPRECATED_PRODUCTION_PROFILE_ID &&
            profile.name == DEPRECATED_PRODUCTION_PROFILE_NAME &&
            profile.baseUrl == DEPRECATED_PRODUCTION_BASE_URL &&
            !profile.isEnabled

    private fun initialProfiles(): List<SiteProfile> {
        val legacyDelay = if (prefs.contains(KEY_DELAY_MS)) prefs.getLong(KEY_DELAY_MS, DEFAULT_DELAY_MS) else null
        val legacyBatch = if (prefs.contains(KEY_BATCH_SIZE)) prefs.getInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE) else null
        val legacyRetries = if (prefs.contains(KEY_MAX_RETRIES)) prefs.getInt(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES) else null
        val normalizedDelay = (legacyDelay ?: DEFAULT_DELAY_MS)
            .coerceIn(MIN_DELAY_MS, SiteProfile.MAX_DELAY_MS)
        val normalizedBatch = (legacyBatch ?: DEFAULT_BATCH_SIZE).coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)
        val normalizedRetries = (legacyRetries ?: DEFAULT_MAX_RETRIES).coerceIn(MIN_RETRIES, MAX_RETRIES)
        if (legacyDelay != null && normalizedDelay != legacyDelay ||
            legacyBatch != null && normalizedBatch != legacyBatch ||
            legacyRetries != null && normalizedRetries != legacyRetries
        ) {
            prefs.edit().putBoolean(KEY_MIGRATION_WARNING, true).apply()
        }

        val staging = SiteProfile(
            id = SiteProfile.DEFAULT_STAGING_ID,
            name = "بيئة الاختبار (Staging)",
            baseUrl = RemoteEndpoint.BASE_URL,
            delayBetweenMessagesMs = normalizedDelay,
            batchSize = normalizedBatch,
            maxRetryAttempts = normalizedRetries,
            isEnabled = true,
            isContractApproved = false,
        )

        return listOf(staging)
    }

    private fun initialDefaultStagingProfile(): SiteProfile = SiteProfile(
        id = SiteProfile.DEFAULT_STAGING_ID,
        name = "بيئة الاختبار (Staging)",
        baseUrl = RemoteEndpoint.BASE_URL,
        isContractApproved = false,
    )

    private fun syncLegacyKeys(profile: SiteProfile) {
        prefs.edit()
            .putLong(KEY_DELAY_MS, profile.delayBetweenMessagesMs)
            .putInt(KEY_BATCH_SIZE, profile.batchSize)
            .putInt(KEY_MAX_RETRIES, profile.maxRetryAttempts)
            .apply()
    }

    private fun serializeProfile(p: SiteProfile): String = buildString {
        append("{")
        append("\"id\":\"").append(escape(p.id)).append("\",")
        append("\"name\":\"").append(escape(p.name)).append("\",")
        append("\"baseUrl\":\"").append(escape(p.baseUrl)).append("\",")
        append("\"delayBetweenMessagesMs\":").append(p.delayBetweenMessagesMs).append(",")
        append("\"batchSize\":").append(p.batchSize).append(",")
        append("\"maxRetryAttempts\":").append(p.maxRetryAttempts).append(",")
        append("\"connectTimeoutSeconds\":").append(p.connectTimeoutSeconds).append(",")
        append("\"readTimeoutSeconds\":").append(p.readTimeoutSeconds).append(",")
        append("\"callTimeoutSeconds\":").append(p.callTimeoutSeconds).append(",")
        append("\"isEnabled\":").append(p.isEnabled).append(",")
        append("\"isContractApproved\":").append(p.isContractApproved).append(",")
        append("\"preset\":\"").append(p.preset.name).append("\"")
        append("}")
    }

    private fun serializeProfiles(profiles: List<SiteProfile>): String =
        "[" + profiles.joinToString(",") { serializeProfile(it) } + "]"

    private fun deserializeProfiles(json: String): List<SiteProfile> {
        val list = mutableListOf<SiteProfile>()
        var i = 0
        while (i < json.length) {
            val open = json.indexOf('{', i)
            if (open == -1) break
            val close = findMatchingBrace(json, open)
            if (close == -1) break
            val objJson = json.substring(open + 1, close)
            parseSingleProfile(objJson)?.let { list.add(it) }
            i = close + 1
        }
        return list
    }

    private fun parseSingleProfile(content: String): SiteProfile? {
        val map = mutableMapOf<String, String>()
        var idx = 0
        while (idx < content.length) {
            val keyStart = content.indexOf('"', idx)
            if (keyStart == -1) break
            val keyEnd = content.indexOf('"', keyStart + 1)
            if (keyEnd == -1) break
            val key = content.substring(keyStart + 1, keyEnd)

            val colon = content.indexOf(':', keyEnd + 1)
            if (colon == -1) break

            var valStart = colon + 1
            while (valStart < content.length && content[valStart].isWhitespace()) valStart++
            if (valStart >= content.length) break

            val value: String
            if (content[valStart] == '"') {
                var quoteEnd = valStart + 1
                while (quoteEnd < content.length) {
                    if (content[quoteEnd] == '"' && !isEscaped(content, quoteEnd)) break
                    quoteEnd++
                }
                if (quoteEnd >= content.length) break
                value = unescape(content.substring(valStart + 1, quoteEnd))
                idx = quoteEnd + 1
            } else {
                var endVal = valStart
                while (endVal < content.length && content[endVal] != ',' && !content[endVal].isWhitespace()) {
                    endVal++
                }
                value = content.substring(valStart, endVal).trim()
                idx = endVal
            }
            map[key] = value
            val comma = content.indexOf(',', idx)
            if (comma != -1) {
                idx = comma + 1
            }
        }

        val id = map["id"] ?: return null
        val name = map["name"] ?: return null
        val baseUrl = map["baseUrl"] ?: return null
        val delay = map["delayBetweenMessagesMs"]?.toLongOrNull() ?: SiteProfile.DEFAULT_DELAY_MS
        val batch = map["batchSize"]?.toIntOrNull() ?: SiteProfile.DEFAULT_BATCH_SIZE
        val retries = map["maxRetryAttempts"]?.toIntOrNull() ?: SiteProfile.DEFAULT_MAX_RETRIES
        val connectTimeout = map["connectTimeoutSeconds"]?.toIntOrNull() ?: SiteProfile.DEFAULT_CONNECT_TIMEOUT_SEC
        val readTimeout = map["readTimeoutSeconds"]?.toIntOrNull() ?: SiteProfile.DEFAULT_READ_TIMEOUT_SEC
        val callTimeout = map["callTimeoutSeconds"]?.toIntOrNull() ?: SiteProfile.DEFAULT_CALL_TIMEOUT_SEC
        val isEnabled = map["isEnabled"]?.toBooleanStrictOrNull() ?: true
        val isContractApproved = map["isContractApproved"]?.toBooleanStrictOrNull() ?: false
        val presetName = map["preset"] ?: ""
        val preset = try {
            ThrottlePreset.valueOf(presetName)
        } catch (e: Exception) {
            ThrottlePreset.fromValues(delay, retries)
        }

        return SiteProfile(
            id = id,
            name = name,
            baseUrl = baseUrl,
            delayBetweenMessagesMs = delay,
            batchSize = batch,
            maxRetryAttempts = retries,
            connectTimeoutSeconds = connectTimeout,
            readTimeoutSeconds = readTimeout,
            callTimeoutSeconds = callTimeout,
            isEnabled = isEnabled,
            isContractApproved = isContractApproved,
            preset = preset,
        )
    }

    private fun findMatchingBrace(json: String, openIndex: Int): Int {
        var depth = 0
        var inString = false
        for (i in openIndex until json.length) {
            val c = json[i]
            if (c == '"' && !isEscaped(json, i)) {
                inString = !inString
            } else if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun isEscaped(text: String, index: Int): Boolean {
        var backslashes = 0
        var cursor = index - 1
        while (cursor >= 0 && text[cursor] == '\\') {
            backslashes++
            cursor--
        }
        return backslashes % 2 == 1
    }

    private fun escape(text: String): String = buildString {
        text.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }

    private fun unescape(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val character = text[index++]
            if (character != '\\' || index >= text.length) {
                append(character)
                continue
            }
            when (val escaped = text[index++]) {
                '"' -> append('"')
                '\\' -> append('\\')
                '/' -> append('/')
                'b' -> append('\b')
                'f' -> append('\u000C')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'u' -> {
                    if (index + 4 <= text.length) {
                        val codePoint = text.substring(index, index + 4).toIntOrNull(16)
                        if (codePoint != null) {
                            append(codePoint.toChar())
                            index += 4
                        } else {
                            append('\\').append(escaped)
                        }
                    } else {
                        append('\\').append(escaped)
                    }
                }
                else -> append('\\').append(escaped)
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "hudhud_app_settings"
        private const val KEY_PROFILES_JSON = "site_profiles_json"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_DELAY_MS = "delay_between_messages_ms"
        private const val KEY_BATCH_SIZE = "batch_size"
        private const val KEY_MAX_RETRIES = "max_retry_attempts"
        private const val KEY_MIGRATION_WARNING = "settings_migration_adjusted"
        private const val DEPRECATED_PRODUCTION_PROFILE_ID = "default_production"
        private const val DEPRECATED_PRODUCTION_PROFILE_NAME = "بيئة الإنتاج (غير مفعّلة - بانتظار العقد)"
        private const val DEPRECATED_PRODUCTION_BASE_URL = "https://api.alhudhudai.com"

        const val DEFAULT_DELAY_MS = 2000L // 2 seconds
        const val MIN_DELAY_MS = 0L

        const val DEFAULT_BATCH_SIZE = SiteProfile.DEFAULT_BATCH_SIZE
        const val MIN_BATCH_SIZE = SiteProfile.MIN_BATCH_SIZE
        const val MAX_BATCH_SIZE = SiteProfile.MAX_BATCH_SIZE

        const val DEFAULT_MAX_RETRIES = SiteProfile.DEFAULT_MAX_RETRIES
        const val MIN_RETRIES = SiteProfile.MIN_RETRIES
        const val MAX_RETRIES = SiteProfile.MAX_RETRIES
    }
}
