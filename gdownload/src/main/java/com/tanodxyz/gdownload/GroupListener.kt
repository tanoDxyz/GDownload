package com.tanodxyz.gdownload

/**
 *
 */
interface GroupListener {
    /**
     * Will be invoked when new Download is Enqueued and is going to be downloaded.
     * @param groupId groupId
     * @param download download meta
     * @param groupState Group state
     */
    fun onEnqueued(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

    /**
     * This method indicates that Download was just added to the queue and it is unknown when it is going to be started.
     */
    fun onAdded(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

    /**
     * Download is going to be started.
     */
    fun onStarting(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

    /**
     * Downloading started. I/O operations are running.
     */
    fun onDownloading(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

    /**
     * Download is successful
     */
    fun onSuccess(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

    /**
     * Download failed.
     */
    fun onFailure(
        groupId: Long,
        errorMessage: String?,
        download: DownloadInfo,
        groupState: GroupState
    ) {
    }

    /**
     * Download paused
     */
    fun onPaused(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

    /**
     * Download stopped
     */
    fun onStopped(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

    /**
     * [Group] internal [Downloader]s are busy and this download is waiting for it's turn.
     */
    fun onWaitingForTurn(groupId: Long, download: DownloadInfo, groupState: GroupState) {}
}