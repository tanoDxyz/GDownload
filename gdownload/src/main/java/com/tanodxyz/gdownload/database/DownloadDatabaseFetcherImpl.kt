package com.tanodxyz.gdownload.database

import com.tanodxyz.gdownload.Download
import com.tanodxyz.gdownload.database.DownloadDatabaseFetcher

open class DownloadDatabaseFetcherImpl(internal val dbManager: DownloadDatabaseManager):
    DownloadDatabaseFetcher {
    override fun fetchDownloadFromLocalDatabase(id: Int): Download? {
        return dbManager.findDownloadByDownloadId(id)
    }

    override fun fetchDownloadFromLocalDatabase(filePath: String): Download? {
        return dbManager.findDownloadByFilePath(filePath)
    }

}