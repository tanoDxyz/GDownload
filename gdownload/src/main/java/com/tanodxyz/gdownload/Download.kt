package com.tanodxyz.gdownload

import android.content.ContentValues
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * Encapsulates different kind of information about single download.
 *
 */
open class Download(
    val id: Long = System.nanoTime(),
    /**
     * Url to the remote resource
     */
    private var url: String,
    /**
     * File name or file path on the local storage
     */
    private var filePath: String,
    /**
     * Content length in bytes or else -1 if not available
     */
    private var contentLengthBytes: Long = 0,
    /**
     * Content Length in bytes that has been downloaded
     */
    private var contentLengthDownloaded: Long = 0,
    /**
     * Download status.
     * @see Download.Companion.DOWNLOADED
     * @see Download.Companion.FAILED
     * @see Download.Companion.DOWNLOADING
     * @see Download.Companion.ENQUEUED
     * @see Download.Companion.STARTING
     * @see Download.Companion.STOPPED
     * @see Download.Companion.PAUSED
     */
    private var status: String = ENQUEUED,
    /**
     * Queue/Group id to which this download is assigned
     */
    private var queueId: Long = 1,
    /**
     * Network Type on Which download is allowed
     * @see NetworkType
     */
    private var networkType: Int = NetworkType.valueOf(NetworkType.ALL),
    /**
     * Number of retries that should be made for any connection before this download is
     * considered failed.
     */
    private var connectionRetryCount: Int = DEF_RETRIES_PER_CONNECTION,
    /**
     * Number of concurrent connections in case remote server supports partial or byte range requests.
     */
    private var maxNumberOfConnections: Int = DEF_MAX_CONNECTIONS_PER_DOWNLOAD,

    /**
     * Time in milli seconds after which progress update callback is invoked.
     */
    private var progressUpdateTimeMilliSec: Long = DEF_PROGRESS_UPDATE_MILLISECONDS,
    /**
     * Slice Data for download
     */
    private var sliceData: List<Slice>? = null,
    /**
     * Download total progress
     */
    private var progress: Double = 0.0
) : Serializable {

    /**
     * An estimated time in milliseconds download will take.
     */
    @Transient
    var timeRemainingMilliSeconds: Long = 0L

    /**
     * An estimated time in milliseconds that is elapsed since the start of download.
     */
    @Transient
    var timeElapsedMilliSeconds: Long = 0L

    /**
     * Download speed bytes per second
     */
    @Transient
    var bytesPerSecond: Long = 0L

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Download) {
            return false
        }
        return (filePath == other.filePath && id == other.id)
    }

    override fun toString(): String {
        return JSONObject().apply {
            put(ID, id)
            put(URL, url)
            put(FILE_PATH, filePath)
            put(CONTENT_LENGTH, contentLengthBytes)
            put(CONTENT_LENGTH_DOWNLOADED, contentLengthDownloaded)
            put(STATUS, status)
            put(QUEUE_ID, queueId)
            put(NETWORK_TYPE, networkType)
            put(CONNECTION_RETRY_COUNT, connectionRetryCount)
            put(MAX_NUMBER_CONNECTIONS, maxNumberOfConnections)
            put(DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND, progressUpdateTimeMilliSec)
            put(SLICE_DATA, sliceData)
        }.toString(3)
    }

    @Synchronized
    fun set(
        url: String? = null,
        filePath: String? = null,
        contentLengthBytes: Long? = null,
        contentLengthDownloaded: Long? = null,
        status: String? = null,
        queueId: Long? = null,
        networkType: Int? = null,
        connectionRetryCount: Int? = null,
        maxNumberOfConnections: Int? = null,
        progressUpdateTimeMilliSec: Long? = null,
        progress: Double? = null,
        sliceData: List<Slice>? = null
    ) {
        contentLengthDownloaded?.let { e ->
            this.contentLengthDownloaded = e
        }
        progress?.let { e ->
            this.progress = e
        }
        url?.let { e ->
            this.url = e
        }

        filePath?.let { e ->
            this.filePath = e
        }
        contentLengthBytes?.let { e ->
            this.contentLengthBytes = e
        }
        status?.let { e ->
            this.status = e
        }
        queueId?.let { e ->
            this.queueId = e
        }
        networkType?.let { e ->
            this.networkType = e
        }
        connectionRetryCount?.let { e ->
            this.connectionRetryCount = e
        }
        maxNumberOfConnections?.let { e ->
            this.maxNumberOfConnections = e
        }
        progressUpdateTimeMilliSec?.let { e ->
            this.progressUpdateTimeMilliSec = e
        }
        sliceData?.let { e ->
            this.sliceData = e
        }
    }

    @Synchronized
    fun getProgress(): Double = progress

    @Synchronized
    fun getUrl(): String = url

    @Synchronized
    fun getFilePath(): String = filePath

    @Synchronized
    fun getContentLengthBytes(): Long = contentLengthBytes

    @Synchronized
    fun getContentLengthDownloaded(): Long = contentLengthDownloaded

    @Synchronized
    fun getStatus(): String = status

    @Synchronized
    fun getQueueId(): Long = queueId

    @Synchronized
    fun getNetworkType(): Int = networkType

    @Synchronized
    fun getConnectionRetryCount(): Int = connectionRetryCount

    @Synchronized
    fun getMaxNumberOfConnections(): Int = maxNumberOfConnections

    @Synchronized
    fun getProgressUpdateTimeMilliSeconds(): Long = progressUpdateTimeMilliSec

    @Synchronized
    fun getSliceData(): List<Slice>? = sliceData
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + filePath.hashCode()
        return result
    }

    @Synchronized
    fun prepareForDatabaseWrite(): ContentValues {
        return ContentValues().apply {
            put(URL, url)
            put(FILE_PATH, filePath)
            put(CONTENT_LENGTH, contentLengthBytes)
            put(CONTENT_LENGTH_DOWNLOADED, contentLengthDownloaded)
            put(STATUS, status)
            put(QUEUE_ID, queueId)
            put(DOWNLOAD_ID, id)
            put(NETWORK_TYPE, networkType)
            put(CONNECTION_RETRY_COUNT, connectionRetryCount)
            put(MAX_NUMBER_CONNECTIONS, maxNumberOfConnections)
            put(DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND, progressUpdateTimeMilliSec)
            val byteArrayOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            objectOutputStream.writeObject(sliceData)
            objectOutputStream.flush()
            val sliceBlob = byteArrayOutputStream.toByteArray()
            put(SLICE_DATA, sliceBlob)
            closeResource(objectOutputStream)
        }
    }

    fun isIncompleteDownload(): Boolean {
        return status != DOWNLOADED
    }

    /**
     * Move the file represented by this download object to another path represented by
     * destination file.
     */
    fun moveFile(destinationFile: File, deleteSource: Boolean) {
        moveFileToAnotherDir(File(filePath), destinationFile, deleteSource)
    }

    companion object {
        /**
         * When this download object is enqueued
         */
        const val ENQUEUED = "enqued"

        /**
         * when download is going to start
         */
        const val STARTING = "starting"

        /**
         * download is running and it means I/O is in progress
         */
        const val DOWNLOADING = "downloading"

        /**
         * download completed
         */
        const val DOWNLOADED = "downloaded"

        /**
         * download paused and can be resumed
         */
        const val PAUSED = "paused"

        /**
         * download stopped
         */
        const val STOPPED = "stopped"

        /**
         * download failed
         */
        const val FAILED = "failed"

        fun getState(state: Downloader.STATE): String {
            return when (state) {
                Downloader.STATE.DOWNLOADING -> DOWNLOADING
                Downloader.STATE.COMPLETED -> DOWNLOADED
                Downloader.STATE.FAILED -> FAILED
                Downloader.STATE.PAUSED -> PAUSED
                Downloader.STATE.STOPPED -> STOPPED
                Downloader.STATE.STARTING -> STARTING
                else -> ENQUEUED
            }
        }

    }
}