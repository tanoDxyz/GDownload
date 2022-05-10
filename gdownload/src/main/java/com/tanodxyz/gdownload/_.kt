package com.tanodxyz.gdownload

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import androidx.annotation.ChecksSdkIntAtLeast
import java.io.*
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URLConnection
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.text.DecimalFormat

const val ID = "_id"
const val DOWNLOAD_ID = "download_id"
const val URL = "url"
const val FILE_PATH = "filePath"
const val CONTENT_LENGTH = "contentLength"
const val CONTENT_LENGTH_DOWNLOADED = "contentLengthDownloaded"
const val STATUS = "status"
const val QUEUE_ID = "queueID"
const val NETWORK_TYPE = "networkType"
const val CONNECTION_RETRY_COUNT = "connRetryCount"
const val MAX_NUMBER_CONNECTIONS = "maxNumberOfConnections"
const val SLICE_DATA = "sliceData"
const val DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND = "progressUpdateTimeMilliSeconds"
const val GROUP_DEFAULT_DOWNLOAD_CAPACITY = 4
const val DEF_MAX_THREADS_PER_EXECUTOR = 33
const val DEF_MAX_CONNECTIONS_PER_DOWNLOAD = 32
const val DEF_RETRIES_PER_CONNECTION = 3
const val DEF_PROGRESS_UPDATE_MILLISECONDS = 500L
const val DEF_CONNECTION_READ_TIMEOUT = 15_000
const val DEF_GROUP_LOOP_INTERVAL_MILLISECONDS = 1_000L
const val DEF_CONNECTION_TIMEOUT = 15_000
const val KILO: Long = 1024
const val MEGA = KILO * KILO
const val GIGA = MEGA * KILO
const val TERA = GIGA * KILO
const val FREEZE = "Pause/Freeze"
const val STOP = "Stop"
const val RESUME = "Resume"
const val RESTART = "Restart"

internal const val HEADER_ACCEPT_RANGE = "Accept-Ranges"

internal const val HEADER_ACCEPT_RANGE_LEGACY = "accept-ranges"

internal const val HEADER_ACCEPT_RANGE_COMPAT = "AcceptRanges"

internal const val HEADER_CONTENT_LENGTH = "content-length"

internal const val HEADER_CONTENT_LENGTH_LEGACY = "Content-Length"

internal const val HEADER_CONTENT_LENGTH_COMPAT = "ContentLength"

internal const val HEADER_TRANSFER_ENCODING = "Transfer-Encoding"

internal const val HEADER_TRANSFER_LEGACY = "transfer-encoding"

internal const val HEADER_TRANSFER_ENCODING_COMPAT = "TransferEncoding"

internal const val HEADER_CONTENT_RANGE = "Content-Range"

internal const val HEADER_CONTENT_RANGE_LEGACY = "content-range"

internal const val HEADER_CONTENT_RANGE_COMPAT = "ContentRange"

const val NETWORK_CHECK_URL = "https://www.google.com"

const val DEFAULT_LOGGING_TAG = "GDownload"

const val USER_AGENT_CHROME_WINDOWS_1 =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36"
const val USER_AGENT_CHROME_ANDROID_12 =
    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36"
const val USER_AGENT_FIREFOX_ANDROID_12 =
    "Mozilla/5.0 (Android 12; Mobile; rv:68.0) Gecko/68.0 Firefox/94.0"
const val USER_AGENT_CHROME_WINDOWS =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36"
const val CONTENT_TYPE_TEXT_PLAIN = "text/plain"
const val CONTENT_TYPE_BINARY_FILE = "application/octet-stream"
const val CONTENT_TYPE_EVERYTHING = "*/*"
const val DEFAULT_USER_AGENT: String = USER_AGENT_FIREFOX_ANDROID_12

const val ERROR_MSG_DOWNLOAD_START_FAILED_WRONG_NETWORK =
    "Download Start Failed. Started on Wrong Network "
const val ERROR_MSG_DOWNLOAD_START_FAILED_DOWNLOADER_BUSY =
    "Download Start Failed. downloader busy "
const val DOWNLOAD_PAUSE_FAILED_NO_RUNNING_WORKERS =
    "can't pause download as it seems already completed or stopped"
const val ERROR_MSG_FAILED_TO_CREATE_FILE = "Failed to create file on disk! "
const val ERROR_MSG_INSUFFICIENT_STORAGE = "insufficient disk to save file!"
const val ERROR_MSG_STOP_DOWNLOAD_NOT_SUPPORTED =
    "stopping is not supported for single thread downloads."
const val ERROR_MSG_PAUSE_DOWNLOAD_NOT_SUPPORTED =
    "freez/pause is not supported for single thread downloads."
const val ERROR_STR_CANNOT = "Can't "
const val ERROR_MSG_RESUME_DOWNLOAD_NOT_SUPPORTED =
    "resuming is not supported for single thread downloads."
const val ERROR_STOP_DOWNLOAD_BEFORE_RESTARTING_IT = "stop download before restarting it."
const val ERROR_DOWNLOAD_IDLE = "Downloader is IDle"
const val ERROR_DOWNLOAD_NOT_FULLY_STARTED = "Downloader is not started fully"
const val ERROR_DOWNLOAD_FAILED_ALREADY = "Download already failed"
const val ERROR_DOWNLOAD_COMPLETED_ALREADY = "Download already completed"
const val ERROR_DOWNLOAD_STOPPED_ALREADY = "Download already Stopped"
const val ERROR_DOWNLOAD_PAUSED_ALREADY = "Download already paused"
const val ERROR_DOWNLOAD_ALREADY_RUNNING = "Download already running"
const val ERROR_DOWNLOAD_FAILED_RESTART_IT_ = "Download failed! restart it rather resuming"
const val ERROR_DOWNLOAD_STOPPED_RESTART_IT_ = "Download stopped! restart it rather resuming"
const val ERROR_DOWNLOAD_ALREADY_STARTED_STOP_IT_FIRST = "Download already started! stop it first"
const val ERROR_DOWNLOAD_ALREADY_RUNNING_STOP_IT_FIRST = "Download already running! stop it first"
const val ERROR_DOWNLOAD_NOT_ALLOWED_ON_SELECTED_NETWORK =
    "Error! Download not allowed on selected network."
const val ERROR_NETWORK_NOT_AVAILABLE = "Error! Network not available."
fun String.bytesAvailable(): Long {
    val stat = StatFs(this)
    val bytesAvailable: Long = if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.JELLY_BEAN_MR2
    ) {
        stat.blockSizeLong * stat.availableBlocksLong
    } else {
        stat.blockSize.toLong() * stat.availableBlocks.toLong()
    }
    return bytesAvailable
}

@ChecksSdkIntAtLeast(parameter = 0)
fun isApiVersionEqualOrHigher(version: Int) = Build.VERSION.SDK_INT >= version
fun isAndroid10Plus() = isApiVersionEqualOrHigher(29)
fun <T : Closeable> closeResource(resource: T?) {
    try {
        resource?.close()
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
}

fun File.getMimeType(): String {
    return try {
        if (isApiVersionEqualOrHigher(26)) {
            val path: Path = toPath()
            Files.probeContentType(path)
        } else {
            val connection: URLConnection = toURI().toURL().openConnection()
            return connection.contentType
        }
    } catch (ex: Exception) {
        "application/octet-stream"
    }
}

fun Uri.getFilePath(
    contentResolver: ContentResolver
): String? {
    val filePath: String
    val filePathColumn = arrayOf(MediaStore.MediaColumns.DATA)
    val cursor: Cursor = contentResolver.query(this, filePathColumn, null, null, null)!!
    cursor.moveToFirst()
    val columnIndex: Int = cursor.getColumnIndex(filePathColumn[0])
    filePath = cursor.getString(columnIndex)
    closeResource(cursor)
    return filePath
}

//courtesy Fetch
fun String.isUriPath(): Boolean = this.takeIf { it.isNotEmpty() }
    ?.let { it.startsWith("content://") || it.startsWith("file://") }
    ?: false

fun Context.isOnWiFi(): Boolean {
    val manager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetworkInfo = manager.activeNetworkInfo
    return if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
        activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI
    } else {
        false
    }
}

fun Context.isOnMeteredConnection(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= 16) {
        cm.isActiveNetworkMetered
    } else {
        val info: NetworkInfo = cm.activeNetworkInfo ?: return true
        when (info.type) {
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_MOBILE_DUN,
            ConnectivityManager.TYPE_MOBILE_HIPRI,
            ConnectivityManager.TYPE_MOBILE_MMS,
            ConnectivityManager.TYPE_MOBILE_SUPL,
            ConnectivityManager.TYPE_WIMAX -> true
            ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_BLUETOOTH,
            ConnectivityManager.TYPE_ETHERNET -> false
            else -> true
        }
    }
}

fun getDefaultCookieManager(): CookieManager {
    val cookieManager = CookieManager()
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    return cookieManager
}

fun Context.isNetworkAvailable(): Boolean {
    val manager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetworkInfo = manager.activeNetworkInfo
    var connected = activeNetworkInfo != null && activeNetworkInfo.isConnected
    if (!connected) {
        connected = manager.allNetworkInfo?.any { it.isConnected } ?: false
    }
    return connected
}

fun String.getRefererFromUrl(): String {
    return try {
        val uri = Uri.parse(this)
        "${uri.scheme}://${uri.authority ?: throw Exception("invalid refereer")}"
    } catch (e: Exception) {
        "https://google.com"
    }
}

fun HttpURLConnection.checkAndAddReferral(url: String) {
    if (this.getRequestProperty("Referer") == null) {
        val referer = url.getRefererFromUrl()
        this.addRequestProperty("Referer", referer)
    }
}

fun Map<String, List<String>>.getHeaderValue(
    vararg keys: String
): String? {
    for (key in keys) {
        val value = this[key]?.firstOrNull()
        if (!value.isNullOrBlank()) {
            return value
        }
    }
    return null
}

fun Download.getDownloadInfo(): DownloadInfo {
    return DownloadInfo.newInstance(this)
}

fun <T : HttpURLConnection> T.closeConnection() {
    try {
        this.disconnect()
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
}

fun Int.isResponseOk(): Boolean {
    return this in 200..299
}

fun Map<String, List<String>>.getContentLengthFromHeader(defaultValue: Long): Long {
    val contentRange = this.getHeaderValue(
        HEADER_CONTENT_RANGE,
        HEADER_CONTENT_RANGE_LEGACY,
        HEADER_CONTENT_RANGE_COMPAT
    )
    val lastIndexOf = contentRange?.lastIndexOf("/")
    var contentLength = -1L
    if (lastIndexOf != null && lastIndexOf != -1 && lastIndexOf < contentRange.length) {
        contentLength = contentRange.substring(lastIndexOf + 1).toLongOrNull() ?: -1L
    }
    if (contentLength == -1L) {
        contentLength = this.getHeaderValue(
            HEADER_CONTENT_LENGTH,
            HEADER_CONTENT_LENGTH_LEGACY,
            HEADER_CONTENT_LENGTH_COMPAT
        )?.toLongOrNull() ?: defaultValue
    }
    return contentLength
}

fun MutableMap<String, List<String>>.getContentHashMD5(): String {
    return this.getHeaderValue("Content-MD5") ?: ""
}

fun InputStream?.copyStreamToString(): String {
    return if (this == null) {
        ""
    } else {
        val stringBuilder = StringBuilder()
        BufferedReader(InputStreamReader(this)).also {
            var line: String? = it.readLine()
            while (line != null) {
                stringBuilder.append(line)
                    .append('\n')
                line = it.readLine()
            }
        }
        stringBuilder.toString()
    }
}

fun Map<String, List<String>>.acceptRanges(
    code: Int
): Boolean {
    val acceptRangeValue = this.getHeaderValue(
        HEADER_ACCEPT_RANGE,
        HEADER_ACCEPT_RANGE_LEGACY,
        HEADER_ACCEPT_RANGE_COMPAT
    )
    val transferValue = this.getHeaderValue(
        HEADER_TRANSFER_ENCODING,
        HEADER_TRANSFER_LEGACY,
        HEADER_TRANSFER_ENCODING_COMPAT
    )
    val contentLength = this.getContentLengthFromHeader(-1L)
    val acceptsRanges = code == HttpURLConnection.HTTP_PARTIAL || acceptRangeValue == "bytes"
    return (contentLength > -1L && acceptsRanges) || (contentLength > -1L && transferValue?.lowercase() != "chunked")
}

fun getETAString(context: Context, etaInMilliSeconds: Long, elapsed: Boolean = false): String? {
    if (etaInMilliSeconds < 0) {
        return ""
    }
    var seconds = (etaInMilliSeconds / 1000).toInt()
    val hours = (seconds / 3600).toLong()
    seconds -= (hours * 3600).toInt()
    val minutes = (seconds / 60).toLong()
    seconds -= (minutes * 60).toInt()
    return when {
        hours > 0 -> {
            context.getString(
                if (!elapsed) R.string.download_eta_hrs else R.string.download_elapsed_hrs,
                hours,
                minutes,
                seconds
            )
        }
        minutes > 0 -> {
            context.getString(
                if (!elapsed) R.string.download_eta_min else R.string.download_elapsed_min,
                minutes,
                seconds
            )
        }
        else -> {
            context.getString(
                if (!elapsed) R.string.download_eta_sec else R.string.download_elapsed_sec,
                seconds
            )
        }
    }
}

fun getDownloadSpeedString(
    context: Context,
    downloadedBytesPerSecond: Long
): String {
    if (downloadedBytesPerSecond < 0) {
        return ""
    }
    val kb = downloadedBytesPerSecond.toDouble() / 1000.toDouble()
    val mb = kb / 1000.toDouble()
    val decimalFormat = DecimalFormat(".##")
    return when {
        mb >= 1 -> {
            context.getString(R.string.download_speed_mb, decimalFormat.format(mb))
        }
        kb >= 1 -> {
            context.getString(R.string.download_speed_kb, decimalFormat.format(kb))
        }
        else -> {
            context.getString(R.string.download_speed_bytes, downloadedBytesPerSecond)
        }
    }
}

fun isMainThread(): Boolean {
    return Looper.myLooper() == Looper.getMainLooper()
}

internal fun initialised(): String {
    return """ 
            ${BuildConfig.LIBRARY_PACKAGE_NAME}-Initialized
        """
}

@Throws(IOException::class)
fun moveFileToAnotherDir(source: File, destination: File, deleteSource: Boolean) {
    val newFile = File(destination, source.getName())
    var outputChannel: FileChannel? = null
    var inputChannel: FileChannel? = null
    try {
        outputChannel = FileOutputStream(newFile).getChannel()
        inputChannel = FileInputStream(source).getChannel()
        inputChannel.transferTo(0, inputChannel.size(), outputChannel)
        inputChannel.close()
        if (deleteSource) {
            source.delete()
        }
    } finally {
        inputChannel?.close()
        outputChannel?.close()
    }
}

fun getGroupName(id: Long): String = "Group-$id"

//courtesy github
fun getSize(size: Long): String {
    var s = ""
    val kb: Double = size.toDouble() / KILO
    val mb: Double = kb / KILO
    val gb: Double = mb / KILO
    val tb: Double = gb / KILO
    if (size < KILO) {
        s = "$size Bytes"
    } else if (size in KILO until MEGA) {
        s = String.format("%.2f", kb) + " KB"
    } else if (size in MEGA until GIGA) {
        s = String.format("%.2f", mb) + " MB"
    } else if (size in GIGA until TERA) {
        s = String.format("%.2f", gb) + " GB"
    } else if (size >= TERA) {
        s = String.format("%.2f", tb) + " TB"
    }
    return s
}

