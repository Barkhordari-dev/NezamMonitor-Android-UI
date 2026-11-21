package com.nezammonitor.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * v3.11.3 addition: the engineer profile photo is fetched from the
 * engineer's own detail page when the profile opens (one request,
 * display-only, session-only — never written back to the tables).
 * The REAL detail page fixture (fetched from
 * service.yazdnezam.ir:8033 on 1405/08/29) drives the checks:
 *  - a real photo <img> yields its URL;
 *  - the placeholder "images.png" yields NO photo (never the
 *    placeholder image);
 *  - a failed request yields null, silently (the screen keeps the
 *    stored photo or the initials fallback).
 */
class DirectoryPhotoLoaderTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("directory/$name")!!
            .bufferedReader(Charsets.UTF_8).readText()

    private fun cannedClient(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain -> handler(chain.request()) }.build()

    private fun okResponse(request: Request, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()

    private fun errorResponse(request: Request, code: Int): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code).message("Error")
            .body("".toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()

    private val realPhotoUrl =
        "http://service.yazdnezam.ir:8033/resume/Resume/public/uploads/img/ftpscanmohandesin/Pic/31-5-3-02134.jpg"

    @Test
    fun parserYieldsRealPhotoUrlFromDetailPage() {
        val parsed = DirectoryParser.parseEngineerDetail(fixture("engineer-5896.html"), 5896)
        assertEquals(realPhotoUrl, parsed?.photo)
    }

    @Test
    fun parserYieldsNoPhotoForPlaceholder() {
        // The same real page, but with the placeholder image instead of
        // a real photo — the site's "no photo" case.
        val placeholderHtml = fixture("engineer-5896.html")
            .replace("31-5-3-02134.jpg", "images.png")
        assertTrue(placeholderHtml.contains("images.png"))
        val parsed = DirectoryParser.parseEngineerDetail(placeholderHtml, 5896)
        assertEquals("", parsed?.photo)
    }

    @Test
    fun loaderFetchesTheDetailPageOnce() = runBlocking {
        var calls = 0
        var requestedUrl = ""
        val client = cannedClient { req ->
            calls++
            requestedUrl = req.url.toString()
            okResponse(req, fixture("engineer-5896.html"))
        }
        val url = DirectoryPhotoLoader.fetchPhotoUrl(5896, client)
        assertEquals(realPhotoUrl, url)
        assertEquals(1, calls)
        assertEquals("http://service.yazdnezam.ir:8033/resume/engineers/5896", requestedUrl)
    }

    @Test
    fun loaderReturnsNullForPlaceholderPage() = runBlocking {
        val placeholderHtml = fixture("engineer-5896.html")
            .replace("31-5-3-02134.jpg", "images.png")
        val client = cannedClient { req -> okResponse(req, placeholderHtml) }
        assertNull(DirectoryPhotoLoader.fetchPhotoUrl(5896, client))
    }

    @Test
    fun loaderReturnsNullOnHttpError() = runBlocking {
        val client = cannedClient { req -> errorResponse(req, 500) }
        assertNull(DirectoryPhotoLoader.fetchPhotoUrl(5896, client))
    }

    @Test
    fun loaderReturnsNullOnNetworkFailure() = runBlocking {
        val client = cannedClient { throw IOException("Unable to resolve host") }
        assertNull(DirectoryPhotoLoader.fetchPhotoUrl(5896, client))
    }

    @Test
    fun loaderSendsNoCompressionAndCloseHeaders() = runBlocking {
        var connection: String? = null
        var encoding: String? = null
        val client = cannedClient { req ->
            connection = req.header("Connection")
            encoding = req.header("Accept-Encoding")
            okResponse(req, fixture("engineer-5896.html"))
        }
        DirectoryPhotoLoader.fetchPhotoUrl(5896, client)
        assertEquals("close", connection)
        assertEquals("identity", encoding)
    }
}
