package com.cheroliv.newpipe

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * Implémentation du Downloader pour NewPipe Extractor
 * Utilise OkHttp pour effectuer les requêtes HTTP
 */
class DownloaderImpl private constructor() : Downloader() {
    
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private val instance = DownloaderImpl()
        
        fun getInstance(): DownloaderImpl = instance
    }
    
    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val requestBody: RequestBody? = if (request.httpMethod() == "POST" && request.dataToSend() == null) {
            // For POST requests with no data, provide an empty request body
            RequestBody.create(null, ByteArray(0))
        } else {
            request.dataToSend()?.let { data -> RequestBody.create(null, data) }
        }

        val requestBuilder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), requestBody)
        
        // Ajout des headers
        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }
        
        val response: Response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            throw ReCaptchaException("Erreur réseau: ${e.message}", e.cause?.message)
        }
        
        val responseBody = response.body?.string() ?: ""
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
