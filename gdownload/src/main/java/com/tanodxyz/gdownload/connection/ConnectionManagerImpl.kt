package com.tanodxyz.gdownload.connection

import com.tanodxyz.gdownload.DefaultLogger
import com.tanodxyz.gdownload.Factory
import com.tanodxyz.gdownload.Slice
import com.tanodxyz.gdownload.closeResource
import com.tanodxyz.gdownload.executors.BackgroundExecutor

class ConnectionManagerImpl(
    private var defaultConnectionHandlerFactory: Factory<URLConnectionHandler>,
    private var backgroundExecutorImpl: BackgroundExecutor
) : ConnectionManager {
    private val connections: MutableList<RemoteConnection> = mutableListOf()
    private lateinit var connectionCallbacksRefsList: MutableList<BackgroundExecutor.Cancelable?>
    val TAG = "ConnectionManager"
    private val logger = DefaultLogger(TAG)

    @Synchronized
    override fun createConnections(
        url: String,
        numSlices: Int,
        retriesPerConnection: Int,
        slices: List<Slice>?,
        callback: ConnectionManager.ConnectionManagerCallback?
    ) {
        logger.d("Creating connections")
        val connectionHandler = defaultConnectionHandlerFactory.instance
        val connErrorPair = createConnection(connectionHandler, url, retriesPerConnection)
        val connection = connErrorPair.second
        val error = connErrorPair.first
        connection?.addConnectionToList()
        if (connection == null) {
            logger.e("Failed to connect to remote host! ${error?.localizedMessage}")
            connection.connectionCallback(
                connectionHandler,
                true,
                error.toString(),
                callback = callback
            )
        } else {
            var startedDownload = false
            callback?.onConnectionStart(connection.contentLength) { shouldStartDownloading ->
                if (shouldStartDownloading && (!startedDownload)) {
                    startedDownload = true
                    logger.d("Connection Established with remote host")
                    if (connection.concurrentDownloadRequirementsFulfilled(numSlices)) {
                        logger.d("Multi Connection Downloads Requirements")
                        val newSlices =
                            slices.getInCompletedSlices() ?: createSlices(
                                connection.contentLength,
                                numSlices
                            )
                        callback.onConnectionEstablished(
                            connection.acceptRanges,
                            connection.contentLength,
                            newSlices.count(),
                            newSlices
                        )
                        closeResource(connection.inputResourceWrapper)
                        connectionCallbacksRefsList = MutableList(newSlices.count()) { null }
                        logger.d("Making multiple connections to remote host -> count ${newSlices.count()}")
                        for (i: Int in 0 until newSlices.count()) {
                            val slice = newSlices[i]
                            val connectionCallbackRef = backgroundExecutorImpl.execute {
                                logger.d("Making connection with #$i ")
                                createConnectionCallback(
                                    url,
                                    i,
                                    newSlices.count(),
                                    retriesPerConnection,
                                    slice,
                                    callback
                                )
                                logger.d("Connection process done.")
                            }
                            synchronized(this) {
                                connectionCallbacksRefsList.add(connectionCallbackRef)
                            }
                        }
                    } else {
                        logger.d("Multi Connection Downloads Requirements not fulfilled -> single connection download")
                        val newSlices = createSlices(connection.contentLength, 1)
                        callback.onConnectionEstablished(
                            connection.acceptRanges,
                            connection.contentLength,
                            1,
                            newSlices
                        )
                        connection.connectionCallback(
                            connectionHandler,
                            false,
                            callback = callback,
                            slice = newSlices[0]
                        )
                    }
                } else {
                    logger.e("failed to make connections and hence error ${error.toString()}")
                    connection.connectionCallback(
                        connectionHandler,
                        true,
                        error.toString(),
                        callback = callback
                    )
                }
            }
        }
    }

    private fun List<Slice>?.getInCompletedSlices(): List<Slice>? {
        if (this == null) {
            return null
        }
        val incompleteSlices = this.filter { !it.downloadComplete.get() }
        return if (incompleteSlices.isNullOrEmpty()) null else incompleteSlices
    }

    private fun RemoteConnection?.connectionCallback(
        connectionHandler: URLConnectionHandler,
        failure: Boolean,
        failureMessage: String? = null,
        slice: Slice? = null,
        connectionCount: Int = 1,
        currentConnection: Int = 1,
        callback: ConnectionManager.ConnectionManagerCallback?
    ) {
        if (failure) {
            callback?.onConnectionFailure("$failureMessage")
        } else {
            callback?.onDownloadableConnection(
                connectionCount,
                currentConnection,
                ConnectionManager.ConnectionData(
                    this,
                    connectionHandler,
                    slice ?: Slice(1, 0, this!!.contentLength, 0, false)
                )
            )
        }
    }

    private fun RemoteConnection.addConnectionToList() {
        try {
            synchronized(connections) {
                connections.add(this)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun createConnectionCallback(
        url: String,
        indexSlice: Int,
        totalSliceCount: Int,
        retryTimesPerConnection: Int,
        slice: Slice,
        callback: ConnectionManager.ConnectionManagerCallback?
    ) {
        logger.d("Creating connection for URL = $url : slicesCount = $totalSliceCount : indexSlice = $indexSlice : retriesPerConnection = $retryTimesPerConnection : slice=$slice")
        val connectionHandler = defaultConnectionHandlerFactory.instance

        val slicedConnectionPair = createConnection(
            connectionHandler,
            url,
            retryTimesPerConnection,
            slice.startByte,
            slice.endByte,
            slice.downloaded.get()
        )
        val slicedConnection = slicedConnectionPair.second
        slicedConnection.connectionCallback(
            connectionHandler,
            slicedConnectionPair.first != null,
            slicedConnectionPair.first.toString(),
            slice,
            totalSliceCount,
            indexSlice + 1,
            callback = callback
        )
        slicedConnection?.addConnectionToList()
    }

    private fun RemoteConnection.concurrentDownloadRequirementsFulfilled(numSlices: Int) =
        (contentLength > -1 && acceptRanges && numSlices > 1)

    override fun shutDownNow() {
        synchronized(connections) {
            connections.forEach { connection ->
                connection.disconnect()
            }
            connections.clear()
        }
        synchronized(this) {
            if (this::connectionCallbacksRefsList.isInitialized) {
                connectionCallbacksRefsList.forEach { connectionCallbackRef ->
                    connectionCallbackRef?.cancel()
                }
                connectionCallbacksRefsList.clear()
            }
        }
    }

    private fun createConnection(
        urlConnectionHandler: URLConnectionHandler,
        url: String,
        retriesCount: Int = 1,
        start: Long = 0,
        end: Long = 0,
        downloaded: Long = 0
    ): Pair<Exception?, RemoteConnection?> {
        return urlConnectionHandler.makeConnection(url, retriesCount, start, end, downloaded)
    }

    private fun createSlices(contentLength: Long, count: Int): List<Slice> {
        if (count <= 0) {
            throw IllegalArgumentException("no slices for count -> $count")
        }

        val slices = mutableListOf<Slice>() //todo
        var slicesRemainder = 0L
        var sliceLength = contentLength
        val slicesCount = if (contentLength <= -1 || contentLength <= count) {
            1
        } else {
            slicesRemainder = contentLength % count
            sliceLength = contentLength / count
            count
        }
        var byteStart = 0L
        var byteEnd = sliceLength
        for (i: Int in 1..slicesCount) {
            if (i == slicesCount) {
                byteEnd += slicesRemainder
            }
            val slice = Slice(i, byteStart, byteEnd, 0L, false)
            byteStart = (byteEnd + 1)
            byteEnd += (sliceLength)
            slices.add(slice)
        }
        return slices
    }
}