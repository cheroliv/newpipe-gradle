package com.cheroliv.newpipe

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * NewPipe Extractor [Downloader] backed by OkHttp.
 *
 * When a [SessionManager] is available, each request is decorated with
 * `Authorization: Bearer <accessToken>` from the next session in Round-Robin.
 *
 * On HTTP 401/403 the session is marked invalid and the request is retried
 * once anonymously — avoids dropping an entire download because one token
 * expired between [AuthSessionTask] and the actual request.
 *
 * The [httpClient] singleton is shared with [YouTubeDownloader] so both
 * metadata extraction and audio download reuse the same connection pool
 * and thread pool.
 */
class DownloaderImpl private constructor(
    private val sessionManager: SessionManager? = null
) : Downloader() {

    private val logger = LoggerFactory.getLogger(DownloaderImpl::class.java)
    private val client get() = httpClient

    companion object {
        val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        @Volatile
        private var instance: DownloaderImpl = DownloaderImpl()

        fun getInstance(): DownloaderImpl = instance

        /**
         * Replaces the singleton with an instance backed by [sessionManager].
         * Called by [DownloadMusicTask] at the start of its [TaskAction],
         * after [AuthSessionTask] has written fresh tokens to sessions.yml.
         */
        fun init(sessionManager: SessionManager?) {
            instance = DownloaderImpl(sessionManager)
        }
    }

    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val session = sessionManager?.next()
        return try {
            doExecute(request, session)
        } catch (e: SessionExpiredException) {
            // Token expired between auth and execution — mark invalid, retry anonymous
            logger.warn("Retrying anonymously after session expiry: ${request.url()}")
            doExecute(request, session = null)
        }
    }

    private fun doExecute(request: NewPipeRequest, session: Session?): NewPipeResponse {
        val requestBody: RequestBody? =
            if (request.httpMethod() == "POST" && request.dataToSend() == null) {
                ByteArray(0).toRequestBody(null, 0, 0)
            } else {
                request.dataToSend()?.let { it.toRequestBody(null, 0, it.size) }
            }

        val builder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), requestBody)

        // Forward all headers from the NewPipe request
        request.headers().forEach { (key, values) ->
            values.forEach { value -> builder.addHeader(key, value) }
        }

        // Inject Bearer token — no cookie spoofing, no User-Agent override
        if (session != null) {
            builder.header("Authorization", "Bearer ${session.accessToken}")
            logger.debug("Request via session '${session.id}': ${request.url()}")
        }

        val response: Response = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw ReCaptchaException("Network error: ${e.message}", e.cause?.message)
        }

        if (session != null && (response.code == 401 || response.code == 403)) {
            sessionManager?.markInvalid(session.id)
            response.close()
            throw SessionExpiredException(session.id, response.code)
        }

        val responseBody = response.body?.string()
        val headers = mutableMapOf<String, MutableList<String>>()
        response.headers.names().forEach { name ->
            headers[name] = response.headers.values(name).toMutableList()
        }

        return NewPipeResponse(
            response.code,
            response.message,
            headers,
            responseBody,
            request.url()
        )
    }

    private class SessionExpiredException(sessionId: String, code: Int) :
        Exception("Session '$sessionId' returned HTTP $code")
}