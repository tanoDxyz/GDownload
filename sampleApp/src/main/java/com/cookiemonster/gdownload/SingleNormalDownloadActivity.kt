package com.cookiemonster.gdownload

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import com.tanodxyz.gdownload.*
import com.tanodxyz.gdownload.connection.ConnectionManagerImpl
import com.tanodxyz.gdownload.connection.URLConnectionFactory
import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutorImpl
import com.tanodxyz.gdownload.io.DefaultFileStorageHelper
import com.tanodxyz.gdownload.worker.DataReadWriteWorkersManagerImpl


/**
 * see download object
 */
class SingleNormalDownloadActivity : AppCompatActivity() {

    private var autoStartDownload: Boolean = false
    private lateinit var statusTextView: TextView
    private lateinit var linkTextView: TextView

    private lateinit var numberOfDownloadRetriesEditText: EditText
    private lateinit var spinnerMaxNumberOfDownloadThreads: AppCompatSpinner
    private lateinit var spinnerNetworkType: AppCompatSpinner
    private lateinit var spinnerProgressUpdateTimeMilliSeconds: EditText
    private lateinit var filePathEdT: EditText

    private lateinit var cancelButton: Button
    private lateinit var downloadButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var restartButton: Button

    private lateinit var progressBar: ProgressBar
    private lateinit var downloadSpeedTv: TextView
    private lateinit var elapsedTimeTv: TextView
    private lateinit var remainingTimeTimeTv: TextView
    private lateinit var downloadedTotalTv: TextView
    private lateinit var progressTxtView: TextView


    private var download: Download? = null


    private lateinit var downloadManager: Downloader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_download)
        init()
    }

    private fun init() {
        linkTextView = findViewById(R.id.linkTv)
        statusTextView = findViewById(R.id.statusTv)

        numberOfDownloadRetriesEditText = findViewById(R.id.NumberOfRetriesEdT)
        spinnerMaxNumberOfDownloadThreads = findViewById(R.id.spinnerMaxNumThreads)
        spinnerNetworkType = findViewById(R.id.spinnerNetworkType)
        spinnerProgressUpdateTimeMilliSeconds = findViewById(R.id.ProgressUpdateTimeMilliSecondsEdT)
        filePathEdT = findViewById(R.id.FilePathEdT)

        cancelButton = findViewById(R.id.cancelButton)
        downloadButton = findViewById(R.id.downloadButton)
        pauseButton = findViewById(R.id.pauseButton)
        resumeButton = findViewById(R.id.resumeButton)
        restartButton = findViewById(R.id.restartButton)

        progressBar = findViewById(R.id.simpleProgressBar)
        downloadSpeedTv = findViewById(R.id.downloadSpeedTv)
        elapsedTimeTv = findViewById(R.id.elapsedTimeTv)
        remainingTimeTimeTv = findViewById(R.id.remainintTimeTv)
        downloadedTotalTv = findViewById(R.id.downloadedTotalTv)
        progressTxtView = findViewById(R.id.progressTv)
        // construct download object and populate ui according to download options .one time
        constructDownloadObject()
        constructDownloader()

        syncDownloadUiOptionsWithDownloadObject()


        //click listeners
        downloadButton.setOnClickListener {
            onDownloadButtonClicked()
        }

        cancelButton.setOnClickListener {
            downloadManager.stopDownload(null)
        }

        resumeButton.setOnClickListener {
            downloadManager.resumeDownload(null)
        }
        pauseButton.setOnClickListener {
            downloadManager.freezeDownload(null)
        }
        restartButton.setOnClickListener {
            downloadManager.restart(null)
        }

        if(autoStartDownload) {
            downloadButton.performClick()
        }
    }

    private fun constructDownloader() {

        // you can use this object to set root path for saving files
        val defaultFileStorageHelper = DefaultFileStorageHelper(this)
        // on android 10 or above we can't create random access file via scope storage procedure
        // so the following method either will use Download Directory or fall back to internal
        // app directory
        // if you are sure about some directory you can call  [defaultFileStorageHelper.setFilesRoot(File)]
        defaultFileStorageHelper.setFilesRootToDownloadsOrFallbackToInternalDirectory()
        val urlConnectionFactory = URLConnectionFactory()
        // passing lifecycle will cause progress callbacks to automatically detached when
        // component attached to this lifecycle goes out of scope or in other words'
        // lifecycle pause,stop,destroy methods are called
        val executor = ScheduledBackgroundExecutorImpl(
            corePoolSize = DEF_MAX_THREADS_PER_EXECUTOR
        )

        this.downloadManager =
            DownloadManager.Builder(this).setNetworkInfoProvider(NetworkInfoProvider(this))
                .setDataReadWriteWorkersManager(DataReadWriteWorkersManagerImpl())
                .setCallbacksHandler(runCallbacksOnMainThread = true)
                .setStorageHelper(defaultFileStorageHelper)
                .setConnectionManager(ConnectionManagerImpl(urlConnectionFactory, executor))
                .setScheduledBackgroundExecutor(executor)
                .build()

        // if connection is lost and download fails this will restart download once connectivity is resumed
        this.downloadManager.registerNetworkChangeListener()
    }


    private fun onDownloadButtonClicked() {
        // sync because user may have changed the download options views
        syncDownloadObjectWithDownloadUIOptions()
        updateUI(UiUpdate.START_DOWNLOAD)
        startDownload()
    }


    private fun constructDownloadObject() {
        val download = intent.getSerializableExtra(DOWNLOAD) as? Download?
        if (download != null) {
            this.download = download
            autoStartDownload = true
        } else {
            this.download = Download(
                url = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.second,
                id = System.nanoTime(),
                filePath = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.first
            )
        }
    }

    private fun startDownload() {
        this.download?.apply {
            downloadManager.download(this, DownloadListenerImpl())
        }
    }

    private fun updateUI(type: UiUpdate, payLoadExtra: Any? = null) {
        when (type) {
            UiUpdate.START_DOWNLOAD -> {
//                downloadButton.isEnabled = false
                statusTextView.text = "Downloading!"
            }
            UiUpdate.PROGRESS -> {
                (payLoadExtra as? DownloadInfo)?.apply {
                    updateProgressViewsOnly()
                }
            }
            UiUpdate.FAILED -> {
                (payLoadExtra as? Pair<DownloadInfo, String>)?.apply {
                    statusTextView.text = this.second
                    this.first.updateProgressViewsOnly()
                }
//                downloadButton.isEnabled = true
            }

            UiUpdate.STOP -> {
                (payLoadExtra as? Pair<Boolean, String>)?.apply {
                    if (this.first) {
                        statusTextView.text = "Download Stopped"
                    } else {
                        statusTextView.text = this.second
                    }
                }
            }

            UiUpdate.PAUSE -> {
                (payLoadExtra as? Pair<Boolean, String>)?.apply {
                    if (this.first) {
                        statusTextView.text = "Download Paused"
                    } else {
                        statusTextView.text = this.second
                    }
                }
            }

            UiUpdate.RESTART -> {
                (payLoadExtra as? Pair<Boolean, String>)?.apply {
                    if (this.first) {
                        statusTextView.text = "Download Restarted"
                    } else {
                        statusTextView.text = this.second
                    }
                }
            }

            UiUpdate.RESUME -> {
                (payLoadExtra as? Pair<Boolean, String>)?.apply {
                    if (this.first) {
                        statusTextView.text = "Download resumed"
                    } else {
                        statusTextView.text = this.second
                    }
                }
            }

            UiUpdate.SUCCESS -> {
                (payLoadExtra as? DownloadInfo)?.apply {
                    statusTextView.text = "Download Success!"
                    updateProgressViewsOnly()
                }
//                downloadButton.isEnabled = true
            }
        }
    }

    private fun DownloadInfo.updateProgressViewsOnly() {
        val context = this@SingleNormalDownloadActivity
        val downloadProgressInDeterminate = this.progress.toInt() < 0
        val progress = if (downloadProgressInDeterminate) 100 else this.progress.toInt()
        progressBar.progress = progress
        downloadSpeedTv.text = this.getDownloadSpeedStr(context)
        elapsedTimeTv.text = this.getElapsedTimeStr(context)
        remainingTimeTimeTv.text = this.getTimeRemainingStr(context)
        downloadedTotalTv.text = this.getDownloadedTotalStr()
        progressTxtView.text = "$progress %"
    }

    private fun syncDownloadUiOptionsWithDownloadObject() {
        download?.apply {
            numberOfDownloadRetriesEditText.setText("${getConnectionRetryCount()}")
            spinnerMaxNumberOfDownloadThreads.setSelection(this.getMaxNumberOfConnections() - 1)
            spinnerNetworkType.setSelection(this.getNetworkType())
            spinnerProgressUpdateTimeMilliSeconds.setText("${this.getProgressUpdateTimeMilliSeconds()}")
            filePathEdT.setText(this.getFilePath())
            linkTextView.setText(getUrl())
        }
    }

    private fun syncDownloadObjectWithDownloadUIOptions() {
        download?.apply {
            set(
                connectionRetryCount = numberOfDownloadRetriesEditText.text.toString().toInt(),
                maxNumberOfConnections = (spinnerMaxNumberOfDownloadThreads.selectedItemPosition + 1),
                networkType = spinnerNetworkType.selectedItemPosition,
                progressUpdateTimeMilliSec = spinnerProgressUpdateTimeMilliSeconds.text.toString()
                    .toLong(),
                filePath = filePathEdT.text.toString()
            )
        }
    }

    /**
     * There is no need to override all the methods.
     * override what is necessary to you.
     */
    inner class DownloadListenerImpl : DownloadProgressListener {

        // this is where initial connection to server is established
        // probably download starting method
        override fun onConnectionEstablished(downloadInfo: DownloadInfo?) {
            super.onConnectionEstablished(downloadInfo)
            updateUI(UiUpdate.START_DOWNLOAD)
        }

        override fun onDownloadProgress(downloadInfo: DownloadInfo?) {
            super.onDownloadProgress(downloadInfo)
            updateUI(UiUpdate.PROGRESS, downloadInfo)
        }

        override fun onDownloadFailed(downloadInfo: DownloadInfo, ex: String) {
            super.onDownloadFailed(downloadInfo, ex)
            updateUI(UiUpdate.FAILED, Pair(downloadInfo, ex))
        }

        override fun onStop(downloadInfo: DownloadInfo, stopped: Boolean, reason: String) {
            super.onStop(downloadInfo, stopped, reason)
            updateUI(UiUpdate.STOP, Pair(stopped, reason))
        }

        override fun onPause(downloadInfo: DownloadInfo, paused: Boolean, reason: String) {
            super.onPause(downloadInfo, paused, reason)
            updateUI(UiUpdate.PAUSE, Pair(paused, reason))
        }

        override fun onRestart(downloadInfo: DownloadInfo, restarted: Boolean, reason: String) {
            super.onRestart(downloadInfo, restarted, reason)
            updateUI(UiUpdate.RESTART, Pair(restarted, reason))
        }

        override fun onResume(downloadInfo: DownloadInfo, resume: Boolean, reason: String) {
            super.onResume(downloadInfo, resume, reason)
            updateUI(UiUpdate.RESUME, Pair(resume, reason))
        }

        override fun onDownloadSuccess(downloadInfo: DownloadInfo) {
            super.onDownloadSuccess(downloadInfo)
            updateUI(UiUpdate.SUCCESS, downloadInfo)
            showFileDownloadedDialog(downloadInfo.filePath)
        }

        override fun onConnection(downloadInfo: DownloadInfo, slice: Slice?) {
            super.onConnection(downloadInfo, slice)
        }

        override fun onDownloadIsMultiConnection(
            downloadInfo: DownloadInfo,
            multiConnection: Boolean
        ) {
            super.onDownloadIsMultiConnection(downloadInfo, multiConnection)
        }
    }

    enum class UiUpdate {
        START_DOWNLOAD, FAILED, PROGRESS, STOP, RESTART, PAUSE, RESUME, SUCCESS
    }

    companion object {
        const val DOWNLOAD = "download"

        fun launch(activity: AppCompatActivity, download: Download) {
            Intent(activity, SingleNormalDownloadActivity::class.java).apply {
                putExtra(DOWNLOAD, download)
                activity.startActivity(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadManager.shutDown(null)
    }
}