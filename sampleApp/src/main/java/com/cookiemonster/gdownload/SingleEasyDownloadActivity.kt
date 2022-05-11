package com.cookiemonster.gdownload

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.tanodxyz.gdownload.*

class SingleEasyDownloadActivity : AppCompatActivity() {

    private var downloader: Downloader? = null
    private lateinit var statusTextView: TextView
    private lateinit var linkTextView: TextView
    private lateinit var downloadButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadSpeedTv: TextView
    private lateinit var elapsedTimeTv: TextView
    private lateinit var remainingTimeTimeTv: TextView
    private lateinit var downloadedTotalTv: TextView
    private lateinit var progressTv: TextView

    private var wifiOnly = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_easy_download)
        init()
    }

    private fun init() {
        linkTextView = findViewById(R.id.linkTv)
        statusTextView = findViewById(R.id.statusTv)

        downloadButton = findViewById(R.id.downloadButton)


        progressBar = findViewById(R.id.simpleProgressBar)
        downloadSpeedTv = findViewById(R.id.downloadSpeedTv)
        elapsedTimeTv = findViewById(R.id.elapsedTimeTv)
        remainingTimeTimeTv = findViewById(R.id.remainintTimeTv)
        downloadedTotalTv = findViewById(R.id.downloadedTotalTv)
        progressTv = findViewById(R.id.progressTv)

        findViewById<SwitchCompat>(R.id.wifiOnlySwitch).setOnCheckedChangeListener { buttonView, isChecked ->
            wifiOnly = isChecked
        }
        linkTextView.setText(DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.second)
        // *********************************************************************

        downloadButton.setOnClickListener {
            /**
             * we will hide the download button here because with each invocation it will create
             * a new downloader instance so multiple downloaders will be updating single group of views
             * it may not be necessary in your case.
             */
            downloadButton.isEnabled = false
            onDownloadButtonClicked()
        }

    }


    private fun onDownloadButtonClicked() {
        val downloadUrl = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.second
        val fileName = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.first
        // this download progress listener  callback is restricted to activity's lifecycle
        // will automatically stop receiving updates once activity goes out of scope
        GDownload.singleDownload(this) {
            maxNumberOfConnections = 32 // number of connections/ threads
            url = downloadUrl
            name = fileName
            networkType =
                if (wifiOnly) NetworkType.valueOf(NetworkType.WIFI_ONLY) else NetworkType.valueOf(
                    NetworkType.ALL
                )
            downloadProgressListener =
                DownloadProgressListenerImpl()
            //optional to get downloader object for further processing.
            this@SingleEasyDownloadActivity.downloader = getDownloader()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloader?.shutDown(null)
    }
    private fun DownloadInfo.updateProgressViewsOnly() {
        val context = this@SingleEasyDownloadActivity
        progressBar.progress = getNormalizedProgress().toInt()
        downloadSpeedTv.text = this.getDownloadSpeedStr(context)
        elapsedTimeTv.text = this.getElapsedTimeStr(context)
        remainingTimeTimeTv.text = this.getTimeRemainingStr(context)
        downloadedTotalTv.text = this.getDownloadedTotalStr()
        progressTv.text = getNormalizedProgressStr()
    }

    inner class DownloadProgressListenerImpl : DownloadProgressListener {
        override fun onConnectionEstablished(downloadInfo: DownloadInfo?) {
            statusTextView.text = "Downloading!"
        }

        override fun onDownloadProgress(downloadInfo: DownloadInfo?) {
            downloadInfo?.updateProgressViewsOnly()
        }

        override fun onDownloadFailed(downloadInfo: DownloadInfo, ex: String) {
            statusTextView.text = ex
            downloadButton.isEnabled = true
        }

        override fun onDownloadSuccess(downloadInfo: DownloadInfo) {
            statusTextView.text = "Download success!"
            showFileDownloadedDialog(downloadInfo.filePath)
            downloadButton.isEnabled = true
        }
    }
}