package com.nezammonitor.app.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.11.2 hardening, kept for the remaining network calls (v3.11.3):
 * the shared client never reuses a pooled connection (a stale pooled
 * connection is what failed mid-body on the phone) and retries
 * connect-time failures. The client factory moved out of the deleted
 * directory-sync layer into ResilientHttpClient; NezamApi (case sync,
 * login, report downloads) uses it with its own timeouts.
 */
class ResilientHttpClientTest {

    @Test
    fun clientRetriesOnConnectionFailure() {
        assertTrue(ResilientHttpClient.newClient().retryOnConnectionFailure)
    }

    @Test
    fun clientNeverKeepsAPooledConnection() {
        assertEquals(0, ResilientHttpClient.newClient().connectionPool.idleConnectionCount())
        assertEquals(
            0,
            ResilientHttpClient.newClient(connectTimeoutSec = 30, readTimeoutSec = 60, writeTimeoutSec = 60)
                .connectionPool.idleConnectionCount()
        )
    }

    @Test
    fun callerTimeoutsAreHonored() {
        val caseSync = ResilientHttpClient.newClient(
            connectTimeoutSec = 30, readTimeoutSec = 60, writeTimeoutSec = 60
        )
        assertEquals(30_000, caseSync.connectTimeoutMillis)
        assertEquals(60_000, caseSync.readTimeoutMillis)
        assertEquals(60_000, caseSync.writeTimeoutMillis)
        val defaults = ResilientHttpClient.newClient()
        assertEquals(15_000, defaults.connectTimeoutMillis)
        assertEquals(30_000, defaults.readTimeoutMillis)
    }
}
