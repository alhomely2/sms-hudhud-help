package com.hudhud.app.data.remote

import com.hudhud.app.domain.settings.SiteProfile
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudhudClientTest {
    @Test
    fun `2xx marks the connection as connected`() {
        val result = probe(responseCode = 200)

        assertEquals(ConnectionOutcome.CONNECTED, result.outcome)
        assertEquals(200, result.httpCode)
        assertTrue(result.hostReachable)
        assertEquals(true, result.authAccepted)
        assertTrue(result.apiOperational)
    }

    @Test
    fun `401 marks the key as rejected`() {
        val result = probe(responseCode = 401)

        assertEquals(ConnectionOutcome.AUTH_REJECTED, result.outcome)
        assertEquals(401, result.httpCode)
        assertTrue(result.hostReachable)
        assertEquals(false, result.authAccepted)
        assertFalse(result.apiOperational)
    }

    @Test
    fun `403 marks the key as incomplete or mismatched`() {
        val result = probe(responseCode = 403)

        assertEquals(ConnectionOutcome.KEY_MISMATCH, result.outcome)
        assertEquals(403, result.httpCode)
        assertTrue(result.hostReachable)
        assertEquals(false, result.authAccepted)
        assertFalse(result.apiOperational)
    }

    @Test
    fun `5xx marks a server error and keeps the correlation id`() {
        val result = probe(
            responseCode = 500,
            body = """{"message":"Server Error"}""",
            correlationId = "d82f3618-7759-4bdd-ad88-fb5beac14433",
        )

        assertEquals(ConnectionOutcome.SERVER_ERROR, result.outcome)
        assertEquals(500, result.httpCode)
        assertEquals("d82f3618-7759-4bdd-ad88-fb5beac14433", result.correlationId)
        assertTrue(result.hostReachable)
        assertEquals(true, result.authAccepted)
        assertFalse(result.apiOperational)
    }

    @Test
    fun `transport failure marks a network error`() {
        val client = HudhudClient(
            baseUrl = "https://staging.example.test",
            callFactory = failingFactory(IOException("unreachable")),
        )

        val result = client.probe("id.secret")

        assertEquals(ConnectionOutcome.NETWORK_ERROR, result.outcome)
        assertNull(result.httpCode)
        assertFalse(result.hostReachable)
    }

    @Test
    fun `blank key is rejected without touching the network`() {
        val calls = AtomicInteger(0)
        val client = HudhudClient(
            baseUrl = "https://staging.example.test",
            callFactory = recordingFactory(calls, responseCode = 200),
        )

        val result = client.probe("   ")

        assertEquals(ConnectionOutcome.AUTH_REJECTED, result.outcome)
        assertEquals(0, calls.get())
    }

    @Test
    fun `probe sends the api key header to the messages endpoint`() {
        var capturedHeader: String? = null
        var capturedPath: String? = null
        val interceptor = Interceptor { chain ->
            capturedHeader = chain.request().header(RemoteEndpoint.API_KEY_HEADER)
            capturedPath = chain.request().url.encodedPath
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody(null))
                .build()
        }
        val client = HudhudClient(
            baseUrl = "https://staging.example.test/",
            callFactory = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )

        client.probe("TESTKEYID.testsecret")

        assertEquals("TESTKEYID.testsecret", capturedHeader)
        assertEquals(RemoteEndpoint.MESSAGES_PATH, capturedPath)
    }

    @Test
    fun `probe parses rate limit headers and retry after`() {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .header("X-Ratelimit-Limit", "300")
                .header("X-Ratelimit-Remaining", "0")
                .header("Retry-After", "60")
                .body("{}".toResponseBody(null))
                .build()
        }
        val client = HudhudClient(
            baseUrl = "https://staging.example.test",
            callFactory = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )

        val result = client.probe("id.secret")

        assertEquals(429, result.httpCode)
        assertEquals("300", result.rateLimitLimit)
        assertEquals("0", result.rateLimitRemaining)
        assertEquals(60L, result.retryAfterSeconds)
    }

    @Test
    fun `site clients do not follow redirects with api keys`() {
        val client = HudhudClient.clientFor()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    private fun probe(
        responseCode: Int,
        body: String = "",
        correlationId: String? = null,
    ): ConnectionResult {
        val calls = AtomicInteger(0)
        val client = HudhudClient(
            baseUrl = "https://staging.example.test",
            callFactory = recordingFactory(calls, responseCode, body, correlationId),
        )

        val result = client.probe("id.secret")
        assertTrue(calls.get() == 1)
        return result
    }

    private fun recordingFactory(
        calls: AtomicInteger,
        responseCode: Int,
        body: String = "",
        correlationId: String? = null,
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            calls.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("test")
                .apply { if (correlationId != null) header("X-Correlation-Id", correlationId) }
                .body(body.toResponseBody(null))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun failingFactory(error: IOException): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw error })
            .build()
}
