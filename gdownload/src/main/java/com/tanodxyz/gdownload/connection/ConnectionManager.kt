package com.tanodxyz.gdownload.connection


import com.tanodxyz.gdownload.Slice
import java.io.IOException

/**
 *
 * Primary contract of this interface is to make connections to the Remote Service.
 * Connection = it is the combination of [RemoteConnection],[URLConnectionHandler] and [Slice].
 *              in other words each connection is the combination of input stream and it's start and
 *              end index in the content Length of the remote resource.
 */
interface ConnectionManager {

    /**
     * This will try to connect to the [url] remote resource and make [numSlices] connections.
     * For each connection if some exception occurs it will
     * retry to connect [retriesPerConnection] times.
     * if [slices] are not null it means [numSlices] flag will be ignored and [slices] will be used instead
     */
    @Throws(IOException::class)
    fun createConnections(
        url: String, numSlices: Int = 1,
        retriesPerConnection: Int = 3,
        slices: List<Slice>? = null,
        callback: ConnectionManagerCallback?
    )

    /**
     * Will immediately close all live connection and remove all other idle connections.
     * Idle connection = That is not yet made but in a queue or waiting list.
     */
    fun shutDownNow()

    /**
     * Whenever [ConnectionManager] makes a new connection - following methods are called at appropriate time.
     */
    interface ConnectionManagerCallback {
        /**
         * If the [ConnectionManager] is successful in making first connection to remote resource.
         * this method is called.
         * [remoteServerAcceptRanges] whether remote service supports Ranged downloads
         * [contentLength] of the resource (-1 in case of unknown content length)
         * [totalConnections] total connection that would be established
         * [slices] represents each connection byte ranges
         */
        fun onConnectionEstablished(
            remoteServerAcceptRanges: Boolean,
            contentLength: Long = -1,
            totalConnections: Int = 1,
            slices: List<Slice>,
        )

        /**
         * When [ConnectionManager] make a first connection to remote resource and get the necessary
         * information about the resource this method is called and [callback] specify whether
         * we should move further or not.
         */
        fun onConnectionStart(contentLength: Long = -1, callback: (Boolean) -> Unit)

        /**
         * when any problems occurs while making a connection to remote resource
         * [message] exact cause
         */
        fun onConnectionFailure(message: String)

        /**
         * The general contract of this method is as follows.
         * whenever [ConnectionManager] makes a new connection and this connection is Downloadable.
         * [totalConnections] max number of connection [ConnectionManager] will establish
         * [connectionIndex] this connection's index
         * [connectionData] data that encapsulate connection.
         */
        fun onDownloadableConnection(
            totalConnections: Int,
            connectionIndex: Int,
            connectionData: ConnectionData
        )

    }

    data class ConnectionData(
        var remoteConnection: RemoteConnection? = null,
        var connectionFactory: URLConnectionHandler,
        var slice: Slice? = null
    )
}