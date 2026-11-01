package com.hudhud.app.domain.send

import android.content.SharedPreferences
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.domain.settings.SiteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformSendGuardTest {
    @Test
    fun `approve only when profile is enabled and contract is approved`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)
        val profile = SiteProfile(
            id = "approved_site",
            name = "Approved site",
            baseUrl = "https://api.example.com",
            isEnabled = true,
            isContractApproved = true,
        )
        store.saveProfile(profile)
        store.setActiveProfileId(profile.id)

        val guard = PlatformSendGuard(
            appSettings = store,
            apiKeyLookup = { "tenant.secret" },
            contractApprovedProvider = { true },
        )

        val result = guard.evaluate(profile.id)
        assertTrue(result.canProceed)
        assertEquals(SendGateStatus.APPROVED, result.status)
    }

    @Test
    fun `block when no profile exists`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)
        val guard = PlatformSendGuard(
            appSettings = store,
            apiKeyLookup = { "tenant.secret" },
            contractApprovedProvider = { true },
        )

        val result = guard.evaluate("missing")
        assertFalse(result.canProceed)
        assertEquals(SendGateStatus.BLOCKED_MISSING_PROFILE, result.status)
    }

    @Test
    fun `block when URL is not https`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)
        val serialized = """
            [{
              "id":"bad_url",
              "name":"Bad URL",
              "baseUrl":"http://api.example.com",
              "delayBetweenMessagesMs":2000,
              "batchSize":50,
              "maxRetryAttempts":3,
              "connectTimeoutSeconds":15,
              "readTimeoutSeconds":20,
              "callTimeoutSeconds":30,
              "isEnabled":true,
              "preset":"BALANCED"
            }]
        """.trimIndent()
        prefs.edit().putString("site_profiles_json", serialized).putString("active_profile_id", "bad_url").commit()

        val guard = PlatformSendGuard(
            appSettings = store,
            apiKeyLookup = { "tenant.secret" },
            contractApprovedProvider = { true },
        )

        val result = guard.evaluate("bad_url")
        assertFalse(result.canProceed)
        assertEquals(SendGateStatus.BLOCKED_INVALID_URL, result.status)
    }

    @Test
    fun `block when api key is missing`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)
        val profile = SiteProfile(
            id = "api_missing",
            name = "API Missing",
            baseUrl = "https://api.example.com",
        )
        store.saveProfile(profile)
        store.setActiveProfileId(profile.id)

        val guard = PlatformSendGuard(
            appSettings = store,
            apiKeyLookup = { null },
            contractApprovedProvider = { true },
        )

        val result = guard.evaluate(profile.id)
        assertFalse(result.canProceed)
        assertEquals(SendGateStatus.BLOCKED_MISSING_API_KEY, result.status)
    }

    @Test
    fun `block when platform contract is not approved`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)
        val profile = SiteProfile(
            id = "pending_contract",
            name = "Pending contract",
            baseUrl = "https://api.example.com",
        )
        store.saveProfile(profile)
        store.setActiveProfileId(profile.id)

        val guard = PlatformSendGuard(
            appSettings = store,
            apiKeyLookup = { "tenant.secret" },
            contractApprovedProvider = { false },
        )

        val result = guard.evaluate(profile.id)
        assertFalse(result.canProceed)
        assertEquals(SendGateStatus.BLOCKED_UNAPPROVED_CONTRACT, result.status)
    }

    @Test
    fun `block when stored profile contract flag is false even if callback says true`() {
        val prefs = FakeSharedPreferences()
        val store = AppSettingsStore(prefs)
        val profile = SiteProfile(
            id = "approval_flag_missing",
            name = "Approval flag missing",
            baseUrl = "https://api.example.com",
            isContractApproved = false,
        )
        store.saveProfile(profile)
        store.setActiveProfileId(profile.id)

        val guard = PlatformSendGuard(
            appSettings = store,
            apiKeyLookup = { "tenant.secret" },
            contractApprovedProvider = { true },
        )

        val result = guard.evaluate(profile.id)
        assertFalse(result.canProceed)
        assertEquals(SendGateStatus.BLOCKED_UNAPPROVED_CONTRACT, result.status)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class EditorImpl(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                map[key ?: ""] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                map[key ?: ""] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                map[key ?: ""] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                map[key ?: ""] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                map[key ?: ""] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                map[key ?: ""] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                map.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                map.clear()
                return this
            }

            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
    }
}
