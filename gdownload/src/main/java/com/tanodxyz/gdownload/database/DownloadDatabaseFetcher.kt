package com.tanodxyz.gdownload.database

import com.tanodxyz.gdownload.Download


interface DownloadDatabaseFetcher {
    fun fetchDownloadFromLocalDatabase(id:Int): Download?
    fun fetchDownloadFromLocalDatabase(filePath:String):Download?
}