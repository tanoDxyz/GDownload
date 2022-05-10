package com.tanodxyz.gdownload

import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutor

/**
 * A Downloader is responsible for downloading single resource from the remote server or machine.
 * >
 * ### Submitting or Starting Download.
 * >Download is start by simply providing url and fileName and an optional Listener
 *   or just [Download] object and optional listener.[DownloadProgressListener].
 * ### Checking State
 *>There are two ways to check download state - running in [Downloader].
 * Either provide the [DownloadProgressListener] when starting the download.
 * or add Listener via [Downloader.addListener]
 * There are also other methods like [isBusy], [isFailed], [isAlive], [isSuccess], [isFreeze],
 * [isStopped], [isSuccess] which returns the instant state of the download running.
 * ### Stopping Download
 * >It will shutdown any live [RemoteConnection] to the remote resource. Release any threads
 * that runs any workers and clean the workers.
 * ### Pause/Freeze Download
 * >It is same as stopping the download but it would park threads but clean the workers.
 * So if [Downloader.resumeDownload] is called these parked threads would be used.
 * ### Resume Download
 * >If [Downloader] is in [Downloader.STATE.PAUSED] it will resume the download from the most previous
 * state.
 * ### Restarting Download
 * >if previous download either failed,stopped - restarting will cause it to start from the last
 * known state.
 */
interface Downloader {
    /**
     * Start a download if [Downloader] is not currently busy and tune the [Downloader] according
     * to the provided [Download] object.
     */
    fun download(download: Download, listener: DownloadProgressListener?)

    /**
     * This method calls [Downloader.download]
     */
    fun download(
        url: String,
        name: String,
        networkType: NetworkType,
        listener: DownloadProgressListener?
    )

    /**
     * If this [Downloader] is stopped and can't accept any connections further.
     * trying to call methods on terminated [Downloader] will throw exceptions.
     */
    fun isTerminated(): Boolean

    /**
     * If network change listener is registered any [Downloader.STATE.FAILED],[Downloader.STATE.STOPPED] Downloads will
     * restart only if the [Download] object has same [NetworkType] as that of Device.
     * Similarly any running download inside [Downloader] will be stopped on network change.
     * **By Default it is turned off**
     */
    fun registerNetworkChangeListener()

    /**
     * UnRegistering network change listener will stop affecting any running/stopped/failed downloads.
     */
    fun unRegisterNetworkChangeListener()

    /**
     * Delete any file created by this [Downloader].
     * @param removeFromDatabase specifies if the [Download] should be deleted from database too.
     */
    fun deleteFile(removeFromDatabase: Boolean = false)

    /**
     * Add listener to the list of registered listeners for this specific [Download] running.
     */
    fun addListener(listener: DownloadProgressListener)

    /**
     * Load any recent [Download] from database with specific id if it exists and start it.
     * @param id will be used to load [Download] from database.
     * @param callback notify about the Download loading state.
     */
    fun loadDownloadFromDatabase(id: Int, callback: ((Boolean) -> Unit)? = null)

    /**
     * @see [Downloader.loadDownloadFromDatabase]
     */
    fun loadDownloadFromDatabase(filePath: String, callback: ((Boolean) -> Unit)? = null)

    /**
     * Remove listener for this specific [Download].
     * If [listener] is null, all download listeners will be removed
     */
    fun removeListener(listener: DownloadProgressListener?)

    /**
     * Stop the download
     * @param listener result callback and it shows whether the stop operation succeeded [Boolean]
     * with option message [String]
     */
    fun stopDownload(listener: BiConsumer<Boolean, String>? = null)

    /**
     * Freeze/pause the currently running download.
     * @param listener result callback and it shows whether the pause operation succeeded [Boolean]
     * with option message [String]
     */
    fun freezeDownload(listener: BiConsumer<Boolean, String>? = null)

    /**
     * Resume the currently paused download.
     * @param listener result callback and it shows whether the resume operation succeeded [Boolean]
     * with option message [String]
     */
    fun resumeDownload(listener: BiConsumer<Boolean, String>? = null)

    /**
     * Restart the download if it recently [Downloader.STATE.FAILED] , [Downloader.STATE.STOPPED] , [Downloader.STATE.COMPLETED]
     *
     * @param listener result callback and it shows whether the restart operation succeeded [Boolean]
     * with option message [String]
     */
    fun restart(listener: BiConsumer<Boolean, String>? = null)

    /**
     * Shutdown the [Downloader] irrespective of the state [Downloader.STATE]
     * Best effort is put in to notify the user before complete shutdown.
     */
    fun shutDown(listener: Runnable?)
    val executor: ScheduledBackgroundExecutor
    val activeDownloadPayload: Download?

    /**
     * [Downloader] is busy if it is in the following states.
     * @see [Downloader.STATE.PAUSED]
     * @see [Downloader.STATE.DOWNLOADING]
     * @see [Downloader.STATE.STARTING]
     *
     */
    val isBusy: Boolean

    /**
     * Indicates whether download failed.
     * @see [Downloader.STATE.FAILED]
     */
    val isFailed: Boolean

    /**
     * Indicates whether download is running or froze
     */
    val isAlive: Boolean

    /**
     * Indicates whether download was complete success
     */
    val isSuccess: Boolean

    /**
     * Indicates whether download is paused
     */
    val isFreeze: Boolean

    /**
     * @return the current state of the [Downloader]
     */
    fun getState(): STATE

    /**
     * @return the download failed error message.
     */
    val downloadFailure: String?

    /**
     * Indicates whether download was stopped.
     */
    val isStopped: Boolean

    /**
     * Checks whether Download is started or not.
     */
    val isDownloadStarted: Boolean

    /**
     * Represents the current state of any [Download] running in [Downloader]
     */
    enum class STATE {
        /**
         * [Downloader] is idle for the first time it is constructed.
         */
        IDLE,

        /**
         * Indicates that [Download] is starting.
         */
        STARTING,

        /**
         * Shows that I/O operation is running and Download is running.
         */
        DOWNLOADING,

        /**
         * When download is failed.
         */
        FAILED,

        /**
         * Indicates download success
         */
        COMPLETED,

        /**
         * when download is paused it indicates that all running threads are parked and all
         * [DataReadWriteWorker] were cleaned.
         */
        PAUSED,

        /**
         * when download is stopped.
         */
        STOPPED
    }
}
