package com.tanodxyz.gdownload

import androidx.lifecycle.Lifecycle
import com.tanodxyz.gdownload.executors.BackgroundExecutor
import java.util.*


class DownloadCallbacksHandler(
    callbackThreadMain: Boolean = false,
    lifecycle: Lifecycle? = null,
    backgroundExecutor: BackgroundExecutor? = null,
) : CallbacksHandler(
    callbackThreadMain = callbackThreadMain,
    lifecycle = lifecycle,
    executor = backgroundExecutor
) {
    private val downloadProgressListeners: MutableList<DownloadProgressListener> =
        Collections.synchronizedList(
            mutableListOf()
        )
    val TAG = "DCH-${System.nanoTime()}"
    private val logger = DefaultLogger(TAG)
    fun addListener(listener: DownloadProgressListener?) {
        if (listener != null && (!downloadProgressListeners.contains(listener))) {
            logger.d("Download listener added")
            downloadProgressListeners.add(listener)
        }
    }

    fun removeListener(listener: DownloadProgressListener?) {
        logger.d("Download listener removed")
        if(listener == null) {
            downloadProgressListeners.clear()
        }else {
            downloadProgressListeners.remove(listener)
        }
    }

    fun getListeners(): List<DownloadProgressListener> {
        return downloadProgressListeners
    }

    fun notifyStateBusy(
        downloadProgressListener: DownloadProgressListener,
        downloadInfo: DownloadInfo
    ) {
        logger.d("Notify - state busy to listeners")
        Runnable {
            downloadProgressListener.onDownloadFailed(
                downloadInfo, (ERROR_MSG_DOWNLOAD_START_FAILED_DOWNLOADER_BUSY)
            )
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadStartFailed(
        downloadProgressListener: DownloadProgressListener,
        downloadInfo: DownloadInfo,
        msg: String
    ) {
        logger.d("Notify - state failed to listeners")
        Runnable {
            downloadProgressListener.onDownloadFailed(
                downloadInfo, (msg)
            )
        }.apply { runOnSelectedThread() }
    }

    override fun clean() {
        logger.d("Cleaning")
        super.clean()
        downloadProgressListeners.clear()
        logger.d("Cleaned.")
    }

    fun notifyStateDownloadStarting(downloadInfo: DownloadInfo) {
        logger.d("Notify - state download starting to listeners")
        Runnable {
            forEachProgressListener {
                it.onConnectionEstablished(downloadInfo)
            }
        }.apply { runOnSelectedThread() }
    }


    private fun forEachProgressListener(callback: (DownloadProgressListener) -> Unit) {
        downloadProgressListeners.forEach { downloadProgressListener ->
            callback(downloadProgressListener)
        }
    }

    fun notifyStateDownloadFailed(downloadInfo: DownloadInfo, message: String) {
        logger.d("Notify - state download failed -> $message :  to listeners")
        Runnable {
            forEachProgressListener {
                it.onDownloadFailed(downloadInfo, message)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadRestart(
        downloadInfo: DownloadInfo,
        restarted: Boolean,
        message: String
    ) {
        logger.d("Notify - state download restart to listeners")
        Runnable {
            forEachProgressListener {
                it.onRestart(downloadInfo, restarted, message)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadCompleted(downloadInfo: DownloadInfo) {
        logger.d("Notify - state downloadComplete to listeners")
        Runnable {
            forEachProgressListener {
                it.onDownloadSuccess(downloadInfo)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadStop(downloadInfo: DownloadInfo, stopped: Boolean, message: String) {
        logger.d("Notify - state download stop to listeners")
        Runnable {
            forEachProgressListener {
                it.onStop(downloadInfo, stopped, message)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadPause(downloadInfo: DownloadInfo, paused: Boolean, message: String) {
        logger.d("Notify - state download pause to listeners")
        Runnable {
            forEachProgressListener {
                it.onPause(downloadInfo, paused, message)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadResume(downloadInfo: DownloadInfo, resumed: Boolean, message: String) {
        logger.d("Notify - state download resume to listeners")
        Runnable {
            forEachProgressListener {
                it.onResume(downloadInfo, resumed, message)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadServerSupportsMultiConnectionDownload(
        downloadInfo: DownloadInfo,
        downloadIsMultiConnection: Boolean
    ) {
        logger.d("Notify - state download is multi connection to listeners")
        Runnable {
            forEachProgressListener {
                it.onDownloadIsMultiConnection(
                    downloadInfo,
                    downloadIsMultiConnection
                )
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyShouldStartDownload(contentLength: Long, downloadStartCallback: (Boolean) -> Unit) {
        forEachProgressListener {
            it.shouldStartDownload(contentLength, downloadStartCallback)
        }
    }

    fun notifyStateDownloadProgress(progressInstance: DownloadInfo) {
        Runnable {
            forEachProgressListener { downloadProgressListener ->
                downloadProgressListener.onDownloadProgress(progressInstance)
            }
        }.apply { runOnSelectedThread() }

    }


    fun notifyNewConnectionMadeToServer(slice: Slice?, downloadInfo: DownloadInfo) {
        logger.d("Notify - state new connection made to server -> to listeners")
        Runnable {
            forEachProgressListener {
                it.onConnection(downloadInfo, slice)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadLoadedFromDatabase(
        id: Int,
        fetchedDownloadFromLocalDatabase: Download?
    ) {
        logger.d("Notify - state download loaded from database via id -> to listeners")
        Runnable {
            forEachProgressListener {
                it.onDownloadLoadedFromDatabase(id, fetchedDownloadFromLocalDatabase)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadLoadedFromDatabase(
        filePath: String,
        fetchedDownloadFromLocalDatabase: Download?
    ) {
        logger.d("Notify - state download loaded from database via filepath -> to listeners")
        Runnable {
            forEachProgressListener {
                it.onDownloadLoadedFromDatabase(filePath, fetchedDownloadFromLocalDatabase)
            }
        }.apply { runOnSelectedThread() }
    }
}