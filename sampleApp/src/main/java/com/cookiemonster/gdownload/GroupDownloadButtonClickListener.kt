package com.cookiemonster.gdownload

import com.tanodxyz.gdownload.DownloadInfo


interface GroupDownloadButtonClickListener {
        fun onPauseClicked(downloadId: Long) {}
        fun onResumeClicked(downloadId: Long) {}
        fun onRestartClicked(downloadId: Long) {}
        fun onCancelClicked(downloadId: Long) {}
        fun onDownloadClicked(download: DownloadInfo) {}
    }