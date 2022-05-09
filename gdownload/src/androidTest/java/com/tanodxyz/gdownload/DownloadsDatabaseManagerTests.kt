package com.tanodxyz.gdownload

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanodxyz.gdownload.DownloaderTests.Companion.getDummyDownload
import com.tanodxyz.gdownload.database.SQLiteManager
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class DownloadsDatabaseManagerTests {

    private lateinit var appContext: Context
    private lateinit var downloadDatabaseManager: SQLiteManager

    @Before
    fun init() {
        // Context of the app under test.
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertEquals("com.tanodxyz.jdownload.test", appContext.packageName)
        downloadDatabaseManager = SQLiteManager.getInstance(appContext)
    }

    @After
    fun cleanUp() {
        downloadDatabaseManager.deleteAllDownloads()
        downloadDatabaseManager.close()
    }

    @Test
    fun insertSingle() {
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        Assert.assertEquals(1, downloadDatabaseManager.getAll().count())
    }

    @Test
    fun addOrUpdateDownload() {
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
        val dummyDownload = getDummyDownload()
        downloadDatabaseManager.insertDownload(dummyDownload)
        Assert.assertEquals(1, downloadDatabaseManager.getAll().count())
        val dummyDownload1 = getDummyDownload()
        downloadDatabaseManager.insertOrUpdateDownload(dummyDownload1)
        Assert.assertEquals(2, downloadDatabaseManager.getAll().count())
        dummyDownload.set(connectionRetryCount = 230)
        downloadDatabaseManager.insertOrUpdateDownload(dummyDownload)
        Assert.assertEquals(2, downloadDatabaseManager.getAll().count())
        val dummyDownloadCopyFromDatabase =
            downloadDatabaseManager.findDownloadByFilePath(dummyDownload.getFilePath())
        Assert.assertEquals(
            dummyDownload.getConnectionRetryCount(),
            dummyDownloadCopyFromDatabase!!.getConnectionRetryCount()
        )
    }


    @Test
    fun deleteDownloadByFilePath() {
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
        val dummyDownload = getDummyDownload()
        downloadDatabaseManager.insertDownload(dummyDownload)
        Assert.assertEquals(1, downloadDatabaseManager.getAll().count())
        downloadDatabaseManager.deleteDownloadByFilePath(dummyDownload.getFilePath())
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())

    }


    @Test
    fun deleteAllDownloads() {
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        downloadDatabaseManager.insertDownload(getDummyDownload())
        Assert.assertEquals(7, downloadDatabaseManager.getAll().count())
        downloadDatabaseManager.deleteAllDownloads()
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
    }

    @Test
    fun deleteDownloadByStatus() {
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
        val dummyDownload = getDummyDownload()
        dummyDownload.set(status = Download.DOWNLOADED)

        val dummyDownload1 = getDummyDownload()
        dummyDownload1.set(status = Download.ENQUEUED)

        val dummyDownload2 = getDummyDownload()
        dummyDownload2.set(status = Download.FAILED)

        val dummyDownload3 = getDummyDownload()
        dummyDownload3.set(status = Download.PAUSED)

        val dummyDownload4 = getDummyDownload()
        dummyDownload4.set(status = Download.PAUSED)

        downloadDatabaseManager.insertDownload(dummyDownload)
        downloadDatabaseManager.insertDownload(dummyDownload1)
        downloadDatabaseManager.insertDownload(dummyDownload2)
        downloadDatabaseManager.insertDownload(dummyDownload3)
        downloadDatabaseManager.insertDownload(dummyDownload4)
        Assert.assertEquals(5, downloadDatabaseManager.getAll().count())

        downloadDatabaseManager.deleteDownloads(Download.PAUSED)

        Assert.assertEquals(3, downloadDatabaseManager.getAll().count())
    }

    @Test
    fun findAllGroupDownloads() {
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
        val groupId1 = 1L
        val groupId2 = 2L
        val dummyDownload = getDummyDownload()
        dummyDownload.set(status = Download.DOWNLOADED, queueId = groupId1)

        val dummyDownload1 = getDummyDownload()
        dummyDownload1.set(status = Download.ENQUEUED, queueId = groupId1)

        val dummyDownload2 = getDummyDownload()
        dummyDownload2.set(status = Download.FAILED, queueId = groupId1)

        val dummyDownload3 = getDummyDownload()
        dummyDownload3.set(status = Download.PAUSED, queueId = groupId2)

        val dummyDownload4 = getDummyDownload()
        dummyDownload4.set(status = Download.PAUSED, queueId = groupId2)

        downloadDatabaseManager.insertDownload(dummyDownload)
        downloadDatabaseManager.insertDownload(dummyDownload1)
        downloadDatabaseManager.insertDownload(dummyDownload2)
        downloadDatabaseManager.insertDownload(dummyDownload3)
        downloadDatabaseManager.insertDownload(dummyDownload4)
        Assert.assertEquals(5, downloadDatabaseManager.getAll().count())

        val groupDownloadsByGroupId1 = downloadDatabaseManager.findAllGroupDownloads(groupId1)
        Assert.assertEquals(3, groupDownloadsByGroupId1.count())
        val groupDownloadsByGroupId2 = downloadDatabaseManager.findAllGroupDownloads(groupId2)
        Assert.assertEquals(2, groupDownloadsByGroupId2.count())

    }

    @Test
    fun findAllInCompleteDownloads() {
        Assert.assertEquals(0, downloadDatabaseManager.getAll().count())
        val dummyDownload = getDummyDownload()
        dummyDownload.set(status = Download.DOWNLOADED)

        val dummyDownload1 = getDummyDownload()
        dummyDownload1.set(status = Download.ENQUEUED) // enqueued download is not started

        val dummyDownload2 = getDummyDownload()
        dummyDownload2.set(status = Download.FAILED)

        val dummyDownload3 = getDummyDownload()
        dummyDownload3.set(status = Download.PAUSED)

        val dummyDownload4 = getDummyDownload()
        dummyDownload4.set(status = Download.PAUSED)

        val dummyDownload5 = getDummyDownload()
        dummyDownload5.set(status = Download.STOPPED)

        downloadDatabaseManager.insertDownload(dummyDownload)
        downloadDatabaseManager.insertDownload(dummyDownload1)
        downloadDatabaseManager.insertDownload(dummyDownload2)
        downloadDatabaseManager.insertDownload(dummyDownload3)
        downloadDatabaseManager.insertDownload(dummyDownload4)
        downloadDatabaseManager.insertDownload(dummyDownload5)
        Assert.assertEquals(4, downloadDatabaseManager.findAllInCompleteDownloads().count())

    }


}