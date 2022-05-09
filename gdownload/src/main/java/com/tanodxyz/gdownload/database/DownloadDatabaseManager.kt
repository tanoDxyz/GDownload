package com.tanodxyz.gdownload.database

import com.tanodxyz.gdownload.Download

/**
 * The prime responsibility is to handle all the CRUD operations in local database related to
 * downloading.
 */
interface DownloadDatabaseManager {
    /**
     * Insert a download in database.
     */
    fun insertDownload(download: Download)

    /**
     * Insert a download in database only if it does not exist otherwise update it.
     */
    fun insertOrUpdateDownload(Download: Download)

    /**
     * Delete download by file path and returns affected rows
     * @return affected rows
     */
    fun deleteDownloadByFilePath(filePath: String): Int

    /**
     * Delete all the Download objects stored in the database
     * @return rows affected
     */
    fun deleteAllDownloads(): Int

    /**
     *   Delete all the Download objects stored in the database based on status
     * @see Download.Companion.DOWNLOADED
     * @see Download.Companion.FAILED
     * @see Download.Companion.DOWNLOADING
     * @see Download.Companion.ENQUEUED
     * @see Download.Companion.STARTING
     * @see Download.Companion.STOPPED
     * @see Download.Companion.PAUSED
     * @return rows affected
     */
    fun deleteDownloads(status: String): Int

    /**
     * As download can also runs in groups and each group has unique id.
     * The general contract of this method is to find all downloads for a specific group provided
     * group Id
     * @return List containing Downloads
     */
    fun findAllGroupDownloads(groupId: Long): MutableList<Download>

    /**
     * @return all the previously saved incomplete downloads.
     */
    fun findAllInCompleteDownloads(): MutableList<Download>

    /**
     * @return find and returns the first download by file path
     */
    fun findDownloadByFilePath(filePath: String): Download?

    /**
     * @return finds the download by it's id.
     */
    fun findDownloadByDownloadId(id: Int): Download?

    /**
     * @return all the downloads from database.
     */
    fun getAll(): MutableList<Download>

    /**
     * @return checks whether database is currently open or not.
     */
    fun isOpen(): Boolean

    /**
     * close the database
     */
    fun release()
}