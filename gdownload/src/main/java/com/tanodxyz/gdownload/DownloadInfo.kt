package com.tanodxyz.gdownload

import android.content.Context
import java.io.File
import java.lang.Exception
import java.text.DecimalFormat

/**
 * Immutable snapshot of [Download]
 */
class DownloadInfo private constructor(
    val id: Long,
    val url: String,
    val filePath: String,
    val contentLengthBytes: Long,
    val downloadedContentLengthBytes: Long,
    val status: String,
    val queueId: Long,
    val networkType: Int,
    val connectionRetryCount: Int,
    val maxNumberOfConnections: Int,
    val sliceData: List<Slice>? = null,
    val timeRemainingMilliSeconds: Long,
    val timeElapsedMilliSeconds: Long,
    val bytesPerSecond: Long,
    val progress: Double
) {

    /**
     * @return elapsed time in ##Hours ##minutes ##seconds format
     */
    fun getElapsedTimeStr(context: Context): String? =
        getETAString(context, timeElapsedMilliSeconds, elapsed = true)

    /**
     * @return remaining time in ##Hours ##minutes ##seconds format
     */
    fun getTimeRemainingStr(context: Context): String? =
        getETAString(context, timeRemainingMilliSeconds)

    /**
     * @return download speed
     */
    fun getDownloadSpeedStr(context: Context): String =
        getDownloadSpeedString(context, bytesPerSecond)

    /**
     * @return String indicating DownloadedBytes / TotalBytes.
     */
    fun getDownloadedTotalStr(): String =
        "${getSize(downloadedContentLengthBytes)} / ${getSize(contentLengthBytes)}"


    fun getFileName(): String {
        return try {
            val idx = filePath.lastIndexOf(File.separatorChar)
            val fileNameStartIdx = if (idx <= 0) 0 else idx + 1
            filePath.substring(fileNameStartIdx)
        } catch (ex: Exception) {
            filePath
        }
    }

    /**
     * @see getNormalizedProgress
     */
    fun getNormalizedProgressStr(): String {
        return "${getNormalizedProgress()} %"
    }

    /**
     * Sometimes for some resources content length is not available and it is hard to guess the
     * progress in such cases a term is used called Intermediate progress.
     * @return progress = 100 % in case of intermediate progress.
     */
    fun getNormalizedProgress(): Double = if (progress < 0) 100.0 else progress

    /**
     * @see [Download.moveFile]
     */
    fun moveFile(destinationFile: File, deleteSource: Boolean) {
        moveFileToAnotherDir(File(filePath), destinationFile, deleteSource)
    }

    companion object {
        fun newInstance(
            download: Download
        ): DownloadInfo {
            val it = download
            return DownloadInfo(
                it.id,
                it.getUrl(),
                it.getFilePath(),
                it.getContentLengthBytes(),
                it.getContentLengthDownloaded(),
                it.getStatus(),
                it.getQueueId(),
                it.getNetworkType(),
                it.getConnectionRetryCount(),
                it.getMaxNumberOfConnections(),
                it.getSliceData(),
                it.timeRemainingMilliSeconds,
                it.timeElapsedMilliSeconds,
                it.bytesPerSecond,
                it.getProgress()
            )
        }

    }
}