package com.hudhud.app.data.local

import android.content.SharedPreferences
import com.hudhud.app.domain.settings.SiteProfile
import com.hudhud.app.domain.settings.ThrottlePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStoreTest {

    @Test
    fun `initializes only the confirmed staging profile`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)

        val profiles = store.getAllProfiles()
        assertEquals(1, profiles.size)

        val staging = store.getActiveProfile()
        assertEquals(SiteProfile.DEFAULT_STAGING_ID, staging.id)
        assertTrue(staging.isEnabled)
        assertEquals(2000L, staging.delayBetweenMessagesMs)
        assertEquals(50, staging.batchSize)
        assertEquals(3, staging.maxRetryAttempts)
    }

    @Test
    fun `migrates legacy preferences to default staging profile`() {
        val prefs = FakeSharedPreferences().apply {
            putLong("delay_between_messages_ms", 4000L)
            putInt("batch_size", 100)
            putInt("max_retry_attempts", 5)
        }
        val store = AppSettingsStore(prefs)

        val staging = store.getActiveProfile()
        assertEquals(4000L, staging.delayBetweenMessagesMs)
        assertEquals(100, staging.batchSize)
        assertEquals(5, staging.maxRetryAttempts)
    }

    @Test
    fun `migration safely bounds legacy values and reports the adjustment once`() {
        val prefs = FakeSharedPreferences().apply {
            putLong("delay_between_messages_ms", 90_000L)
            putInt("batch_size", 600)
            putInt("max_retry_attempts", 0)
        }
        val store = AppSettingsStore(prefs)

        assertEquals(SiteProfile.MAX_DELAY_MS, store.getActiveProfile().delayBetweenMessagesMs)
        assertEquals(SiteProfile.MAX_BATCH_SIZE, store.getActiveProfile().batchSize)
        assertEquals(SiteProfile.MIN_RETRIES, store.getActiveProfile().maxRetryAttempts)
        assertTrue(store.consumeMigrationWarning())
        assertFalse(store.consumeMigrationWarning())
    }

    @Test
    fun `removes deprecated unconfirmed production placeholder during migration`() {
        val prefs = FakeSharedPreferences().apply {
            putString(
                "site_profiles_json",
                """[{"id":"default_staging","name":"بيئة الاختبار (Staging)","baseUrl":"https://staging.mytestdomain.club","delayBetweenMessagesMs":2000,"batchSize":50,"maxRetryAttempts":3,"connectTimeoutSeconds":15,"readTimeoutSeconds":20,"callTimeoutSeconds":30,"isEnabled":true,"preset":"BALANCED"},{"id":"default_production","name":"بيئة الإنتاج (غير مفعّلة - بانتظار العقد)","baseUrl":"https://api.alhudhudai.com","delayBetweenMessagesMs":2000,"batchSize":50,"maxRetryAttempts":3,"connectTimeoutSeconds":15,"readTimeoutSeconds":20,"callTimeoutSeconds":30,"isEnabled":false,"preset":"BALANCED"}]""",
            )
            putString("active_profile_id", "default_production")
        }

        val store = AppSettingsStore(prefs)

        assertEquals(listOf(SiteProfile.DEFAULT_STAGING_ID), store.getAllProfiles().map { it.id })
        assertEquals(SiteProfile.DEFAULT_STAGING_ID, store.getActiveProfile().id)
        assertEquals(SiteProfile.DEFAULT_STAGING_ID, prefs.getString("active_profile_id", null))
        assertFalse(prefs.getString("site_profiles_json", null).orEmpty().contains("api.alhudhudai.com"))
    }

    @Test
    fun `switching active profile updates getActiveProfile and legacy keys`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)

        val secondProfile = SiteProfile(
            id = "custom_site",
            name = "الموقع البديل",
            baseUrl = "https://api.example.com",
        )
        val disabledProfile = secondProfile.copy(id = "disabled_site", isEnabled = false)
        assertTrue(store.saveProfile(secondProfile))
        assertTrue(store.saveProfile(disabledProfile))
        assertTrue(store.setActiveProfileId(secondProfile.id))
        assertEquals(secondProfile.id, store.getActiveProfile().id)

        // Invalid profile id returns false
        assertFalse(store.setActiveProfileId("non_existent"))
        assertFalse(store.setActiveProfileId("disabled_site"))
    }

    @Test
    fun `saving a new profile persists and is retrievable`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)

        val customProfile = SiteProfile(
            id = "custom_client_1",
            name = "فرع جدة",
            baseUrl = "https://jeddah.example.com",
            delayBetweenMessagesMs = 1500L,
            batchSize = 80,
            maxRetryAttempts = 4,
            preset = ThrottlePreset.CUSTOM,
        )

        assertTrue(store.saveProfile(customProfile))

        val loaded = store.getProfile("custom_client_1")
        assertNotNull(loaded)
        assertEquals("فرع جدة", loaded?.name)
        assertEquals("https://jeddah.example.com", loaded?.baseUrl)
        assertEquals(1500L, loaded?.delayBetweenMessagesMs)
        assertEquals(80, loaded?.batchSize)
    }

    @Test
    fun `profile strings round trip escaped characters and legacy batch limit`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)
        val profile = SiteProfile(
            id = "quoted_profile",
            name = "فرع \"جدة\" \\\\ الرئيسي\nالمبنى",
            baseUrl = "https://jeddah.example.com",
            batchSize = 500,
        )

        assertTrue(store.saveProfile(profile))

        val loaded = store.getProfile(profile.id)
        assertEquals(profile.name, loaded?.name)
        assertEquals(500, loaded?.batchSize)
    }

    @Test
    fun `cannot delete active profile or last profile`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)

        // Attempting to delete active profile (default_staging) fails
        assertFalse(store.deleteProfile(SiteProfile.DEFAULT_STAGING_ID))

        val secondProfile = SiteProfile(
            id = "custom_site",
            name = "الموقع البديل",
            baseUrl = "https://api.example.com",
        )
        assertTrue(store.saveProfile(secondProfile))
        assertTrue(store.deleteProfile(secondProfile.id))
        assertEquals(1, store.getAllProfiles().size)

        // Cannot delete last remaining profile
        assertFalse(store.deleteProfile(SiteProfile.DEFAULT_STAGING_ID))
    }

    @Test
    fun `resetProfileToDefaults restores profile to standard parameters`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)

        val custom = store.getActiveProfile().copy(
            delayBetweenMessagesMs = 5000L,
            batchSize = 150,
            maxRetryAttempts = 8,
        )
        store.saveProfile(custom)

        val reset = store.resetProfileToDefaults(custom.id) ?: error("Expected reset to persist")
        assertEquals(SiteProfile.DEFAULT_DELAY_MS, reset.delayBetweenMessagesMs)
        assertEquals(SiteProfile.DEFAULT_BATCH_SIZE, reset.batchSize)
        assertEquals(SiteProfile.DEFAULT_MAX_RETRIES, reset.maxRetryAttempts)
    }

    /** In-memory fake SharedPreferences for deterministic local testing. */
    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any>()

        fun putLong(key: String, value: Long) = map.put(key, value)
        fun putInt(key: String, value: Int) = map.put(key, value)
        fun putString(key: String, value: String) = map.put(key, value)

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val storage: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val uncommitted = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) if (value != null) uncommitted[key] = value else uncommitted.remove(key)
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) uncommitted[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) uncommitted[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) uncommitted[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) uncommitted[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) uncommitted[key] = this }
            override fun clear(): SharedPreferences.Editor = apply { storage.clear(); uncommitted.clear() }
            override fun commit(): Boolean {
                for ((k, v) in uncommitted) {
                    if (v === this) storage.remove(k) else if (v != null) storage[k] = v
                }
                uncommitted.clear()
                return true
            }
            override fun apply() { commit() }
        }
    }
}
