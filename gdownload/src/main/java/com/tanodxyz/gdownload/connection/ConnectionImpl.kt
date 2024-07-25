package com.tanodxyz.gdownload.connection

import com.tanodxyz.gdownload.closeConnection
import java.net.HttpURLConnection
import java.net.URLConnection

class ConnectionImpl(private val connection: URLConnection):Connection {
    override fun disconnect() {
        if ((connection is HttpURLConnection)) {
            connection.closeConnection()
        }
    }
}