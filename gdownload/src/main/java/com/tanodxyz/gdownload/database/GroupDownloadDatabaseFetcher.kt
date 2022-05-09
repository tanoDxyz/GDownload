package com.tanodxyz.gdownload.database

import com.tanodxyz.gdownload.Download

open class GroupDownloadDatabaseFetcher(dbManager: DownloadDatabaseManager) :
    DownloadDatabaseFetcherImpl(dbManager) {
    fun fetchAllGroupDownloads(groupId: Long): MutableList<Download> {
        return dbManager.findAllGroupDownloads(groupId)
    }
    fun fetchAllGroupInCompleteDownloads(groupId: Long):List<Download> {
        return fetchAllGroupDownloads(groupId).filter { it.isIncompleteDownload() }
    }
}