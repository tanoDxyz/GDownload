package com.tanodxyz.gdownload.connection

import com.tanodxyz.gdownload.io.StreamInputResourceWrapper
import com.tanodxyz.gdownload.*

import java.io.IOException
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.*
import kotlin.collections.HashMap
import kotlin.jvm.Throws

class DefaultURLConnectionHandler(val addRefererAndHost: Boolean = false) : URLConnectionHandler() {
    @Throws(IOException::class)
    override fun makeConnection(url: String): RemoteConnection {
        this.url = url
        val connection = checkAndReconnectToNewUrlAddressIfRequired(makeConnection())
        connection.parseResponseData()
        return if (responseCode.isResponseOk()) {
            RemoteConnection(
                ConnectionImpl(connection),
                StreamInputResourceWrapper(connection.getInputStream().buffered()),
                acceptRanges,
                md5Hash,
                contentLength,
                readTimeOut,
                connectionTimeOut,
                method,
                useCache,
                followRedirects,
                responseCode,
                responseMessage,
                responseError,
                this.url,
                requestHeaders,
                responseHeaders
            )
        } else {
            throw IOException("failed to connect to remote service. $responseError")
        }
    }

    override fun makeConnection(
        url: String,
        retriesCount: Int,
        startRange: Long,
        endRange: Long,
        downloaded: Long
    ): Pair<Exception?, RemoteConnection?> {
        var remoteConnection: RemoteConnection? = null
        var exception: Exception? = null
        val canMakeConnectionRequest = (startRange == 0L && endRange == 0L && downloaded == 0L)
        val byteRangeIsAccurate = addByteRangeHeader(startRange, endRange, downloaded)
        if (byteRangeIsAccurate || canMakeConnectionRequest) {
            for (i: Int in 1..retriesCount) {
                try {
                    remoteConnection = makeConnection(url)
                    break
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    exception = ex
                }
            }
        } else {
            exception =
                Exception("Invalid args byteRangeIsAccurate = $byteRangeIsAccurate & canMakeConnectionRequest = $canMakeConnectionRequest")
        }
        return Pair(exception, remoteConnection)
    }

    private fun checkAndReconnectToNewUrlAddressIfRequired(connection: URLConnection): URLConnection {
        var modifiedConnection = connection
        if (connection is HttpURLConnection) {
            val newUrlLocation =
                connection.checkToSeeIfReconnectionToNewAddressIsRequired()
            if (newUrlLocation != null) {
                this.url = newUrlLocation
                connection.closeConnection()
                modifiedConnection = makeConnection()
            }
        }
        return modifiedConnection
    }

    private fun makeConnection(): URLConnection {
        val connection = URL(url).openConnection()
        return connection.apply {
            readTimeout = readTimeOut
            connectTimeout = connectionTimeOut
            useCaches = useCache
            defaultUseCaches = useCaches
            setupThingsIfConnectionTypesHttp()
            connect()
        }
    }

    private fun URLConnection.parseResponseData() {
        if (this is HttpURLConnection) {
            this@DefaultURLConnectionHandler.also {
                it.responseCode = this.responseCode
                it.responseMessage = this.responseMessage
                it.responseHeaders =
                    this.headerFields.getCleanedHeaders()
                if (responseCode.isResponseOk()) {
                    it.contentLength = responseHeaders.getContentLengthFromHeader(-1)
                    it.md5Hash = responseHeaders.getContentHashMD5()
                    acceptRanges = responseHeaders.acceptRanges(responseCode)
                } else {
                    it.responseError = this.errorStream.copyStreamToString()
                    closeConnection()
                }
            }

        }
    }

    private fun HttpURLConnection.checkToSeeIfReconnectionToNewAddressIsRequired(): String? {
        return when (this.responseCode) {
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_SEE_OTHER -> {
                val headerValueLocation = this.headerFields.getHeaderValue("Location")
                headerValueLocation
            }
            else -> null
        }
    }

    private fun URLConnection.setupThingsIfConnectionTypesHttp() {
        if (this is HttpURLConnection) {
            this.apply {
                instanceFollowRedirects = followRedirects
                requestMethod = method
                if (addRefererAndHost) {
                    checkAndAddReferral(this@DefaultURLConnectionHandler.url)
                    addHostIfAvailable()
                }
                for ((header, value) in requestHeaders) {
                    addRequestProperty(header, value)
                }
            }
        }
    }

    private fun addHostIfAvailable() {
        setHostHeader(url)
    }

    private fun MutableMap<String?, List<String>?>.getCleanedHeaders():
            HashMap<String, List<String>> {
        val headers = HashMap<String, List<String>>()
        for (responseHeader in this) {
            val key = responseHeader.key
            if (key != null) {
                headers[key] = responseHeader.value ?: emptyList()
            }
        }
        return headers
    }

    fun resetResponseRequestHeaders() {
        requestHeaders = HashMap()
        responseHeaders = HashMap()
    }


    fun setHostHeader(host: String) {
        requestHeaders["Host"] = host
    }

    fun setUserAgentHeader(userAgent: String) {
        requestHeaders["User-Agent"] = userAgent
    }

    fun setAcceptHeader(type: String) {
        requestHeaders["Accept"] = type
    }

    fun setAcceptEncoding(encoding: String) {
        requestHeaders["Accept-Encoding"] = encoding
    }

    fun setConnectionHeader(connectionHeaderValue: String) {
        requestHeaders["Connection"] = connectionHeaderValue
    }

    fun addDefaultHeaders(
        userAgent: String = DEFAULT_USER_AGENT,
        acceptType: String = "*/*",
        connection: String = "keep-alive"
    ): URLConnectionHandler {
        setUserAgentHeader(userAgent)
        setAcceptHeader(acceptType)
        setConnectionHeader(connection)
        return this
    }

}
