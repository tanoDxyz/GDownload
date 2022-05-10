package com.tanodxyz.gdownload.connection

import com.tanodxyz.gdownload.closeConnection
import com.tanodxyz.gdownload.io.InputResourceWrapper
import java.net.HttpURLConnection
import java.net.URLConnection

data class RemoteConnection(
    var connection: URLConnection,
    var inputResourceWrapper: InputResourceWrapper,
    var acceptRanges: Boolean = false,
    var md5Hash: String = "",
    var contentLength: Long = -1L,
    var readTimeOut: Int = 0,
    var connectionTimeOut: Int = 0,
    var method: String = "GET",
    var useCache: Boolean = true,
    var followRedirects: Boolean = true,
    var responseCode: Int = -1,
    var responseMessage: String = "",
    var responseError: String = "",
    var url: String = "",
    var requestHeaders: HashMap<String, String> = HashMap(),
    var responseHeaders: HashMap<String, List<String>> = HashMap()
) {
    fun disconnect() {
        if ((connection is HttpURLConnection)) {
            (connection as HttpURLConnection).closeConnection()
        }
    }
}