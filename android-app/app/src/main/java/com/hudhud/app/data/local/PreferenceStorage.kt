package com.hudhud.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.hudhud.app.data.local.ImportBatchEntity

/**
 * Utility class for handling app preferences and settings.
 */
class PreferenceStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========== Import Settings ==========

    var lastImportMode: String
        get() = prefs.getString(KEY_LAST_IMPORT_MODE, ImportBatchEntity.MODE_RAW_DATA) 
            ?: ImportBatchEntity.MODE_RAW_DATA
        set(value) = prefs.edit().putString(KEY_LAST_IMPORT_MODE, value).apply()

    var lastSelectedTemplateId: Long
        get() = prefs.getLong(KEY_LAST_TEMPLATE_ID, -1)
        set(value) = prefs.edit().putLong(KEY_LAST_TEMPLATE_ID, value).apply()

    var lastPhoneColumn: String
        get() = prefs.getString(KEY_LAST_PHONE_COLUMN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PHONE_COLUMN, value).apply()

    // ========== Smart Features Settings ==========

    var autoMapEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MAP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_MAP_ENABLED, value).apply()

    var smartFillEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_FILL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_FILL_ENABLED, value).apply()

    var showSuggestionsAutomatically: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SUGGESTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SUGGESTIONS, value).apply()

    // ========== UI Settings ==========

    var messagesPreviewCount: Int
        get() = prefs.getInt(KEY_PREVIEW_COUNT, 50)
        set(value) = prefs.edit().putInt(KEY_PREVIEW_COUNT, value).apply()

    var showAdvancedOptions: Boolean
        get() = prefs.getBoolean(KEY_ADVANCED_OPTIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_ADVANCED_OPTIONS, value).apply()

    // ========== App State ==========

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var lastDatabaseVersion: Int
        get() = prefs.getInt(KEY_DB_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_DB_VERSION, value).apply()

    // ========== Recent Items ==========

    fun addRecentFile(fileUri: String) {
        val recent = getRecentFiles().toMutableList()
        recent.remove(fileUri)
        recent.add(0, fileUri)
        if (recent.size > MAX_RECENT_FILES) {
            recent.removeAt(recent.lastIndex)
        }
        prefs.edit().putStringSet(KEY_RECENT_FILES, recent.toSet()).apply()
    }

    fun getRecentFiles(): List<String> {
        return prefs.getStringSet(KEY_RECENT_FILES, emptySet())?.toList() ?: emptyList()
    }

    fun clearRecentFiles() {
        prefs.edit().remove(KEY_RECENT_FILES).apply()
    }

    // ========== Helpers ==========

    fun resetSmartFeatures() {
        prefs.edit()
            .putBoolean(KEY_AUTO_MAP_ENABLED, true)
            .putBoolean(KEY_SMART_FILL_ENABLED, false)
            .putBoolean(KEY_SHOW_SUGGESTIONS, true)
            .apply()
    }

    fun resetAllSettings() {
        prefs.edit().clear().apply()
    }

    fun exportSettings(): Map<String, Any?> {
        return mapOf(
            "lastImportMode" to lastImportMode,
            "autoMapEnabled" to autoMapEnabled,
            "smartFillEnabled" to smartFillEnabled,
            "showSuggestionsAutomatically" to showSuggestionsAutomatically,
            "messagesPreviewCount" to messagesPreviewCount,
            "showAdvancedOptions" to showAdvancedOptions,
        )
    }

    companion object {
        private const val PREFS_NAME = "hudhud_settings"

        // Keys
        private const val KEY_LAST_IMPORT_MODE = "last_import_mode"
        private const val KEY_LAST_TEMPLATE_ID = "last_template_id"
        private const val KEY_LAST_PHONE_COLUMN = "last_phone_column"
        private const val KEY_AUTO_MAP_ENABLED = "auto_map_enabled"
        private const val KEY_SMART_FILL_ENABLED = "smart_fill_enabled"
        private const val KEY_SHOW_SUGGESTIONS = "show_suggestions"
        private const val KEY_PREVIEW_COUNT = "preview_count"
        private const val KEY_ADVANCED_OPTIONS = "advanced_options"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_DB_VERSION = "db_version"
        private const val KEY_RECENT_FILES = "recent_files"

        private const val MAX_RECENT_FILES = 10
    }
}