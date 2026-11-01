package com.hudhud.app.data.remote

enum class ConnectionOutcome {
    /** The platform answered with a 2xx response. */
    CONNECTED,

    /** 401: the platform did not receive a key or rejected it. */
    AUTH_REJECTED,

    /** 403: the key id was found but the secret does not match, usually an incomplete key. */
    KEY_MISMATCH,

    /** 5xx: the request reached the platform but the platform failed internally. */
    SERVER_ERROR,

    /** No HTTP response at all: DNS, TLS, timeout, or connectivity failure. */
    NETWORK_ERROR,

    /** Any other status code. */
    UNEXPECTED_RESPONSE,
}

/**
 * Result of a single connectivity probe against the platform.
 *
 * @param correlationId value of X-Correlation-Id when returned; useful for platform logs.
 * @param detail human readable server message, when the server provided one.
 */
data class ConnectionResult(
    val outcome: ConnectionOutcome,
    val httpCode: Int? = null,
    val detail: String? = null,
    val correlationId: String? = null,
    val hostReachable: Boolean = false,
    val authAccepted: Boolean? = null,
    val apiOperational: Boolean = false,
    val rateLimitLimit: String? = null,
    val rateLimitRemaining: String? = null,
    val retryAfterSeconds: Long? = null,
)
