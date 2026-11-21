package com.nezammonitor.app.data

import com.nezammonitor.app.api.ResilientHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Engineer profile photo, fetched on demand (v3.11.3 addition).
 *
 * When a profile page opens, the app shows the stored (snapshot) photo
 * URL immediately, then fetches the engineer's own detail page
 * (/resume/engineers/{siteId}) ONCE and replaces it with the freshly
 * parsed photo URL when one is found. The fetch is DISPLAY-ONLY and
 * SESSION-ONLY: the fetched URL is never written back into the
 * directory tables (user decision 2026-11-20 — no in-app directory
 * updates; the snapshot is the only data source).
 *
 * Failures are silent by design: offline, an HTTP error, or an
 * unparseable page all return null and the caller keeps the stored
 * photo (or the initials fallback). No dialog, no crash, no retry
 * loop — one attempt with the shared resilient client (no keep-alive
 * reuse, connect-time retry, no compression).
 */
object DirectoryPhotoLoader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /**
     * The photo URL from the engineer's detail page, or null when there
     * is no real photo (the placeholder "images.png" means "no photo"),
     * the page does not parse, or the request fails. Never throws.
     * The [client] parameter exists for tests (a canned client); the
     * app uses the shared resilient client.
     */
    suspend fun fetchPhotoUrl(
        siteId: Int,
        client: OkHttpClient = ResilientHttpClient.newClient()
    ): String? {
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("${DirectoryParser.BASE_URL}/resume/engineers/$siteId")
                    .header("User-Agent", USER_AGENT)
                    .header("Connection", "close")
                    .header("Accept-Encoding", "identity")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val html = response.body?.string() ?: return@withContext null
                    DirectoryParser.parseEngineerDetail(html, siteId)
                        ?.photo?.takeIf { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
