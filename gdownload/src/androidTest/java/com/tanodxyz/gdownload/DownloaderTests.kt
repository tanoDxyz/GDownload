package com.tanodxyz.gdownload

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanodxyz.gdownload.connection.ConnectionManagerImpl
import com.tanodxyz.gdownload.connection.URLConnectionFactory
import com.tanodxyz.gdownload.database.SQLiteManager
import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutorImpl
import com.tanodxyz.gdownload.io.DefaultFileStorageHelper
import com.tanodxyz.gdownload.worker.DataReadWriteWorkersManagerImpl

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom


@RunWith(AndroidJUnit4::class)
class DownloaderTests {

    private lateinit var appContext: Context
    private lateinit var downloader: DownloadManager

    @Before
    fun init() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertEquals("com.tanodxyz.jdownload.test", appContext.packageName)
        val builder = DownloadManager.Builder(appContext)
        val scheduledBackgroundExecutor = ScheduledBackgroundExecutorImpl(
            DEF_MAX_THREADS_PER_EXECUTOR
        )
        val downloadCallbacksHandler = DownloadCallbacksHandler(false)
        val storageHelper = DefaultFileStorageHelper(appContext)
        val connectionManager =
            ConnectionManagerImpl(URLConnectionFactory(), scheduledBackgroundExecutor)
        val downloadDatabaseManager = SQLiteManager.getInstance(appContext)
        val networkInfoProvider = NetworkInfoProvider(appContext)
        val dataReadWriteWorkerManager = DataReadWriteWorkersManagerImpl()
        downloader = builder.setScheduledBackgroundExecutor(scheduledBackgroundExecutor)
            .setDownloadDatabaseManager(downloadDatabaseManager)
            .setConnectionManager(connectionManager).setStorageHelper(storageHelper)
            .setCallbacksHandler(downloadCallbacksHandler)
            .setDataReadWriteWorkersManager(dataReadWriteWorkerManager)
            .setNetworkInfoProvider(networkInfoProvider).build()
    }

    @After
    fun cleanUp() {
        downloader.shutDown(null)
    }


    @Test
    fun startDownload() {
        val download = getDownload()
        downloader.download(download, object : DownloadProgressListener {
            override fun onDownloadSuccess(downloadInfo: DownloadInfo) {
                super.onDownloadSuccess(downloadInfo)
            }
        })
        Thread.currentThread().join(10_000)
        val downloadSuccess = downloader.isSuccess
        val downloadStarted = downloader.isDownloadStarted
        Assert.assertEquals(
            "Download status ${downloader.getState()}",
            true,
            (downloadStarted || downloadSuccess)
        )
        if (downloadSuccess) {
            downloader.deleteFile(true)
        }
    }

    @Test
    fun cancel_started_download() {
        val download = getDownload(largeFile = true)
        downloader.download(download, object : DownloadProgressListener {})
        Thread.currentThread().join(10_000)
        downloader.stopDownload()
        Thread.currentThread().join(10_000)
        val downloadCanceled = downloader.isStopped
        Assert.assertEquals(
            "Download status ${downloader.getState()}",
            true,
            (downloadCanceled)
        )
        if (downloadCanceled) {
            downloader.deleteFile(true)
        }
    }


    @Test
    fun restart_canceled_download() {
        val download = getDownload(largeFile = true)
        downloader.download(download, object : DownloadProgressListener {})
        Thread.currentThread().join(10_000)
        downloader.stopDownload()
        Thread.currentThread().join(10_000)
        val downloadCanceled = downloader.isStopped
        Assert.assertEquals(
            "Download status ${downloader.getState()}",
            true,
            (downloadCanceled)
        )
        downloader.restart()
        Thread.currentThread().join(10_000)
        Assert.assertEquals(
            "Download status ${downloader.getState()}",
            true,
            (downloader.isBusy || downloader.isSuccess)
        )
        downloader.stopDownload()
    }
    companion object {
        val VIDEO_10_MB_LINK = Pair(
            "tst-video-10mb.mp4",
            "https://file-examples.com/wp-content/uploads/2017/04/file_example_MP4_1280_10MG.mp4"
        )

        val DATA_1_GB_LINK = Pair("large-file.bin", "https://speed.hetzner.de/1GB.bin")

        val VIDEO_18_MB_LINK = Pair(
            "tst-video-18mb.mp4",
            "https://file-examples.com/wp-content/uploads/2017/04/file_example_MP4_1920_18MG.mp4"
        )
        private val secureRandom = SecureRandom()
        fun getDummyUrl(): String = "https://www.testDummyUrl.com/${System.nanoTime()}"
        fun getDummyFilePath(): String =
            "sdcard/folder/anotherFolder/fileName-${System.nanoTime()}.bin"

        fun getDummySliceData(): List<Slice> =
            mutableListOf(Slice(0, 0, secureRandom.nextLong(), 0, false))

        fun getRandomNetworkType(): Int = secureRandom.nextInt(3)
        fun getMaxNumberOfConnections(bound: Int): Int = secureRandom.nextInt(bound) + 1
        fun getRandomProgressUpdateTimeMilliSeconds(strategy: Double): Long {
            return if (strategy < 1) {
                secureRandom.nextInt(1000).toLong() + 100
            } else if (strategy >= 1 && strategy < 2) {
                secureRandom.nextInt(2000).toLong() + 100
            } else if (strategy >= 2 && strategy < 3) {
                secureRandom.nextInt(3000).toLong() + 100
            } else if (strategy >= 3 && strategy < 4) {
                secureRandom.nextInt(4000).toLong() + 100
            } else if (strategy >= 4 && strategy < 5) {
                secureRandom.nextInt(5000).toLong() + 100
            } else {
                2000
            }
        }

        fun getDummyDownload(): Download {
            return Download(
                System.nanoTime(),
                url = getDummyUrl(),
                filePath = getDummyFilePath(),
                networkType = getRandomNetworkType(),
                connectionRetryCount = 3,
                maxNumberOfConnections = getMaxNumberOfConnections(
                    secureRandom.nextInt(32)
                ),
                progressUpdateTimeMilliSec = getRandomProgressUpdateTimeMilliSeconds(1.0),
                sliceData = getDummySliceData()
            )
        }

        fun getDownload(largeFile: Boolean = false): Download {
            val (nameFile,url) = if(largeFile) {
                DATA_1_GB_LINK
            }else {
                VIDEO_10_MB_LINK
            }
            return Download(
                System.nanoTime(),
                url = url,
                filePath = nameFile,
                networkType = getRandomNetworkType(),
                connectionRetryCount = 3,
                maxNumberOfConnections = getMaxNumberOfConnections(
                    secureRandom.nextInt(30)
                ),
                progressUpdateTimeMilliSec = getRandomProgressUpdateTimeMilliSeconds(1.0)
            )
        }
    }
}