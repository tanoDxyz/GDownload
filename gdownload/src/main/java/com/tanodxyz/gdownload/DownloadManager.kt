package com.tanodxyz.gdownload

import android.content.Context
import androidx.core.util.Pair
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.Lifecycle
import com.tanodxyz.gdownload.DownloadManager.Builder
import com.tanodxyz.gdownload.connection.ConnectionManager
import com.tanodxyz.gdownload.connection.ConnectionManagerImpl
import com.tanodxyz.gdownload.connection.URLConnectionFactory
import com.tanodxyz.gdownload.connection.URLConnectionHandler
import com.tanodxyz.gdownload.database.DownloadDatabaseFetcherImpl
import com.tanodxyz.gdownload.database.DownloadDatabaseManager
import com.tanodxyz.gdownload.database.SQLiteManager
import com.tanodxyz.gdownload.executors.BackgroundExecutor
import com.tanodxyz.gdownload.executors.BackgroundExecutorImpl
import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutor
import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutorImpl
import com.tanodxyz.gdownload.io.DefaultFileStorageHelper
import com.tanodxyz.gdownload.io.FileStorageHelper
import com.tanodxyz.gdownload.io.OutputResourceWrapper
import com.tanodxyz.gdownload.io.RandomAccessOutputResourceWrapper
import com.tanodxyz.gdownload.worker.DataReadWriteWorkersManager
import com.tanodxyz.gdownload.worker.DataReadWriteWorkersManagerImpl
import java.io.File
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil

/**
 * An implementation of [Downloader] interface.
 * >
 * ### Creation or Instantiation
 * >Although! Direct Instantiation is possible but use [DownloadManager.Builder] or [GDownload] methods
 * for ease.
 * ### LifeCycle Aware Callbacks
 * >All download listener callbacks can be bound to specific [Lifecycle] if user wants to.
 * to do so either use [GDownload] methods that takes [Lifecycle] as an argument or create
 * new Downloader with [Builder]
 * ### Threading
 * >All methods that returns something as obvious are executed in the caller thread.
 * All others methods will not block the calling thread.
 * @see [Downloader]
 */
open class DownloadManager(
    private val scheduledBackgroundExecutorImpl: ScheduledBackgroundExecutor,
    private val fileStorageHelper: FileStorageHelper,
    private val downloadCallbacksHandler: DownloadCallbacksHandler,
    private val connectionManager: ConnectionManager,
    private val databaseManager: DownloadDatabaseManager,
    private val networkInfoProvider: NetworkInfoProvider,
    private val dataReadWriteWorkersManager: DataReadWriteWorkersManager
) : Downloader {
    val TAG = "DMgr-${System.nanoTime()}"
    private var logger = DefaultLogger(TAG)
    private lateinit var downloadPayload: Download
    private var currentState: Downloader.STATE = Downloader.STATE.IDLE
    private var databaseOperationsCallback: ScheduledBackgroundExecutor.CallbackState? = null
    private var remoteServerAcceptRanges = false
    private var outputIsRandomAccessFile = false
    private var totalConnectionsPerDownloadEstablished = 0
    private var contentLength = 0L
    private var createdFile: File? = null
    private var outputResourceWrapper: OutputResourceWrapper? = null
    private var downloadError: String? = null
    private var incomingCallsExecutor =
        BackgroundExecutorImpl()
    private var totalConnectionsDataWriteCount: AtomicInteger = AtomicInteger(0)
    override val executor: ScheduledBackgroundExecutor
        get() = scheduledBackgroundExecutorImpl
    override val activeDownloadPayload: Download?
        get() = if (this::downloadPayload.isInitialized) downloadPayload else null
    override val isBusy: Boolean
        get() = getState() != Downloader.STATE.FAILED && getState() != Downloader.STATE.COMPLETED
                && getState() != Downloader.STATE.IDLE && getState() != Downloader.STATE.STOPPED
    override val isFailed: Boolean
        get() = getState() == Downloader.STATE.FAILED
    override val isAlive: Boolean
        get() {
            val currState = getState()
            return currState == Downloader.STATE.DOWNLOADING ||
                    currState == Downloader.STATE.PAUSED || currState == Downloader.STATE.STARTING
        }
    override val isSuccess: Boolean
        get() = getState() == Downloader.STATE.COMPLETED
    override val isFreeze: Boolean
        get() = getState() == Downloader.STATE.PAUSED
    override val downloadFailure: String?
        get() = downloadError
    override val isStopped: Boolean
        get() = getState() == Downloader.STATE.STOPPED
    override val isDownloadStarted: Boolean
        get() = isBusy

    override fun isTerminated(): Boolean {
        return (incomingCallsExecutor.isTerminated() || scheduledBackgroundExecutorImpl.isTerminated())
    }

    override fun toString(): String {
        return "Downloader-$TAG"
    }

    @Throws(IllegalArgumentException::class)
    override fun download(download: Download, listener: DownloadProgressListener?) {
        logger.d("submitted for download $download")
        Runnable {
            if (setupAndCanStartDownload(download, listener, true)) {
                download.apply {
                    connectionManager.createConnections(
                        getUrl(),
                        getMaxNumberOfConnections(),
                        getConnectionRetryCount(),
                        getSliceData(),
                        ConnectionsCallbacksReceiver()
                    )
                }
                logger.d("Last thread returned home safe")
            }
        }.runOnBackgroundThread(true)
    }

    /**
     * The general contract is to make sure that [download] is constructed correctly.
     * check if network is available and download can be started on.
     * @throws IllegalArgumentException If [download] is not constructed correctly
     */
    protected fun setupAndCanStartDownload(
        download: Download,
        listener: DownloadProgressListener?,
        removePreviousListeners: Boolean = false
    ): Boolean {
        ensureDownloadObjectIsCorrectlyCreated(download)
        return if (isBusy) {
            listener?.apply {
                downloadCallbacksHandler.notifyStateBusy(
                    listener,
                    download.getDownloadInfo()
                )
            }
            false
        } else {
            val (networkAvailableAndCanStartDownload, msg) =
                checkIfNetworkIsAvailableAndCanStartDownloadOn(download)
            if (networkAvailableAndCanStartDownload) {
                reset()
                if (removePreviousListeners) {
                    downloadCallbacksHandler.clean()
                }
                this.downloadPayload = download
                this.setState(Downloader.STATE.STARTING)
                downloadCallbacksHandler.addListener(listener)
            } else {
                listener?.apply {
                    downloadCallbacksHandler.notifyStateDownloadStartFailed(
                        listener,
                        download.getDownloadInfo(),
                        msg
                    )
                }
            }
            networkAvailableAndCanStartDownload
        }
    }

    protected fun ensureDownloadObjectIsCorrectlyCreated(download: Download) {
        download.apply {
            if (this.getProgressUpdateTimeMilliSeconds() <= 0) {
                throw IllegalArgumentException("Progress update time must be  ->   x > 0")
            }
            if (this.getMaxNumberOfConnections() < 1 || this.getMaxNumberOfConnections() > DEF_MAX_CONNECTIONS_PER_DOWNLOAD) {
                throw IllegalArgumentException("Max connections per download  -> x > 0 && x <= $DEF_MAX_CONNECTIONS_PER_DOWNLOAD")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    internal fun download0(
        download: Download,
        listener: DownloadProgressListener?,
        removePreviousListeners: Boolean = false,
    ) {
        logger.d("downloading $download")
        if (setupAndCanStartDownload(download, listener, removePreviousListeners)) {
            download.apply {
                Runnable {
                    connectionManager.createConnections(
                        getUrl(),
                        getMaxNumberOfConnections(),
                        getConnectionRetryCount(),
                        getSliceData(),
                        ConnectionsCallbacksReceiver()
                    )
                    logger.d("Last thread returned home safe")
                }.runOnBackgroundThread(true)
            }
        }
    }

    @Synchronized
    internal fun setState(state: Downloader.STATE) {
        this.currentState = state
        this.downloadPayload.set(status = Download.getState(state))
    }

    /**
     * This class will handle the [ConnectionManager.ConnectionManagerCallback] from [ConnectionManager]
     */
    private inner class ConnectionsCallbacksReceiver : ConnectionManager.ConnectionManagerCallback {

        override fun onConnectionStart(contentLength: Long, callback: (Boolean) -> Unit) {
            downloadCallbacksHandler.notifyShouldStartDownload(contentLength, callback)
        }

        private var lastTickBytes: Long = 0
        private var downloadLastTickTimeNanos = 0L
        private var elapsedMillis = 0L
        private var callbackTickStart = 0L
        override fun onConnectionEstablished(
            remoteServerAcceptRanges: Boolean,
            contentLength: Long,
            totalConnections: Int,
            slices: List<Slice>
        ) {
            logger.d(
                "Connection established RemoteResourceAcceptRanges" +
                        " = $remoteServerAcceptRanges : content-Length = $contentLength : total connections = $totalConnections "
            )
            val dm = this@DownloadManager
            dm.remoteServerAcceptRanges = remoteServerAcceptRanges
            outputIsRandomAccessFile = /*true*/
                (totalConnections > 1 && remoteServerAcceptRanges)
            totalConnectionsPerDownloadEstablished = totalConnections
            if (contentLength > 0) {
                dm.contentLength = contentLength
            }
            val exceptionWhileCreatingFile = createFile()
            if (exceptionWhileCreatingFile != null) {
                onConnectionFailure(exceptionWhileCreatingFile ?: ERROR_MSG_FAILED_TO_CREATE_FILE)
            } else {
                createOutputResourceWrapper(outputIsRandomAccessFile)
                downloadPayload.set(
                    filePath = createdFile.toString(),
                    contentLengthBytes = contentLength,
                    maxNumberOfConnections = totalConnections,
                    sliceData = updateSliceData(downloadPayload.getSliceData(), slices)
                )
                dataReadWriteWorkersManager.init(outputIsRandomAccessFile, outputResourceWrapper!!)


                val downloadIsMultiConnection = outputIsRandomAccessFile
                downloadCallbacksHandler.notifyStateDownloadServerSupportsMultiConnectionDownload(
                    downloadPayload.getDownloadInfo(),
                    downloadIsMultiConnection
                )
                setState(Downloader.STATE.DOWNLOADING)
                downloadCallbacksHandler.notifyStateDownloadStarting(
                    DownloadInfo.newInstance(
                        downloadPayload
                    )
                )
                databaseOperationsCallback = scheduledBackgroundExecutorImpl.executeAtFixRateAfter(
                    this::progressSaveAndUpdates,
                    downloadPayload.getProgressUpdateTimeMilliSeconds(),
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun updateSliceData(
            source: List<Slice>?,
            updatedPartialData: List<Slice>?
        ): List<Slice> {

            if (source == null) {
                return updatedPartialData!!
            } else if (updatedPartialData == null) {
                return source
            } else {
                updatedPartialData.forEach { updatedSlice ->
                    val sliceObjectsInSourceToBeUpdated = source.filter { it.id == updatedSlice.id }
                    if (sliceObjectsInSourceToBeUpdated.isNotEmpty() && sliceObjectsInSourceToBeUpdated.count() == 1) {
                        val sliceNeedToBeUpdated = sliceObjectsInSourceToBeUpdated[0]
                        if (updatedSlice.isSame(sliceNeedToBeUpdated)) {
                            sliceNeedToBeUpdated.downloaded = updatedSlice.downloaded
                            sliceNeedToBeUpdated.downloadComplete = updatedSlice.downloadComplete
                        } else {
                            throw Exception("Slice mis match amigo") //
                        }
                    }
                }
                return source
            }
        }

        private fun progressSaveAndUpdates() {
            var downloadedBytesTotal = 0L
            val totalBytes = downloadPayload.getContentLengthBytes()
            downloadPayload.getSliceData()?.forEach { slice ->
                downloadedBytesTotal += slice.downloaded.get()
            }
            var databaseWrite = false
            if (downloadedBytesTotal > lastTickBytes) {
                databaseWrite = true
            }
            val currentTickBytes = downloadedBytesTotal - lastTickBytes
            lastTickBytes = downloadedBytesTotal


            if (callbackTickStart < 1) {
                callbackTickStart = System.currentTimeMillis()
            }
            val diff = System.currentTimeMillis() - callbackTickStart
            elapsedMillis = diff
            if (currentTickBytes > 0) {
                val currentNano = System.nanoTime()
                val elapsedNanos = currentNano - downloadLastTickTimeNanos
                val currentTickMillis = abs(elapsedNanos.toDouble() / (1000.0 * 1000.0))
                downloadLastTickTimeNanos = currentNano
                val seconds = currentTickMillis / (1000.0)
                val bytesPerSecond = currentTickBytes / seconds
                var progress = -1.0
                var estimatedTimeRemainingInMilliSecs = 0L
                if (totalBytes > 0) {
                    val remainingSeconds =
                        (totalBytes - downloadedBytesTotal).toDouble() / bytesPerSecond
                    estimatedTimeRemainingInMilliSecs =
                        abs(ceil(remainingSeconds)).toLong() * 1000
                    progress = (downloadedBytesTotal.toDouble() / totalBytes.toDouble() * 100.0)
                }
                downloadPayload.set(
                    progress = progress,
                    contentLengthDownloaded = downloadedBytesTotal
                )
                downloadPayload.apply {
                    timeElapsedMilliSeconds = elapsedMillis
                    timeRemainingMilliSeconds = estimatedTimeRemainingInMilliSecs
                    this.bytesPerSecond = bytesPerSecond.toLong()
                }

                val progressInstance = DownloadInfo.newInstance(
                    downloadPayload
                )
                downloadCallbacksHandler.notifyStateDownloadProgress(progressInstance)
            }
            if (databaseWrite) {
                databaseManager.insertOrUpdateDownload(downloadPayload)
            }
            stopDatabaseCallbackIfDownloadIsNotRunning()
        }

        override fun onConnectionFailure(message: String) {
            logger.d("Connection failure -> $message")
            if (isFailed) {
                return
            }
            downloadError = message
            setState(Downloader.STATE.FAILED)
            closeResources(deleteFile = false, shutDownProgressCallback = true)
            downloadCallbacksHandler.notifyStateDownloadFailed(
                downloadPayload.getDownloadInfo(),
                message
            )
        }

        override fun onDownloadableConnection(
            totalConnections: Int,
            connectionIndex: Int,
            connectionData: ConnectionManager.ConnectionData
        ) {
            logger.d("readable connection totalConnections = $totalConnections : connectionIndex = $connectionIndex :  ")
            if (isFailed || isStopped) {
                return
            }
            downloadCallbacksHandler.notifyNewConnectionMadeToServer(
                connectionData.slice?.copy() ?: connectionData.slice,
                downloadPayload.getDownloadInfo()
            )
            val errorInWritingDataToDisk = dataReadWriteWorkersManager.addWorker(connectionData)
            if (errorInWritingDataToDisk != null) {
                onConnectionFailure(errorInWritingDataToDisk.toString())
            } else {
                val connectionDataSavedCount = totalConnectionsDataWriteCount.incrementAndGet()
                if (connectionDataSavedCount == totalConnections) {
                    downloadCompleted()
                }
            }
        }

    }

    protected fun downloadCompleted() {
        logger.d("Download completed")
        closeResources()
        if (!isStopped) {
            setState(Downloader.STATE.COMPLETED)
            downloadCallbacksHandler.notifyStateDownloadCompleted(
                DownloadInfo.newInstance(
                    downloadPayload
                )
            )
        }
        logger.d("resources closed")
    }

    protected fun closeResources(
        deleteFile: Boolean = false,
        shutDownProgressCallback: Boolean = false
    ) {
        outputResourceWrapper?.close()
        if (deleteFile) {
            fileStorageHelper.deleteFile(createdFile)
        }
        if (shutDownProgressCallback) {
            databaseOperationsCallback?.cancel()
        }
        connectionManager.shutDownNow()
        dataReadWriteWorkersManager.release()
    }

    protected fun createOutputResourceWrapper(randomAccess: Boolean) {
        logger.d("creating output resource wrapper")
        if (outputResourceWrapper == null) {
            outputResourceWrapper =
                fileStorageHelper.getOutputResourceWrapper(createdFile, randomAccess)
            setOutputResourceLength(randomAccess)
            logger.d("output resource wrapper created")
        }

    }

    protected fun stopDatabaseCallbackIfDownloadIsNotRunning() {
        when (getState()) {
            Downloader.STATE.COMPLETED, Downloader.STATE.STOPPED, Downloader.STATE.FAILED -> {
                databaseOperationsCallback?.cancel()
            }
            Downloader.STATE.PAUSED -> {
                databaseOperationsCallback?.pause()
            }
            else -> {
            }
        }
    }

    protected fun restartDatabaseCallbackIfDownloadIsResumed() {
        logger.d("Restarting download progress callback as if it is resumed")
        when (getState()) {
            Downloader.STATE.DOWNLOADING -> {
                databaseOperationsCallback?.resume()
            }
            else -> {
                logger.d("Did nothing")
            }
        }
    }

    protected fun setOutputResourceLength(randomAccess: Boolean = false) {
        if (contentLength > 0 && randomAccess) {
            (outputResourceWrapper as RandomAccessOutputResourceWrapper).setFileLength(
                contentLength
            )
        }
    }

    protected fun createFile(): String? {
        var exception: Exception? = null
        try {
            val existingFilePath = downloadPayload.getFilePath()
            val fileName = try {
                val idx = existingFilePath.lastIndexOf(File.separatorChar)
                val fileNameStartIdx = if (idx <= 0) 0 else idx + 1
                existingFilePath.substring(fileNameStartIdx)
            } catch (ex: Exception) {
                "file-${System.currentTimeMillis()}"
            }
            val existingFile = File(existingFilePath)
            createdFile = if (existingFile.exists()) {
                existingFile
            } else {
                if (existingFile.isDirectory) {
                    fileStorageHelper.filesRoot = existingFile
                } else {
                    if (existingFile.parent != null) {
                        fileStorageHelper.filesRoot = existingFile.parentFile
                    }
                }
                fileStorageHelper.createFile(fileName, false)
            }
            if (createdFile!!.parent!!.bytesAvailable() < contentLength) {
                throw Exception(ERROR_MSG_INSUFFICIENT_STORAGE)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            exception = ex
        }
        return (exception?.toString())
    }

    override fun download(
        url: String,
        name: String,
        networkType: NetworkType,
        listener: DownloadProgressListener?
    ) {
        val download =
            Download(System.nanoTime(), url, name, networkType = networkType.value)
        download(download, listener)
    }

    override fun registerNetworkChangeListener() {
        networkInfoProvider.registerNetworkChangeListener(this::onNetworkChanged)
    }

    override fun unRegisterNetworkChangeListener() {
        networkInfoProvider.unregisterNetworkChangeListener(this::onNetworkChanged)
    }

    override fun deleteFile(removeFromDatabase: Boolean) {
        fileStorageHelper.deleteFile(createdFile)
        if (removeFromDatabase) {
            databaseManager.deleteDownloadByFilePath(downloadPayload.getFilePath())
        }
    }

    override fun addListener(listener: DownloadProgressListener) {
        Runnable {
            downloadCallbacksHandler.addListener(listener)
        }.runOnBackgroundThread(true)
    }

    override fun loadDownloadFromDatabase(id: Int, callback: ((Boolean) -> Unit)?) {
        Runnable {
            if (isBusy) {
                callback?.invoke(false)
            }
            val fetchedDownloadFromLocalDatabase =
                DownloadDatabaseFetcherImpl(databaseManager).fetchDownloadFromLocalDatabase(id)
            fetchedDownloadFromLocalDatabase?.apply {
                if (this.isIncompleteDownload()) {
                    download(fetchedDownloadFromLocalDatabase, null)
                }
            }
            val loadedFromDbs = fetchedDownloadFromLocalDatabase != null
            callback?.invoke(loadedFromDbs)
            downloadCallbacksHandler.notifyStateDownloadLoadedFromDatabase(
                id,
                fetchedDownloadFromLocalDatabase
            )
        }.runOnBackgroundThread(runOnIncomingCallsBackgroundExecutor = true)
    }

    override fun loadDownloadFromDatabase(filePath: String, callback: ((Boolean) -> Unit)?) {
        Runnable {
            if (isBusy) {
                throw IllegalStateException("Cannot load download via filepath. downloader already busy ")
            }
            val fetchedDownloadFromLocalDatabase =
                DownloadDatabaseFetcherImpl(databaseManager).fetchDownloadFromLocalDatabase(filePath)
            fetchedDownloadFromLocalDatabase?.apply {
                if (this.isIncompleteDownload()) {
                    download(fetchedDownloadFromLocalDatabase, null)
                }
            }
            val loadedFromDbs = fetchedDownloadFromLocalDatabase != null
            callback?.invoke(loadedFromDbs)
            downloadCallbacksHandler.notifyStateDownloadLoadedFromDatabase(
                filePath,
                fetchedDownloadFromLocalDatabase
            )
        }.runOnBackgroundThread(runOnIncomingCallsBackgroundExecutor = true)
    }

    override fun removeListener(listener: DownloadProgressListener?) {
        Runnable {
            downloadCallbacksHandler.removeListener(listener)
        }.runOnBackgroundThread(true)
    }

    override fun stopDownload(listener: BiConsumer<Boolean, String>?) {
        Runnable {
            var stopFailedErrorMessage = ""
            if (isAlive) {

                dataReadWriteWorkersManager.stopAllWorkers { stopped, msg ->
                    val stopMsg = if (!stopped) generateErrorMessageForPauseResumeStopRestart(
                        STOP, getState()
                    ) else msg
                    if (stopped) {
                        setState(Downloader.STATE.STOPPED)
                    }
                    listener?.accept(
                        stopped, stopMsg
                    )
                    downloadCallbacksHandler.notifyStateDownloadStop(
                        DownloadInfo.newInstance(
                            downloadPayload
                        ), stopped, stopMsg
                    )
                }
                return@Runnable

            } else {
                stopFailedErrorMessage =
                    generateErrorMessageForPauseResumeStopRestart(STOP, getState())
            }
            listener?.accept(false, stopFailedErrorMessage)
            downloadCallbacksHandler.notifyStateDownloadStop(
                DownloadInfo.newInstance(
                    downloadPayload
                ), false, stopFailedErrorMessage
            )
        }.runOnBackgroundThread(true)
    }

    protected fun generateErrorMessageForPauseResumeStopRestart(
        process: String,
        state: Downloader.STATE
    ): String {
        return when (process) {
            FREEZE -> {
                val cantFreezeDownloadStr = "$ERROR_STR_CANNOT ${FREEZE}"
                when (state) {
                    Downloader.STATE.IDLE -> {
                        "$cantFreezeDownloadStr -> $ERROR_DOWNLOAD_IDLE"
                    }
                    Downloader.STATE.STARTING -> {
                        "$cantFreezeDownloadStr -> $ERROR_DOWNLOAD_NOT_FULLY_STARTED"
                    }
                    Downloader.STATE.FAILED -> {
                        "$cantFreezeDownloadStr -> $ERROR_DOWNLOAD_FAILED_ALREADY"
                    }
                    Downloader.STATE.STOPPED -> {
                        "$cantFreezeDownloadStr -> $ERROR_DOWNLOAD_STOPPED_ALREADY"
                    }
                    Downloader.STATE.COMPLETED -> {
                        "$cantFreezeDownloadStr -> $ERROR_DOWNLOAD_COMPLETED_ALREADY"
                    }
                    Downloader.STATE.PAUSED -> {
                        "$cantFreezeDownloadStr -> $ERROR_DOWNLOAD_PAUSED_ALREADY"
                    }
                    else -> {
                        cantFreezeDownloadStr
                    }
                }
            }
            STOP -> {
                val cantStopDownloadStr = "$ERROR_STR_CANNOT $STOP"
                when (state) {
                    Downloader.STATE.IDLE -> {
                        "$cantStopDownloadStr -> $ERROR_DOWNLOAD_IDLE"
                    }
                    Downloader.STATE.FAILED -> {
                        "$cantStopDownloadStr -> $ERROR_DOWNLOAD_FAILED_ALREADY"
                    }
                    Downloader.STATE.COMPLETED -> {
                        "$cantStopDownloadStr -> $ERROR_DOWNLOAD_COMPLETED_ALREADY"
                    }
                    Downloader.STATE.STOPPED -> {
                        "$cantStopDownloadStr -> $ERROR_DOWNLOAD_STOPPED_ALREADY"
                    }
                    else -> {
                        cantStopDownloadStr
                    }
                }
            }

            RESUME -> {
                val canResumeDownloadStr = "$ERROR_STR_CANNOT $RESUME"
                when (state) {
                    Downloader.STATE.IDLE -> {
                        "$canResumeDownloadStr -> $ERROR_DOWNLOAD_IDLE"
                    }
                    Downloader.STATE.STARTING -> {
                        "$canResumeDownloadStr -> $ERROR_DOWNLOAD_NOT_FULLY_STARTED"
                    }
                    Downloader.STATE.DOWNLOADING -> {
                        "$canResumeDownloadStr -> $ERROR_DOWNLOAD_ALREADY_RUNNING"
                    }
                    Downloader.STATE.FAILED -> {
                        "$canResumeDownloadStr -> $ERROR_DOWNLOAD_FAILED_RESTART_IT_"
                    }
                    Downloader.STATE.COMPLETED -> {
                        "$canResumeDownloadStr -> $ERROR_DOWNLOAD_COMPLETED_ALREADY"
                    }
                    Downloader.STATE.STOPPED -> {
                        "$canResumeDownloadStr -> $ERROR_DOWNLOAD_STOPPED_RESTART_IT_"
                    }
                    else -> {
                        canResumeDownloadStr
                    }
                }
            }

            RESTART -> {
                val cantRestartDownloadStr = "$ERROR_STR_CANNOT $RESTART"
                when (state) {
                    Downloader.STATE.STARTING -> {
                        "$cantRestartDownloadStr -> $ERROR_DOWNLOAD_ALREADY_STARTED_STOP_IT_FIRST"
                    }
                    Downloader.STATE.DOWNLOADING, Downloader.STATE.PAUSED -> {
                        "$cantRestartDownloadStr -> $ERROR_DOWNLOAD_ALREADY_RUNNING_STOP_IT_FIRST"
                    }
                    else -> {
                        cantRestartDownloadStr
                    }
                }
            }
            else -> " Unknown "
        }
    }

    override fun freezeDownload(listener: BiConsumer<Boolean, String>?) {
        Runnable {
            var pausedFailedErrorMessage: String = ""
            if (isBusy) {
                if (outputIsRandomAccessFile) {
                    dataReadWriteWorkersManager.pauseAllWorkers { paused, msg ->
                        val freezeMsg = if (!paused) generateErrorMessageForPauseResumeStopRestart(
                            FREEZE, getState()
                        ) else msg
                        if (paused) {
                            setState(Downloader.STATE.PAUSED)
                        }
                        listener?.accept(paused, freezeMsg)
                        downloadCallbacksHandler.notifyStateDownloadPause(
                            DownloadInfo.newInstance(
                                downloadPayload
                            ), paused, freezeMsg
                        )
                    }
                    return@Runnable
                } else {
                    pausedFailedErrorMessage = ERROR_MSG_PAUSE_DOWNLOAD_NOT_SUPPORTED
                }
            } else {
                pausedFailedErrorMessage =
                    generateErrorMessageForPauseResumeStopRestart(FREEZE, getState())
            }
            listener?.accept(false, pausedFailedErrorMessage)
            downloadCallbacksHandler.notifyStateDownloadPause(
                DownloadInfo.newInstance(
                    downloadPayload
                ), false, pausedFailedErrorMessage
            )
        }.runOnBackgroundThread(true)
    }

    override fun resumeDownload(listener: BiConsumer<Boolean, String>?) {
        Runnable {
            val resumeFailedErrorMessage: String
            if (isFreeze) {
                if (outputIsRandomAccessFile) {
                    dataReadWriteWorkersManager.resumeAllWorkers { resumed, msg ->
                        val resumeMessage =
                            if (!resumed) generateErrorMessageForPauseResumeStopRestart(
                                RESUME, getState()
                            ) else msg
                        if (resumed) {
                            setState(Downloader.STATE.DOWNLOADING)
                            restartDatabaseCallbackIfDownloadIsResumed() //because we are resuming it
                        }
                        listener?.accept(resumed, resumeMessage)
                        downloadCallbacksHandler.notifyStateDownloadResume(
                            DownloadInfo.newInstance(
                                downloadPayload
                            ), resumed, resumeMessage
                        )
                    }
                    return@Runnable
                } else {
                    resumeFailedErrorMessage = ERROR_MSG_RESUME_DOWNLOAD_NOT_SUPPORTED
                }
            } else {
                resumeFailedErrorMessage =
                    generateErrorMessageForPauseResumeStopRestart(RESUME, getState())
            }
            listener?.accept(false, resumeFailedErrorMessage)
            downloadCallbacksHandler.notifyStateDownloadResume(
                DownloadInfo.newInstance(
                    downloadPayload
                ), false, resumeFailedErrorMessage
            )
        }.runOnBackgroundThread(true)
    }

    override fun shutDown(listener: Runnable?) {
        Runnable {
            closeResources(shutDownProgressCallback = true)
            unRegisterNetworkChangeListener()
            downloadCallbacksHandler.clean()
            scheduledBackgroundExecutorImpl.shutDown()
            incomingCallsExecutor.shutDown()
            listener?.run()
        }.runOnBackgroundThread(runOnIncomingCallsBackgroundExecutor = true)
    }

    protected fun clearIncomingCallsExecutorQueue() {
        incomingCallsExecutor.cleanUp()
    }

    override fun restart(listener: BiConsumer<Boolean, String>?) {
        Runnable {
            if (isAlive) {
                val restartFailMessage =
                    generateErrorMessageForPauseResumeStopRestart(RESTART, getState())
                listener?.accept(false, restartFailMessage)
                downloadCallbacksHandler.notifyStateDownloadRestart(
                    downloadPayload.getDownloadInfo(), false,
                    restartFailMessage
                )
            } else {
                listener?.accept(true, "Restarting....")
                downloadCallbacksHandler.notifyStateDownloadRestart(
                    downloadPayload.getDownloadInfo(), true,
                    "Restarted"
                )
                download0(downloadPayload, null)
            }
        }.runOnBackgroundThread(true)
    }

    protected fun reset() {
        clearIncomingCallsExecutorQueue()
        executor.cleanUp()
        dataReadWriteWorkersManager.release()
        downloadError = null
        remoteServerAcceptRanges = false
        outputIsRandomAccessFile = false
        totalConnectionsPerDownloadEstablished = 0
        contentLength = 0
        createdFile = null
        outputResourceWrapper = null
        totalConnectionsDataWriteCount.set(0)
        databaseOperationsCallback?.cancel()
    }

    @Synchronized
    override fun getState(): Downloader.STATE {
        return currentState
    }

    protected fun Runnable.runOnBackgroundThread(runOnIncomingCallsBackgroundExecutor: Boolean = false): Unit =
        if (runOnIncomingCallsBackgroundExecutor) {
            incomingCallsExecutor.execute(this);Unit
        } else {
            scheduledBackgroundExecutorImpl.execute(this);Unit
        }

    protected fun onNetworkChanged() {
        try {
            logger.d("Network changed...")
            fun stopDownload() {
                if (isAlive) {
                    this@DownloadManager.stopDownload()
                }
            }
            Runnable {
                if (networkInfoProvider.isNetworkAvailable) {
                    logger.d("network available ")
                    if (networkInfoProvider.isOnAllowedNetwork(NetworkType.valueOf(downloadPayload.getNetworkType()))) {
                        logger.d("on Desired network")
                        if (isStopped || isFailed) {
                            logger.d("restarting")
                            restart()
                        }
                    } else {
                        stopDownload()
                    }
                }

            }.runOnBackgroundThread(true)
        } catch (ex: Exception) {
            if (ex is RejectedExecutionException) {
                unRegisterNetworkChangeListener()
            }
        }
    }

    protected fun checkIfNetworkIsAvailableAndCanStartDownloadOn(download: Download): Pair<Boolean, String> {
        var allowedDownload = true
        var msg = "Allowed!"
        if (networkInfoProvider.isNetworkAvailable) {
            if (!networkInfoProvider.isOnAllowedNetwork(
                    networkType = NetworkType.valueOf(
                        download.getNetworkType()
                    )
                )
            ) {
                msg = ERROR_DOWNLOAD_NOT_ALLOWED_ON_SELECTED_NETWORK
                allowedDownload = false
            }
        } else {
            allowedDownload = false
            msg = ERROR_NETWORK_NOT_AVAILABLE
        }
        return Pair(allowedDownload, msg)
    }

    class Builder(var context: Context) {
        private var progressCallbacksOnMainThread: Boolean = true
        private var dataReadWriteWorkerManager: DataReadWriteWorkersManager? = null
        private var scheduledBackgroundExecutor: ScheduledBackgroundExecutor? = null
        private var downloadProgressCallbacksHandler: DownloadCallbacksHandler? = null
        private var fileStorageHelper: FileStorageHelper? = null
        private var connectionManager: ConnectionManager? = null
        private var downloadDatabaseManager: DownloadDatabaseManager? = null
        private var lifecycle: Lifecycle? = null
        private var networkInfoProvider: NetworkInfoProvider? = null

        fun setLifeCycle(lifecycle: Lifecycle): Builder {
            this.lifecycle = lifecycle
            return this
        }

        fun setRunProgressCallbacksOnMainThread(runOnMainThread: Boolean = true): Builder {
            progressCallbacksOnMainThread = runOnMainThread
            return this
        }

        fun setScheduledBackgroundExecutor(
            executor: ScheduledBackgroundExecutor
        ): Builder {
            scheduledBackgroundExecutor = executor
            return this
        }

        fun setCallbacksHandler(downloadProgressCallbacksHandler: DownloadCallbacksHandler): Builder {
            this.downloadProgressCallbacksHandler = downloadProgressCallbacksHandler
            return this
        }

        fun setCallbacksHandler(runCallbacksOnMainThread: Boolean = true): Builder {
            this.downloadProgressCallbacksHandler =
                DownloadCallbacksHandler(runCallbacksOnMainThread)
            return this
        }

        fun setCallbacksHandler(runCallbacksOnMainThread: Boolean, lifecycle: Lifecycle): Builder {
            this.downloadProgressCallbacksHandler = DownloadCallbacksHandler(
                runCallbacksOnMainThread,
                lifecycle,
                BackgroundExecutorImpl()
            )
            return this
        }

        fun setStorageHelper(fileStorageHelper: FileStorageHelper): Builder {
            this.fileStorageHelper = fileStorageHelper
            return this
        }

        fun setStorageHelper(context: Context, downloadFilesRoot: File): Builder {
            this.fileStorageHelper = DefaultFileStorageHelper(context.applicationContext)
            this.fileStorageHelper?.filesRoot = downloadFilesRoot
            return this
        }

        fun setStorageHelper(context: Context): Builder {
            this.fileStorageHelper = DefaultFileStorageHelper(context.applicationContext)
            this.fileStorageHelper?.setFilesRootToDownloadsOrFallbackToInternalDirectory()
            return this
        }

        fun setConnectionManager(
            urlConnectionFactory: Factory<URLConnectionHandler>? = null,
            executor: BackgroundExecutor? = null
        ): Builder {
            val connFactory = urlConnectionFactory ?: URLConnectionFactory()
            val backgroundExecutor = executor ?: scheduledBackgroundExecutor
            connectionManager = ConnectionManagerImpl(connFactory, backgroundExecutor!!)
            return this
        }

        fun setConnectionManager(connectionManager: ConnectionManager): Builder {
            this.connectionManager = connectionManager
            return this
        }

        fun setDownloadDatabaseManager(downloadDatabaseManager: DownloadDatabaseManager): Builder {
            this.downloadDatabaseManager = downloadDatabaseManager
            return this
        }

        fun setDownloadDatabaseManager(context: Context): Builder {
            this.downloadDatabaseManager = SQLiteManager.getInstance(context)
            return this
        }

        fun setNetworkInfoProvider(networkInfoProvider: NetworkInfoProvider): Builder {
            this.networkInfoProvider = networkInfoProvider
            return this
        }

        fun setDataReadWriteWorkersManager(dataReadWriteWorkersManager: DataReadWriteWorkersManager): Builder {
            this.dataReadWriteWorkerManager = dataReadWriteWorkersManager
            return this
        }

        fun build(): DownloadManager {
            val scheduledBackgroundExecutorNonNull = if (lifecycle != null) {
                ScheduledBackgroundExecutorImpl(DEF_MAX_THREADS_PER_EXECUTOR, lifecycle)
            } else {
                scheduledBackgroundExecutor ?: ScheduledBackgroundExecutorImpl(
                    DEF_MAX_THREADS_PER_EXECUTOR
                )
            }
            val downloadCallbacksHandler = if (lifecycle != null) {
                DownloadCallbacksHandler(this.progressCallbacksOnMainThread, lifecycle)
            } else {
                downloadProgressCallbacksHandler ?: DownloadCallbacksHandler()
            }
            return DownloadManager(
                scheduledBackgroundExecutorNonNull,
                fileStorageHelper ?: DefaultFileStorageHelper(context),
                downloadCallbacksHandler,
                connectionManager ?: ConnectionManagerImpl(
                    URLConnectionFactory(),
                    scheduledBackgroundExecutorNonNull
                ),
                this.downloadDatabaseManager ?: SQLiteManager.getInstance(context),
                networkInfoProvider ?: NetworkInfoProvider(context),
                dataReadWriteWorkerManager ?: DataReadWriteWorkersManagerImpl()
            )
        }
    }
}