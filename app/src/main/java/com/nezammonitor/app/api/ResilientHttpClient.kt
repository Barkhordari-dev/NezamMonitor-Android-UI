package com.nezammonitor.app.api

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The shared HTTP client for the app's remaining network calls
 * (case sync, login, report downloads — all against
 * service.yazdnezam.ir:8033).
 *
 * This is the v3.11.2 directory hardening, kept and shared: NO
 * keep-alive — ConnectionPool(0, 1ns) means no connection is ever
 * reused, so a pooled connection the server already closed can never
 * fail a request mid-body ("unexpected end of stream" on all 173 phone
 * requests in 3.11.1). retryOnConnectionFailure stays on for the
 * failures it does cover (connect-time). Timeouts are per caller so
 * the case sync keeps its own (30s connect / 60s read / 60s write).
 */
object ResilientHttpClient {

    fun newClient(
        connectTimeoutSec: Long = 15,
        readTimeoutSec: Long = 30,
        writeTimeoutSec: Long? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        if (writeTimeoutSec != null) {
            builder.writeTimeout(writeTimeoutSec, TimeUnit.SECONDS)
        }
        return builder.build()
    }
}
