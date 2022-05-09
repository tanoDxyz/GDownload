package com.tanodxyz.gdownload.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tanodxyz.gdownload.*
import com.tanodxyz.gdownload.Download.Companion.DOWNLOADING
import com.tanodxyz.gdownload.Download.Companion.FAILED
import com.tanodxyz.gdownload.Download.Companion.PAUSED
import com.tanodxyz.gdownload.Download.Companion.STOPPED
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream


class SQLiteManager private constructor(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null,
    DATABASE_VERSION
), DownloadDatabaseManager {
    private var open = true
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL(
            "DROP TABLE IF EXISTS "
                    + DOWNLOADS_TABLE_NAME
        )
        onCreate(db)
    }


    @Synchronized
    override fun insertDownload(Download: Download) {
        writableDatabase.use {
            writableDatabase.insert(
                DOWNLOADS_TABLE_NAME,
                null,
                Download.prepareForDatabaseWrite()
            )
        }
    }


    @Synchronized
    override fun insertOrUpdateDownload(Download: Download) {
        writableDatabase.use {
            val rowsAffected = it.update(
                DOWNLOADS_TABLE_NAME,
                Download.prepareForDatabaseWrite(),
                "$FILE_PATH = ?",
                arrayOf(Download.getFilePath())
            )
            if (rowsAffected == 0) {
                it.insert(DOWNLOADS_TABLE_NAME, null, Download.prepareForDatabaseWrite())
            }
        }
    }

    @Synchronized
    override fun deleteDownloadByFilePath(filePath: String): Int {
        var deletedRows: Int
        writableDatabase.use {
            deletedRows = it.delete(DOWNLOADS_TABLE_NAME, "$FILE_PATH = ?", arrayOf(filePath))
        }
        return deletedRows
    }


    @Synchronized
    override fun deleteAllDownloads(): Int {
        var deletedRows: Int
        writableDatabase.use {
            deletedRows = it.delete(DOWNLOADS_TABLE_NAME, null, null)
        }
        return deletedRows
    }

    override fun deleteDownloads(status: String): Int {
        var deletedRows: Int
        writableDatabase.use {
            deletedRows = it.delete(DOWNLOADS_TABLE_NAME, "$STATUS = ?", arrayOf(status))
        }
        return deletedRows
    }


    @Synchronized
    override fun findAllGroupDownloads(groupId: Long): MutableList<Download> {
        val mutableList: MutableList<Download> = mutableListOf()
        var download: Download? = null
        readableDatabase.use {
            val columns = arrayOf(
                URL,
                FILE_PATH,
                STATUS,
                NETWORK_TYPE,
                SLICE_DATA,
                CONTENT_LENGTH,
                ID,
                QUEUE_ID,
                CONNECTION_RETRY_COUNT,
                MAX_NUMBER_CONNECTIONS,
                DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND,
                DOWNLOAD_ID,
                CONTENT_LENGTH_DOWNLOADED
            )
            val selectionArgs = arrayOf("$groupId")
            val cursor = readableDatabase.query(
                DOWNLOADS_TABLE_NAME,
                columns,
                "$QUEUE_ID = ?",
                selectionArgs,
                null,
                null,
                null
            )
            cursor?.apply {
                while (this.moveToNext()) {
                    val url = cursor.getString(0)
                    val fp = cursor.getString(1)
                    val state = cursor.getString(2)
                    val networkType = cursor.getInt(3)
                    val sliceBlob = cursor.getBlob(4)
                    val objectInputStream =
                        ObjectInputStream(ByteArrayInputStream(sliceBlob))
                    val sliceData = objectInputStream.readObject() as List<Slice>
                    val contentLength = cursor.getString(5).toLong()
                    val id = cursor.getString(6).toLong()
                    val connRetryCount = cursor.getInt(8)
                    val queueID = cursor.getLong(7)
                    val maxNumberConnections = cursor.getInt(9)
                    val progressUpdateTimeMillisSecs = cursor.getLong(10)
                    val downloadId = cursor.getString(11)
                    val contentLengthDownloaded = cursor.getString(12).toLong()
                    download = Download(
                        downloadId.toLong(),
                        url,
                        fp,
                        contentLength,
                        contentLengthDownloaded,
                        state,
                        queueID,
                        networkType,
                        connRetryCount,
                        maxNumberConnections,
                        progressUpdateTimeMillisSecs,
                        sliceData
                    )
                    mutableList.add(download!!)
                    closeResource(objectInputStream)
                }
                closeResource(this)
            }
        }

        return mutableList
    }

    @Synchronized
    override fun findAllInCompleteDownloads(): MutableList<Download> {
        val mutableList: MutableList<Download> = mutableListOf()
        var download: Download? = null
        readableDatabase.use {
            val columns = arrayOf(
                URL,
                FILE_PATH,
                STATUS,
                NETWORK_TYPE,
                SLICE_DATA,
                CONTENT_LENGTH,
                ID,
                QUEUE_ID,
                CONNECTION_RETRY_COUNT,
                MAX_NUMBER_CONNECTIONS,
                DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND,
                DOWNLOAD_ID,
                CONTENT_LENGTH_DOWNLOADED
            )
            val selectionArgs = arrayOf(FAILED, DOWNLOADING, PAUSED, STOPPED)
            val cursor = readableDatabase.query(
                DOWNLOADS_TABLE_NAME,
                columns,
                "$STATUS = ? OR $STATUS = ? OR $STATUS = ? OR $STATUS = ?",
                selectionArgs,
                null,
                null,
                null
            )
            cursor?.apply {
                while (this.moveToNext()) {
                    val url = cursor.getString(0)
                    val fp = cursor.getString(1)
                    val state = cursor.getString(2)
                    val networkType = cursor.getInt(3)
                    val sliceBlob = cursor.getBlob(4)
                    val objectInputStream =
                        ObjectInputStream(ByteArrayInputStream(sliceBlob))
                    val sliceData = objectInputStream.readObject() as List<Slice>
                    val contentLength = cursor.getString(5).toLong()
                    val id = cursor.getString(6).toLong()
                    val connRetryCount = cursor.getInt(8)
                    val queueID = cursor.getLong(7)
                    val maxNumberConnections = cursor.getInt(9)
                    val progressUpdateTimeMillisSecs = cursor.getLong(10)
                    val downloadId = cursor.getString(11)
                    val contentLengthDownload = cursor.getString(12).toLong()
                    download = Download(
                        downloadId.toLong(),
                        url,
                        fp,
                        contentLength,
                        contentLengthDownload,
                        state,
                        queueID,
                        networkType,
                        connRetryCount,
                        maxNumberConnections,
                        progressUpdateTimeMillisSecs,
                        sliceData
                    )
                    mutableList.add(download!!)
                    closeResource(objectInputStream)
                }
                closeResource(this)
            }
        }

        return mutableList
    }


    @Synchronized
    override fun findDownloadByFilePath(filePath: String): Download? {
        var download: Download? = null
        readableDatabase.use {
            val columns = arrayOf(
                URL,
                FILE_PATH,
                STATUS,
                NETWORK_TYPE,
                SLICE_DATA,
                CONTENT_LENGTH,
                ID,
                QUEUE_ID,
                CONNECTION_RETRY_COUNT,
                MAX_NUMBER_CONNECTIONS,
                DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND,
                DOWNLOAD_ID,
                CONTENT_LENGTH_DOWNLOADED
            )
            val selectionArgs = arrayOf(filePath)
            val cursor = readableDatabase.query(
                DOWNLOADS_TABLE_NAME,
                columns,
                "filePath=?",
                selectionArgs,
                null,
                null,
                null
            )
            cursor?.apply {
                if (this.moveToFirst()) {
                    val url = cursor.getString(0)
                    val fp = cursor.getString(1)
                    val state = cursor.getString(2)
                    val networkType = cursor.getInt(3)
                    val sliceBlob = cursor.getBlob(4)
                    val objectInputStream =
                        ObjectInputStream(ByteArrayInputStream(sliceBlob))
                    val sliceData = objectInputStream.readObject() as List<Slice>
                    val contentLength = cursor.getLong(5)
                    val id = cursor.getString(6).toLong()
                    val connRetryCount = cursor.getInt(8)
                    val queueID = cursor.getLong(7)
                    val maxNumberConnections = cursor.getInt(9)
                    val progressUpdateTimeMillisSecs = cursor.getLong(10)
                    val downloadId = cursor.getString(11)
                    val contentLengthDownloaded = cursor.getString(12).toLong()
                    download = Download(
                        downloadId.toLong(),
                        url,
                        fp,
                        contentLength,
                        contentLengthDownloaded,
                        state,
                        queueID,
                        networkType,
                        connRetryCount,
                        maxNumberConnections,
                        progressUpdateTimeMillisSecs,
                        sliceData
                    )
                    closeResource(objectInputStream)
                }
                closeResource(this)
            }
        }

        return download
    }

    override fun findDownloadByDownloadId(id: Int): Download? {
        var download: Download? = null
        readableDatabase.use {
            val columns = arrayOf(
                URL,
                FILE_PATH,
                STATUS,
                NETWORK_TYPE,
                SLICE_DATA,
                CONTENT_LENGTH,
                ID,
                QUEUE_ID,
                CONNECTION_RETRY_COUNT,
                MAX_NUMBER_CONNECTIONS,
                DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND,
                DOWNLOAD_ID,
                CONTENT_LENGTH_DOWNLOADED
            )
            val selectionArgs = arrayOf("$id")
            val cursor = readableDatabase.query(
                DOWNLOADS_TABLE_NAME,
                columns,
                "$DOWNLOAD_ID = ?",
                selectionArgs,
                null,
                null,
                null
            )
            cursor?.apply {
                if (this.moveToFirst()) {
                    val url = cursor.getString(0)
                    val fp = cursor.getString(1)
                    val state = cursor.getString(2)
                    val networkType = cursor.getInt(3)
                    val sliceBlob = cursor.getBlob(4)
                    val objectInputStream =
                        ObjectInputStream(ByteArrayInputStream(sliceBlob))
                    val sliceData = objectInputStream.readObject() as List<Slice>
                    val contentLength = cursor.getLong(5)
                    val id = cursor.getString(6).toLong()
                    val connRetryCount = cursor.getInt(8)
                    val queueID = cursor.getLong(7)
                    val maxNumberConnections = cursor.getInt(9)
                    val progressUpdateTimeMillisSecs = cursor.getLong(10)
                    val downloadId = cursor.getString(11)
                    val contentLengthDownloaded = cursor.getString(12).toLong()
                    download = Download(
                        downloadId.toLong(),
                        url,
                        fp,
                        contentLength,
                        contentLengthDownloaded,
                        state,
                        queueID,
                        networkType,
                        connRetryCount,
                        maxNumberConnections,
                        progressUpdateTimeMillisSecs,
                        sliceData
                    )
                    closeResource(objectInputStream)
                }
                closeResource(this)
            }
        }

        return download
    }

    override fun getAll(): MutableList<Download> {
        val mutableList: MutableList<Download> = mutableListOf()
        var download: Download? = null
        readableDatabase.use {
            val columns = arrayOf(
                URL,
                FILE_PATH,
                STATUS,
                NETWORK_TYPE,
                SLICE_DATA,
                CONTENT_LENGTH,
                ID,
                QUEUE_ID,
                CONNECTION_RETRY_COUNT,
                MAX_NUMBER_CONNECTIONS,
                DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND,
                DOWNLOAD_ID,
                CONTENT_LENGTH_DOWNLOADED
            )
            val cursor = readableDatabase.query(
                DOWNLOADS_TABLE_NAME,
                columns,
                null,
                null,
                null,
                null,
                null
            )
            cursor?.apply {
                while (this.moveToNext()) {
                    val url = cursor.getString(0)
                    val fp = cursor.getString(1)
                    val state = cursor.getString(2)
                    val networkType = cursor.getInt(3)
                    val sliceBlob = cursor.getBlob(4)
                    val objectInputStream =
                        ObjectInputStream(ByteArrayInputStream(sliceBlob))
                    val sliceData = objectInputStream.readObject() as List<Slice>
                    val contentLength = cursor.getString(5).toLong()
                    val id = cursor.getString(6).toLong()
                    val connRetryCount = cursor.getInt(8)
                    val queueID = cursor.getLong(7)
                    val maxNumberConnections = cursor.getInt(9)
                    val progressUpdateTimeMillisSecs = cursor.getLong(10)
                    val downloadId = cursor.getString(11)
                    val contentLengthDownloaded = cursor.getString(12).toLong()
                    download = Download(
                        downloadId.toLong(),
                        url,
                        fp,
                        contentLength,
                        contentLengthDownloaded,
                        state,
                        queueID,
                        networkType,
                        connRetryCount,
                        maxNumberConnections,
                        progressUpdateTimeMillisSecs,
                        sliceData
                    )
                    mutableList.add(download!!)
                    closeResource(objectInputStream)
                }
                closeResource(this)
            }
        }

        return mutableList
    }

    override fun release() {
        close()
    }


    override fun close() {
        super.close()
        open = false
    }

    override fun isOpen(): Boolean = open

    companion object {
        const val DOWNLOADS_TABLE_NAME = "downloads"
        const val TAG = "sqliteManager"

        private val CREATE_TABLE = ("create table " + DOWNLOADS_TABLE_NAME + "(" + ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT, " + URL + " TEXT NOT NULL, " + FILE_PATH + " TEXT NOT NULL, "
                + STATUS + " TEXT NOT NULL, " + NETWORK_TYPE + " INTEGER, " + SLICE_DATA + " BLOB, " + QUEUE_ID +
                " INTEGER, " + CONNECTION_RETRY_COUNT + " INTEGER, " + MAX_NUMBER_CONNECTIONS + " INTEGER, "
                + DOWNLOAD_PROGRESS_UPDATE_TIME_MILLISECOND + " INTEGER, " + DOWNLOAD_ID + " TEXT, "
                + CONTENT_LENGTH + " INTEGER , " + CONTENT_LENGTH_DOWNLOADED + " INTEGER );")

        const val DATABASE_NAME = "gdb"
        const val DATABASE_VERSION = 1

        private var databaseInstance: SQLiteManager? = null

        @Synchronized
        fun getInstance(context: Context): SQLiteManager {
            if (databaseInstance == null || databaseInstance!!.isOpen().not()) {
                databaseInstance = SQLiteManager(context)
            }
            return databaseInstance!!
        }
    }
}