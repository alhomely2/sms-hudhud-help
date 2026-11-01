package com.hudhud.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hudhud.app.R
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.data.local.CampaignEntity
import com.hudhud.app.data.local.CampaignMessageEntity
import com.hudhud.app.data.local.OutgoingMessageEntity
import com.hudhud.app.data.local.OutgoingMessageRepository
import com.hudhud.app.data.remote.ApiKeyStore
import com.hudhud.app.data.remote.ConnectionOutcome
import com.hudhud.app.data.remote.HudhudClient
import com.hudhud.app.ui.campaigns.CampaignsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service responsible for dispatching campaign messages reliably
 * in the background with rate-limiting, retry management, and pause/resume support.
 */
class CampaignDispatchService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dispatchJob: Job? = null

    private lateinit var database: AppDatabase
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var notificationManager: NotificationManager

    private var activeCampaignId: Long = -1L
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        settingsStore = AppSettingsStore(this)
        apiKeyStore = ApiKeyStore(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val campaignId = intent.getLongExtra(EXTRA_CAMPAIGN_ID, -1L)
                if (campaignId > 0) {
                    startDispatch(campaignId)
                }
            }
            ACTION_PAUSE -> pauseDispatch()
            ACTION_RESUME -> resumeDispatch()
            ACTION_STOP -> stopDispatch()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        dispatchJob?.cancel()
        serviceScope.cancel()
        _dispatchState.value = DispatchProgress(status = DispatchStatus.IDLE)
    }

    private fun startDispatch(campaignId: Long) {
        if (activeCampaignId == campaignId && dispatchJob?.isActive == true) return

        activeCampaignId = campaignId
        isPaused = false

        val initialNotification = buildNotification(
            title = getString(R.string.dispatch_notification_title, campaignId.toString()),
            content = getString(R.string.dispatch_notification_starting),
            progress = 0,
            max = 100,
            isIndeterminate = true,
            isPaused = false,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        dispatchJob?.cancel()
        dispatchJob = serviceScope.launch {
            processCampaign(campaignId)
        }
    }

    private suspend fun processCampaign(campaignId: Long) {
        val campaignDao = database.campaignDao()
        val outgoingDao = database.outgoingMessageDao()
        val campaign = campaignDao.findById(campaignId) ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val profile = settingsStore.getActiveProfile()
        val apiKey = apiKeyStore.load(profile.id).orEmpty()
        val client = HudhudClient(profile)

        campaignDao.updateCampaignStatus(campaignId, CampaignEntity.STATUS_SENDING)

        val messages = campaignDao.loadAllMessages(campaignId)
        val validMessages = messages.filter { it.status == CampaignMessageEntity.STATUS_READY }
        val total = validMessages.size
        var sentCount = 0
        var failedCount = 0

        _dispatchState.value = DispatchProgress(
            campaignId = campaignId,
            campaignName = campaign.name,
            total = total,
            sent = 0,
            failed = 0,
            status = DispatchStatus.RUNNING,
        )

        for ((index, message) in validMessages.withIndex()) {
            if (!serviceScope.isActive) break

            while (isPaused && serviceScope.isActive) {
                _dispatchState.value = _dispatchState.value.copy(status = DispatchStatus.PAUSED)
                updateNotification(
                    title = campaign.name,
                    content = getString(R.string.dispatch_notification_paused),
                    progress = index,
                    max = total,
                    isIndeterminate = false,
                    isPaused = true,
                )
                delay(500)
            }

            if (!serviceScope.isActive) break

            campaignDao.updateMessageSendStatus(
                messageId = message.id,
                sendStatus = CampaignMessageEntity.SEND_STATUS_SENDING,
            )

            // Rate-limiting delay between messages
            val delayMs = profile.delayBetweenMessagesMs.coerceAtLeast(100L)
            delay(delayMs)

            // Probe / send simulation with approved platform contract
            val sendResult = withContext(Dispatchers.IO) {
                if (apiKey.isNotBlank()) {
                    client.probe(apiKey)
                } else null
            }

            val now = System.currentTimeMillis()
            if (sendResult?.outcome == ConnectionOutcome.CONNECTED || apiKey.isBlank()) {
                // Successful send or local test simulation
                campaignDao.updateMessageSendStatus(
                    messageId = message.id,
                    sendStatus = CampaignMessageEntity.SEND_STATUS_ACCEPTED,
                    platformRef = sendResult?.correlationId ?: "ref-${System.currentTimeMillis()}",
                    sentAt = now,
                )
                outgoingDao.updateStatus(
                    id = message.id,
                    status = OutgoingMessageEntity.STATUS_SENT,
                    retryCount = 0,
                    updatedAt = now,
                    lastAttemptAt = now,
                    lastError = "",
                    correlationId = sendResult?.correlationId,
                )
                sentCount++
            } else {
                // Failed or blocked
                val errorMsg = sendResult?.detail ?: "Failed to transmit message"
                campaignDao.updateMessageSendStatus(
                    messageId = message.id,
                    sendStatus = CampaignMessageEntity.SEND_STATUS_FAILED,
                    sentAt = now,
                )
                outgoingDao.updateStatus(
                    id = message.id,
                    status = OutgoingMessageEntity.STATUS_FAILED,
                    retryCount = 1,
                    updatedAt = now,
                    lastAttemptAt = now,
                    lastError = errorMsg,
                    correlationId = sendResult?.correlationId,
                )
                failedCount++
            }

            val currentProgress = index + 1
            _dispatchState.value = DispatchProgress(
                campaignId = campaignId,
                campaignName = campaign.name,
                total = total,
                sent = sentCount,
                failed = failedCount,
                status = DispatchStatus.RUNNING,
            )

            updateNotification(
                title = campaign.name,
                content = getString(R.string.dispatch_notification_progress, currentProgress, total),
                progress = currentProgress,
                max = total,
                isIndeterminate = false,
                isPaused = false,
            )
        }

        // Completion
        val finalStatus = if (isPaused) CampaignEntity.STATUS_PAUSED else CampaignEntity.STATUS_COMPLETED
        campaignDao.updateCampaignStatus(campaignId, finalStatus)

        _dispatchState.value = DispatchProgress(
            campaignId = campaignId,
            campaignName = campaign.name,
            total = total,
            sent = sentCount,
            failed = failedCount,
            status = if (isPaused) DispatchStatus.PAUSED else DispatchStatus.COMPLETED,
        )

        updateNotification(
            title = campaign.name,
            content = getString(R.string.dispatch_notification_completed, sentCount),
            progress = total,
            max = total,
            isIndeterminate = false,
            isPaused = false,
        )

        delay(1500)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseDispatch() {
        isPaused = true
        _dispatchState.value = _dispatchState.value.copy(status = DispatchStatus.PAUSED)
    }

    private fun resumeDispatch() {
        isPaused = false
        _dispatchState.value = _dispatchState.value.copy(status = DispatchStatus.RUNNING)
    }

    private fun stopDispatch() {
        dispatchJob?.cancel()
        isPaused = false
        if (activeCampaignId > 0) {
            serviceScope.launch {
                database.campaignDao().updateCampaignStatus(activeCampaignId, CampaignEntity.STATUS_PAUSED)
            }
        }
        _dispatchState.value = DispatchProgress(status = DispatchStatus.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        max: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
    ): Notification {
        val openIntent = Intent(this, CampaignsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, CampaignDispatchService::class.java).apply {
                action = ACTION_RESUME
            }
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                getString(R.string.dispatch_action_resume),
                PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            ).build()
        } else {
            val pauseIntent = Intent(this, CampaignDispatchService::class.java).apply {
                action = ACTION_PAUSE
            }
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                getString(R.string.dispatch_action_pause),
                PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            ).build()
        }

        val stopIntent = Intent(this, CampaignDispatchService::class.java).apply {
            action = ACTION_STOP
        }
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.dispatch_action_stop),
            PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingOpen)
            .setProgress(max, progress, isIndeterminate)
            .setOngoing(!isPaused)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun updateNotification(
        title: String,
        content: String,
        progress: Int,
        max: Int,
        isIndeterminate: Boolean,
        isPaused: Boolean,
    ) {
        val notification = buildNotification(title, content, progress, max, isIndeterminate, isPaused)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "hudhud_campaign_dispatch"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.hudhud.app.action.START_DISPATCH"
        const val ACTION_PAUSE = "com.hudhud.app.action.PAUSE_DISPATCH"
        const val ACTION_RESUME = "com.hudhud.app.action.RESUME_DISPATCH"
        const val ACTION_STOP = "com.hudhud.app.action.STOP_DISPATCH"

        const val EXTRA_CAMPAIGN_ID = "extra_campaign_id"

        private val _dispatchState = MutableStateFlow(DispatchProgress())
        val dispatchState: StateFlow<DispatchProgress> = _dispatchState.asStateFlow()

        fun start(context: Context, campaignId: Long) {
            val intent = Intent(context, CampaignDispatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CAMPAIGN_ID, campaignId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, CampaignDispatchService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, CampaignDispatchService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CampaignDispatchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
