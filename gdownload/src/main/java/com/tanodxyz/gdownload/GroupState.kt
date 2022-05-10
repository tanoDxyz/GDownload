package com.tanodxyz.gdownload

data class GroupState(

    /**
     * The group id.
     * */
    val id: Long,

    val name: String,

    /**
     * All downloads belonging to this group.
     * */
    val downloads: List<DownloadInfo>,

    /**
     * All queued downloads belonging to this group.
     * */
    val queuedDownloads: List<DownloadInfo>,

    /**
     * All paused downloads belonging to this group.
     * */
    val pausedDownloads: List<DownloadInfo>,

    /**
     * All downloading downloads belonging to this group.
     * */
    val runningDownloads: List<DownloadInfo>,

    /**
     * All completed downloads belonging to this group.
     * */
    val completedDownloads: List<DownloadInfo>,

    /**
     * All cancelled downloads belonging to this group.
     * */
    val cancelledDownloads: List<DownloadInfo>,

    /**
     * All failed downloads belonging to this group.
     * */
    val failedDownloads: List<DownloadInfo>,

    /**
     * The groups downloading progress. -1 if the group progress is indeterminate.
     * */
    val groupDownloadProgress: Double
)
