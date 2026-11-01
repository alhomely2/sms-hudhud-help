package com.hudhud.app.ui.home

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hudhud.app.R
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.data.local.OutgoingMessageRepository
import com.hudhud.app.data.remote.ApiKeyStore
import com.hudhud.app.data.remote.RemoteDispatchRepository
import com.hudhud.app.databinding.ActivityMainBinding
import com.hudhud.app.domain.send.DeliveryQueueCoordinator
import com.hudhud.app.domain.send.PlatformSendGuard
import com.hudhud.app.ui.ready.ReadyMessagesActivity
import com.hudhud.app.ui.settings.ConnectionSettingsActivity
import com.hudhud.app.ui.shared.BottomNavBar
import com.hudhud.app.ui.shared.BottomNavTab
import com.hudhud.app.ui.template.DynamicTemplateActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var apiKeyStore: ApiKeyStore
    private var deliveryQueueCoordinator: DeliveryQueueCoordinator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        apiKeyStore = ApiKeyStore(this)

        val database = AppDatabase.getInstance(this)
        val settings = AppSettingsStore(this)
        val repository = OutgoingMessageRepository(database.outgoingMessageDao())
        val remoteDispatch = RemoteDispatchRepository(
            appSettings = settings,
            outgoingMessageDao = database.outgoingMessageDao(),
            apiKeyStore = apiKeyStore,
            sendGuard = PlatformSendGuard(
                appSettings = settings,
                apiKeyLookup = { siteId -> apiKeyStore.load(siteId) },
                contractApprovedProvider = { settings.getActiveProfile().isContractApproved },
            ),
        )
        deliveryQueueCoordinator = DeliveryQueueCoordinator(
            outgoingMessageRepository = repository,
            remoteDispatchRepository = remoteDispatch,
        )

        // المسار الأول: رسائل جاهزة
        binding.readyPathCard.setOnClickListener {
            startActivity(Intent(this, ReadyMessagesActivity::class.java))
        }

        // المسار الثاني: بيانات تحتاج قالب
        binding.dynamicPathCard.setOnClickListener {
            startActivity(Intent(this, DynamicTemplateActivity::class.java))
        }
        binding.connectionAction.setOnClickListener {
            startActivity(Intent(this, ConnectionSettingsActivity::class.java))
        }

        BottomNavBar.bind(this, binding.bottomNav, BottomNavTab.HOME)
    }

    override fun onResume() {
        super.onResume()
        renderConnectionState()
        val activeProfileId = AppSettingsStore(this).getActiveProfile().id
        deliveryQueueCoordinator?.schedulePolling(
            siteProfileId = activeProfileId,
            maxItems = 25,
            intervalMs = 60_000L,
        )
    }

    override fun onPause() {
        super.onPause()
        val activeProfileId = AppSettingsStore(this).getActiveProfile().id
        deliveryQueueCoordinator?.cancelPolling(activeProfileId)
    }

    /**
     * Shows whether a key is stored on this device. It stays offline on purpose:
     * the real check runs from the connection settings screen.
     */
    private fun renderConnectionState() {
        val activeProfile = AppSettingsStore(this).getActiveProfile()
        val configured = apiKeyStore.hasKey(activeProfile.id)
        val approved = activeProfile.isContractApproved

        binding.connectionLabel.setText(
            if (configured) R.string.home_connection_saved else R.string.home_connection_missing,
        )

        val contractState = when {
            !configured -> " • العقد: غير مُثبت"
            approved -> " • العقد: مُعتمد"
            else -> " • العقد: في انتظار الاعتماد"
        }

        val baseText = if (configured) {
            getString(R.string.home_connection_active_site, activeProfile.name)
        } else {
            getString(R.string.home_connection_active_missing, activeProfile.name)
        }
        binding.connectionDetail.text = "$baseText$contractState"

        lifecycleScope.launch {
            val queueStats = AppDatabase.getInstance(this@MainActivity).outgoingMessageDao()
            val pending = queueStats.countPending(activeProfile.id)
            val retry = queueStats.countRetryWaiting(activeProfile.id)
            val blocked = queueStats.countBlocked(activeProfile.id)
            val queueSummary = if (pending + retry + blocked > 0) {
                " • قائمة الانتظار: $pending قيد الانتظار • $retry إعادة محاولة • $blocked محظورة"
            } else {
                " • قائمة الانتظار: 0"
            }
            binding.connectionDetail.text = "$baseText$contractState$queueSummary"
        }

        val pillBackground = color(if (configured) R.color.hudhud_primary else R.color.hudhud_surface)
        val onPill = color(if (configured) R.color.hudhud_on_primary else R.color.hudhud_on_surface)
        val detailColor = color(
            if (configured) R.color.hudhud_on_primary else R.color.hudhud_on_surface_muted,
        )

        binding.connectionAction.setCardBackgroundColor(pillBackground)
        binding.connectionAction.strokeColor = if (configured) {
            pillBackground
        } else {
            color(R.color.hudhud_outline)
        }
        binding.connectionLabel.setTextColor(onPill)
        binding.connectionDetail.setTextColor(detailColor)
        binding.connectionDetail.alpha = if (configured) 0.75f else 1f
        binding.connectionIcon.imageTintList = ColorStateList.valueOf(onPill)
        binding.connectionDot.backgroundTintList = ColorStateList.valueOf(
            if (configured && approved) onPill else color(R.color.hudhud_warn),
        )
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)
}
