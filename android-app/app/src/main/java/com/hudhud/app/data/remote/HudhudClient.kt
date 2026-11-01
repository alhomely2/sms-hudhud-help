package com.hudhud.app.data.remote

import com.hudhud.app.domain.settings.RequestThrottlePolicy
import com.hudhud.app.domain.settings.SiteProfile
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Read-only client for the alhudhudai platform.
 *
 * It deliberately contains no delivery logic yet: the send contract is not
 * documented, and only the authenticated messages listing is confirmed to exist.
 * The probe verifies transport, TLS, and API-key authentication in one call.
 */
class HudhudClient(
    private val baseUrl: String = RemoteEndpoint.BASE_URL,
    private val callFactory: Call.Factory = defaultClient(),
) {
    constructor(siteProfile: SiteProfile) : this(
        baseUrl = siteProfile.normalizedBaseUrl(),
        callFactory = clientFor(
            connectTimeoutSec = siteProfile.connectTimeoutSeconds,
            readTimeoutSec = siteProfile.readTimeoutSeconds,
            callTimeoutSec = siteProfile.callTimeoutSeconds,
        ),
    )

    fun probe(apiKey: String): ConnectionResult {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return ConnectionResult(
                outcome = ConnectionOutcome.AUTH_REJECTED,
                detail = "API key is empty",
                hostReachable = false,
                authAccepted = false,
                apiOperational = false,
            )
        }

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + RemoteEndpoint.MESSAGES_PATH)
            .get()
            .header("Accept", "application/json")
            .header(RemoteEndpoint.API_KEY_HEADER, key)
            .build()

        return try {
            callFactory.newCall(request).execute().use { response ->
                val code = response.code
                val outcome = outcomeFor(code)
                val retryAfter = RequestThrottlePolicy.parseRetryAfterSeconds(response.header("Retry-After"))
                ConnectionResult(
                    outcome = outcome,
                    httpCode = code,
                    detail = readError(response.body?.string()),
                    correlationId = response.header("X-Correlation-Id"),
                    hostReachable = true,
                    authAccepted = when (code) {
                        in 200..299, 500 -> true
                        401, 403 -> false
                        else -> null
                    },
                    apiOperational = code in 200..299,
                    rateLimitLimit = response.header("X-Ratelimit-Limit"),
                    rateLimitRemaining = response.header("X-Ratelimit-Remaining"),
                    retryAfterSeconds = retryAfter,
                )
            }
        } catch (e: IOException) {
            ConnectionResult(
                outcome = ConnectionOutcome.NETWORK_ERROR,
                detail = e.message,
                hostReachable = false,
                authAccepted = null,
                apiOperational = false,
            )
        }
    }

    private fun outcomeFor(code: Int): ConnectionOutcome = when {
        code in 200..299 -> ConnectionOutcome.CONNECTED
        code == 401 -> ConnectionOutcome.AUTH_REJECTED
        code == 403 -> ConnectionOutcome.KEY_MISMATCH
        code >= 500 -> ConnectionOutcome.SERVER_ERROR
        else -> ConnectionOutcome.UNEXPECTED_RESPONSE
    }

    companion object {
        private fun defaultClient(): OkHttpClient = clientFor(15, 20, 30)

        fun clientFor(
            connectTimeoutSec: Int = 15,
            readTimeoutSec: Int = 20,
            callTimeoutSec: Int = 30,
        ): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec.toLong(), TimeUnit.SECONDS)
            .callTimeout(callTimeoutSec.toLong(), TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        /**
         * Both known platform error shapes are understood:
         * `{"success":false,"error":"..."}` and `{"message":"..."}`.
         */
        private fun readError(body: String?): String? {
            if (body.isNullOrBlank()) return null
            return try {
                val json = JSONObject(body)
                val message = when {
                    json.has("error") -> json.optString("error")
                    json.has("message") -> json.optString("message")
                    else -> ""
                }
                message.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }
    }
}
