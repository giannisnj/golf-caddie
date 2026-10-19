package com.golfcaddie.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** An HTTP error status; [code] 429 means "too many requests, slow down". */
class HttpException(val code: Int, host: String) : IOException("HTTP $code from $host")

/** Plain HttpURLConnection calls shared by the repositories (no extra network library). */
class Http(private val userAgent: String) {
    fun get(url: String): String = getBytes(url).decodeToString()

    fun getBytes(url: String): ByteArray = open(url).run {
        try {
            requireOk()
            inputStream.use { it.readBytes() }
        } finally {
            disconnect()
        }
    }

    fun post(url: String, form: String): String = open(url).run {
        try {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(form.toByteArray()) }
            requireOk()
            inputStream.bufferedReader().readText()
        } finally {
            disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 90_000
        setRequestProperty("User-Agent", userAgent)
    }

    private fun HttpURLConnection.requireOk() {
        if (responseCode !in 200..299) throw HttpException(responseCode, url.host)
    }
}
