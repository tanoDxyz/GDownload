package com.tanodxyz.gdownload

import androidx.lifecycle.Lifecycle
import com.tanodxyz.gdownload.executors.BackgroundExecutor

class GroupCallbackHandler(
    mainThread: Boolean = false,
    lifecycle: Lifecycle? = null,
    backgroundExecutor: BackgroundExecutor? = null
) : CallbacksHandler(mainThread, backgroundExecutor, lifecycle) {
    private val groupListeners = mutableListOf<GroupListener>()

    @Synchronized
    fun addListener(listener: GroupListener?) {
        if (listener != null && (!groupListeners.contains(listener))) {
            groupListeners.add(listener)
        }
    }

    @Synchronized
    fun removeListener(listener: GroupListener) {
        groupListeners.remove(listener)
    }

    @Synchronized
    private fun forEachListener(callback: (GroupListener) -> Unit) {
        groupListeners.forEach { groupListener ->
            callback(groupListener)
        }
    }

    @Synchronized
    fun getListeners(): List<GroupListener> {
        return groupListeners
    }

    fun runOnMain(callback: () -> Unit) {
        mainThreadHandler.post(callback)
    }

    @Synchronized
    override fun clean() {
        super.clean()
        groupListeners.clear()
    }


    fun notifyStateDownloadAdded(groupState: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onAdded(groupState.id, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadEnqueued(groupState: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onEnqueued(groupState.id, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadStopped(groupState: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onStopped(groupState.id, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadPaused(groupState: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onPaused(groupState.id, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadSuccess(groupState: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onSuccess(groupState.id, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadRunning(groupState: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onDownloading(groupState.id, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadFailed(
        groupState: GroupState,
        downloadInfo: DownloadInfo,
        error: String?
    ) {
        Runnable {
            forEachListener {
                it.onFailure(groupState.id, error, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadStarting(groupState: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onStarting(groupState.id, downloadInfo, groupState)
            }
        }.apply { runOnSelectedThread() }
    }

    fun notifyStateDownloadWaiting(state: GroupState, downloadInfo: DownloadInfo) {
        Runnable {
            forEachListener {
                it.onWaitingForTurn(state.id, downloadInfo, state)
            }
        }.apply { runOnSelectedThread() }
    }
}