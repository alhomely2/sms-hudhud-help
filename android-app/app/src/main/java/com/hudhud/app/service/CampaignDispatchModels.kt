package com.hudhud.app.service

enum class DispatchStatus { IDLE, RUNNING, PAUSED, COMPLETED }

data class DispatchProgress(
    val campaignId: Long = -1L,
    val campaignName: String = "",
    val total: Int = 0,
    val sent: Int = 0,
    val failed: Int = 0,
    val status: DispatchStatus = DispatchStatus.IDLE,
)
