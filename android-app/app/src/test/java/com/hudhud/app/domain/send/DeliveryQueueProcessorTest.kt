package com.hudhud.app.domain.send

import android.content.SharedPreferences
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.data.remote.RemoteDispatchRepository
import com.hudhud.app.domain.settings.SiteProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryQueueProcessorTest {
    @Test
    fun `processNext blocks sending when feature flag is disabled`() = runBlocking {
        val store = AppSettingsStore(FakePreferences())
        val profile = SiteProfile(
            id = "default_staging",
            name = "Staging",
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

        val processor = DeliveryQueueProcessor(
            appSettings = store,
            sendGuard = guard,
            dispatchExecutor = { _, _ -> RemoteDispatchRepository.DispatchSummary("default_staging", 0, 0, 0, "should-not-run") },
            featureEnabled = { false },
        )

        val result = processor.processNext()
        assertEquals(0, result.attempted)
        assertEquals(0, result.sent)
        assertEquals(0, result.blocked)
        assertTrue(result.detail.contains("disabled"))
    }

    @Test
    fun `processNext executes dispatch when gate is approved and feature flag is enabled`() = runBlocking {
        val store = AppSettingsStore(FakePreferences())
        val profile = SiteProfile(
            id = "default_staging",
            name = "Staging",
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

        val processor = DeliveryQueueProcessor(
            appSettings = store,
            sendGuard = guard,
            dispatchExecutor = { _, _ -> RemoteDispatchRepository.DispatchSummary("default_staging", 1, 1, 0, "ok") },
            featureEnabled = { true },
        )

        val result = processor.processNext()
        assertEquals(1, result.attempted)
        assertEquals(1, result.sent)
        assertEquals(0, result.blocked)
        assertEquals("ok", result.detail)
    }

    private class FakePreferences : SharedPreferences {
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
            override fun putString(key: String?, value: String?): SharedPreferences.Editor { map[key ?: ""] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { map[key ?: ""] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { map[key ?: ""] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { map[key ?: ""] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { map[key ?: ""] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { map[key ?: ""] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { map.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { map.clear(); return this }
            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
    }
}
