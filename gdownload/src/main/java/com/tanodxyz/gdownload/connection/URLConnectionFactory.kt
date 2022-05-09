package com.tanodxyz.gdownload.connection

import com.tanodxyz.gdownload.DEFAULT_USER_AGENT
import com.tanodxyz.gdownload.DEF_CONNECTION_READ_TIMEOUT
import com.tanodxyz.gdownload.DEF_CONNECTION_TIMEOUT
import com.tanodxyz.gdownload.Factory

class URLConnectionFactory: Factory<URLConnectionHandler> {
    override fun getInstance(): URLConnectionHandler {
        val defaultURLConnectionHandler = DefaultURLConnectionHandler(false)
        defaultURLConnectionHandler.readTimeOut = DEF_CONNECTION_READ_TIMEOUT
        defaultURLConnectionHandler.connectionTimeOut = DEF_CONNECTION_TIMEOUT
        defaultURLConnectionHandler.addDefaultHeaders(userAgent = DEFAULT_USER_AGENT)
        return defaultURLConnectionHandler
    }
}