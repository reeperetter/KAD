package com.reeperetter.sonicsnag

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

/**
 * NewPipeExtractor вміє тільки РОЗБИРАТИ дані з YouTube, але сам не робить
 * жодних мережевих запитів - за це відповідає ця реалізація на базі OkHttp.
 * Це стандартний, задокументований у самому NewPipeExtractor спосіб
 * підключення (див. приклади в репозиторії NewPipe).
 */
class NewPipeDownloaderImpl(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = OkRequest.Builder().url(url)

        headers.forEach { (headerName, headerValues) ->
            headerValues.forEach { value ->
                builder.addHeader(headerName, value)
            }
        }

        // Якщо сам запит не задав власний User-Agent - підставляємо наш
        // спільний (той самий, яким потім ExoPlayer буде запитувати сам
        // аудіо-потік, див. NetworkConstants.kt). Без явного узгодження
        // цих двох клієнтів сервери YouTube можуть мовчки відхиляти запит
        // на відтворення.
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.addHeader("User-Agent", NETWORK_USER_AGENT)
        }

        if (dataToSend != null) {
            builder.method(httpMethod, dataToSend.toRequestBody())
        } else {
            builder.method(httpMethod, null)
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string() ?: ""
            val responseHeaders = mutableMapOf<String, MutableList<String>>()
            response.headers.forEach { (name, value) ->
                responseHeaders.getOrPut(name) { mutableListOf() }.add(value)
            }

            return Response(
                response.code,
                response.message,
                responseHeaders,
                body,
                response.request.url.toString()
            )
        }
    }

    companion object {
        fun create(): NewPipeDownloaderImpl {
            val client = OkHttpClient.Builder().build()
            return NewPipeDownloaderImpl(client)
        }
    }
}
