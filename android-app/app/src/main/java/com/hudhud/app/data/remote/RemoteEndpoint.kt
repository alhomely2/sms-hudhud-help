package com.hudhud.app.data.remote

import com.hudhud.app.BuildConfig

/**
 * Central description of the alhudhudai platform endpoints.
 *
 * The base URL is a build-time value so a release build can point to the
 * production host without changing code.
 */
object RemoteEndpoint {
    const val BASE_URL: String = BuildConfig.HUDHUD_BASE_URL

    /** Header confirmed by probing the staging host. */
    const val API_KEY_HEADER = "X-Api-Key"

    /** Only route confirmed to exist on the platform today. */
    const val MESSAGES_PATH = "/api/v1/messages"

    /** Laravel health endpoint, useful to separate "host down" from "API rejected". */
    const val HEALTH_PATH = "/up"
}
