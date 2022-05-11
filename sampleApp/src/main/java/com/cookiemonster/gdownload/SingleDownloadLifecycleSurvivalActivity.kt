package com.cookiemonster.gdownload

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.tanodxyz.gdownload.*


class SingleDownloadLifecycleSurvivalActivity : AppCompatActivity() {
    private var downloadProgressListener: DownloadProgressListenerImpl? = null
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
        setContentView(R.layout.activity_single_download_lifecycle_survival)
        init()
    }

    private fun init() {

        findViewById<View>(R.id.rotateTheDeviceTv).blinkInfinite()

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


        // this download progress listener  callback is not restricted to activity's lifecycle
        // will receive updates if it is not removed and if activity is destroyed

        downloadProgressListener =
            DownloadProgressListenerImpl()
        // add view references for updates
        downloadProgressListener?.init(
            statusTextView,
            linkTextView,
            downloadButton,
            progressBar,
            downloadSpeedTv,
            elapsedTimeTv,
            remainingTimeTimeTv,
            downloadedTotalTv,
            progressTv
        )


        downloadButton.setOnClickListener {
            /**
             * we will hide the download button here because with each invocation it will create
             * a new downloader instance so multiple downloaders will be updating single group of views
             */
            downloadButton.isEnabled = false
            onDownloadButtonClicked()
        }

    }


    private fun onDownloadButtonClicked() {

        val downloadUrl = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.second
        val fileName = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.first

        GDownload.singleDownload(this.applicationContext) {
            url = downloadUrl
            name = fileName
            networkType =
                if (wifiOnly) NetworkType.valueOf(NetworkType.WIFI_ONLY) else NetworkType.valueOf(
                    NetworkType.ALL
                )
            this.downloadProgressListener =
                this@SingleDownloadLifecycleSurvivalActivity.downloadProgressListener
            // Store a reference.
            downloader = getDownloader()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // remove the attached download listener
        downloader?.removeListener(null)
    }

    override fun onResume() {
        super.onResume()
        this.downloadProgressListener?.let {
            downloader?.apply {
                addListener(it)
            }
        }
    }

    companion object {
        var downloader: Downloader? = null
    }


    class DownloadProgressListenerImpl : DownloadProgressListener {
        private var statusTextView: TextView? = null
        private var linkTextView: TextView? = null
        private var downloadButton: Button? = null
        private var progressBar: ProgressBar? = null
        private var downloadSpeedTv: TextView? = null
        private var elapsedTimeTv: TextView? = null
        private var remainingTimeTimeTv: TextView? = null
        private var downloadedTotalTv: TextView? = null
        private var progressTv: TextView? = null
        fun init(
            statusTextView: TextView,
            linkTextView: TextView,
            downloadButton: Button,
            progressBar: ProgressBar,
            downloadSpeedTv: TextView,
            elapsedTimeTv: TextView,
            remainingTimeTimeTv: TextView,
            downloadedTotalTv: TextView,
            progressTv: TextView
        ) {
            this.statusTextView = statusTextView
            this.linkTextView = linkTextView
            this.progressBar = progressBar
            this.downloadButton = downloadButton
            this.downloadSpeedTv = downloadSpeedTv
            this.elapsedTimeTv = elapsedTimeTv
            this.remainingTimeTimeTv = remainingTimeTimeTv
            this.downloadedTotalTv = downloadedTotalTv
            this.progressTv = progressTv
        }

        override fun onConnectionEstablished(downloadInfo: DownloadInfo?) {
            statusTextView?.text = "Downloading!"
        }

        override fun onDownloadProgress(downloadInfo: DownloadInfo?) {
            statusTextView?.context?.let { downloadInfo?.updateProgressViewsOnly(it) }
        }

        override fun onDownloadFailed(downloadInfo: DownloadInfo, ex: String) {
            statusTextView?.text = ex
            downloadButton?.isEnabled = true
        }

        override fun onDownloadSuccess(downloadInfo: DownloadInfo) {
            statusTextView?.text = "Download success!"
            statusTextView?.context?.showFileDownloadedDialog(downloadInfo.filePath)
            downloadButton?.isEnabled = true
        }

        private fun DownloadInfo.updateProgressViewsOnly(context: Context) {
            progressBar?.progress = getNormalizedProgress().toInt()
            downloadSpeedTv?.text = this.getDownloadSpeedStr(context)
            elapsedTimeTv?.text = this.getElapsedTimeStr(context)
            remainingTimeTimeTv?.text = this.getTimeRemainingStr(context)
            downloadedTotalTv?.text = this.getDownloadedTotalStr()
            progressTv?.text = getNormalizedProgressStr()
        }
    }

    fun View.blinkInfinite() {
        val anim: Animation = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 400
        anim.startOffset = 20
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        this.startAnimation(anim)
    }
}