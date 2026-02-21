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
import java.util.concurrent.TimeUnit

/**
 * NewPipe Extractor [Downloader] implementation backed by OkHttp.
 *
 * The [httpClient] singleton is shared with [YouTubeDownloader] so that
 * both metadata extraction and audio download reuse the same connection
 * pool and thread pool — avoiding redundant TCP/TLS handshakes.
 */
class DownloaderImpl private constructor() : Downloader() {

    private val client get() = httpClient

    companion object {
        /**
         * Shared OkHttpClient — one instance for the entire plugin lifecycle.
         * OkHttp is designed as a singleton: each instance owns its own
         * connection pool and dispatcher thread pool.
         */
        val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // audio streams can be slow to start
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private val instance = DownloaderImpl()

        fun getInstance(): DownloaderImpl = instance
    }

    override fun execute(request: NewPipeRequest): NewPipeResponse {
        // POST requests with no body require an explicit empty body
        val requestBody: RequestBody? = if (request.httpMethod() == "POST" && request.dataToSend() == null) {
            val content = ByteArray(0)
            content.toRequestBody(null, 0, content.size)
        } else request.dataToSend()?.let { data -> data.toRequestBody(null, 0, data.size) }

        val requestBuilder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), requestBody)

        // Forward all headers from the NewPipe request
        request.headers().forEach { (key, values) ->
            values.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        val response: Response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            throw ReCaptchaException("Network error: ${e.message}", e.cause?.message)
        }

        val responseBody = response.body.string()
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
}