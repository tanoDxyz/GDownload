package com.cookiemonster.gdownload


import android.os.Bundle
import android.util.LongSparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.set
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tanodxyz.gdownload.*

class GroupDownloadsActivity : AppCompatActivity(), DownloadProgressListener {
    private lateinit var groupProcessorRecyclerViewAdapter: GroupProcessorRecyclerViewAdapter
    private lateinit var downloadsRecyclerView: RecyclerView
    private var groupDownloader: Group? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_downloads)
        init()
    }

    private fun init() {
        downloadsRecyclerView = findViewById(R.id.downloadsRecyclerView)
        downloadsRecyclerView.layoutManager = LinearLayoutManager(this)
        groupProcessorRecyclerViewAdapter =
            GroupProcessorRecyclerViewAdapter(OnGroupDownloadButtonsClickListener())
        downloadsRecyclerView.adapter = groupProcessorRecyclerViewAdapter


        GDownload.init(this.lifecycle)
        createGroupDownloader { groupDownloader ->
            this.groupDownloader = groupDownloader
            val downloadsList = createDownloadList()
            scheduleDownloads(groupDownloader, downloadsList) { addedDownloadsIds ->
                groupProcessorRecyclerViewAdapter.setDownloadIds(addedDownloadsIds)
            }
        }
    }

    private fun createGroupDownloader(callback: (Group) -> Unit) {
        // you can also create group object via its Builder methods
        // here for sake of simplicity we are using this easy way
        GDownload.freeGroup(this) {
            concurrentDownloadsRunningCapacity = 1
            groupLoopTimeMilliSecs = 1_000
            maxConnectionPerDownload = 32
            progressCallbacksOnMainThread = true
            val group = getGroup()
            group?.let(callback)
        }
    }


    private fun scheduleDownloads(
        group: Group?,
        downloads: List<Pair<Download, DownloadProgressListener>>,
        addedDownloadsIdsCallback: (List<Long>) -> Unit
    ) {

        group?.apply {
            start()   // start group processor looper thread
            addGroupProgressListener(GroupProgressListenerImpl())
            addAllWithListeners(downloads) { addedDownloadIdsList ->
                //[addedDownloadIdsList] you will be using these ids to access and modify group downloads
                group.startDownloads(addedDownloadIdsList) // until this is called all downloads will be queued
                addedDownloadsIdsCallback(addedDownloadIdsList)
            }
        }
    }


    private fun createDownloadList(): List<Pair<Download, DownloadProgressListener>> {

        val downloadsList =
            mutableListOf<Pair<Download, DownloadProgressListener>>()
        val sateLiteImageDownload = Download(
            filePath = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.first,
            url = DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR.second
        )
        val sateLiteImageBankokDownload = Download(
            filePath = DEFAULT_DOWNLOAD_15_MB_NAME_LINK_PAIR.first,
            url = DEFAULT_DOWNLOAD_15_MB_NAME_LINK_PAIR.second
        )


        val sateliteImageOntario = Download(
            filePath = DEFAULT_DOWNLOAD_15MB_NAME_LINK_PAIR.first,
            url = DEFAULT_DOWNLOAD_15MB_NAME_LINK_PAIR.second,
            maxNumberOfConnections = 8 // // small file so
        )


        val mp4VideoDownload = Download(
            filePath = DEFAULT_DOWNLOAD_TEST_VIDEO_BUNNY_NAME_LINK_PAIR3.first,
            url = DEFAULT_DOWNLOAD_TEST_VIDEO_BUNNY_NAME_LINK_PAIR3.second,
            maxNumberOfConnections = 5
        )

        // if you don't like to use groupListener
        // each download can have it's own listener
        // and both group and download listener will be called on if added
        val downloadListener = this@GroupDownloadsActivity
        downloadsList.addAll(
            arrayListOf(
                Pair(sateLiteImageBankokDownload, downloadListener),
                Pair(sateliteImageOntario, downloadListener),
                Pair(mp4VideoDownload, downloadListener),
                Pair(sateLiteImageDownload, downloadListener)
            )
        )
        return downloadsList
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    class GroupProcessorRecyclerViewAdapter(private val listener: GroupDownloadButtonClickListener) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var downloads = LongSparseArray<DownloadInfo?>(10)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return DownloadViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_download_list_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val download = downloads.valueAt(position)
            val downloadViewHolder = holder as DownloadViewHolder
            if (download == null) {
                downloadViewHolder.reset()
            } else {
                downloadViewHolder.bind(download)
            }
        }

        override fun getItemCount(): Int {
            return downloads.size()
        }

        fun setDownloadIds(ids: List<Long>) {
            ids.forEach { downloadId ->
                downloads[downloadId] = null
            }
            notifyDataSetChanged()
        }

        @UiThread
        fun updateItem(downloadInfo: DownloadInfo) {
            downloads[downloadInfo.id] = downloadInfo
            notifyItemChanged(downloads.indexOfKey(downloadInfo.id))
        }

        fun getItemPosition(download: DownloadInfo): Int {
            return downloads.indexOfKey(download.id)
        }

        private inner class DownloadViewHolder(private val layoutRootView: View) :
            RecyclerView.ViewHolder(layoutRootView) {
            fun reset() {
                downloadNameTv.text = ("")
                progressTv.text = ""
                progressBar.progress = 0
                downloadTotalTv.text = "0B / 0B"
                downloadSpeedTv.text = "0B/sec"
                elapsedTimeTv.text = "0s Elapsed"
                remainingTimeTv.text = "0s remaining"
                downloadStatusTv.text = "IDLE"
                pauseButton.setOnClickListener { }
                resumeButton.setOnClickListener { }
                restartButton.setOnClickListener { }
                cancelButton.setOnClickListener { }
            }

            fun bind(download: DownloadInfo) {
                download.apply {
                    downloadNameTv.text = getFileName()
                    progressTv.text = getNormalizedProgressStr()
                    progressBar.progress = getNormalizedProgress().toInt()
                    downloadTotalTv.text = getDownloadedTotalStr()
                    downloadSpeedTv.text = getDownloadSpeedStr(layoutRootView.context)
                    elapsedTimeTv.text = getElapsedTimeStr(layoutRootView.context)
                    remainingTimeTv.text = getTimeRemainingStr(layoutRootView.context)
                    downloadStatusTv.text = status
                    pauseButton.setOnClickListener {
                        listener.onPauseClicked(this.id)
                    }
                    resumeButton.setOnClickListener {
                        listener.onResumeClicked(this.id)
                    }
                    restartButton.setOnClickListener {
                        listener.onRestartClicked(this.id)
                    }
                    cancelButton.setOnClickListener {
                        listener.onCancelClicked(this.id)
                    }
                    layoutRootView.setOnClickListener {
                        listener.onDownloadClicked(download)
                    }
                }
            }

            private val downloadNameTv: TextView = layoutRootView.findViewById(R.id.downloadNameTv)
            private val progressTv: TextView = layoutRootView.findViewById(R.id.progressTv)
            private val progressBar: ProgressBar =
                layoutRootView.findViewById(R.id.simpleProgressBar)
            private val downloadTotalTv: TextView =
                layoutRootView.findViewById(R.id.downloadedTotalTv)
            private val downloadSpeedTv: TextView =
                layoutRootView.findViewById(R.id.downloadSpeedTv)
            private val elapsedTimeTv: TextView = layoutRootView.findViewById(R.id.elapsedTimeTv)
            private val remainingTimeTv: TextView =
                layoutRootView.findViewById(R.id.remainingTimeTv)
            private val downloadStatusTv: TextView =
                layoutRootView.findViewById(R.id.downloadStatusTv)
            private val pauseButton: Button = layoutRootView.findViewById(R.id.pauseButton)
            private val resumeButton: Button = layoutRootView.findViewById(R.id.resumeButton)
            private val restartButton: Button = layoutRootView.findViewById(R.id.restartButton)
            private val cancelButton: Button = layoutRootView.findViewById(R.id.cancelButton)

        }
    }

    private inner class GroupProgressListenerImpl : GroupListener {
        val TAG = "groupListenerImpl"

        override fun onEnqueued(groupId: Long, download: DownloadInfo, groupState: GroupState) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
        }

        override fun onAdded(groupId: Long, download: DownloadInfo, groupState: GroupState) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
        }

        override fun onStarting(groupId: Long, download: DownloadInfo, groupState: GroupState) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
            downloadsRecyclerView.smoothScrollToPosition(
                groupProcessorRecyclerViewAdapter.getItemPosition(
                    download
                )
            )
        }

        override fun onDownloading(groupId: Long, download: DownloadInfo, groupState: GroupState) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
        }

        override fun onSuccess(groupId: Long, download: DownloadInfo, groupState: GroupState) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
        }

        override fun onFailure(
            groupId: Long,
            errorMessage: String?,
            download: DownloadInfo,
            groupState: GroupState
        ) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
        }

        override fun onPaused(groupId: Long, download: DownloadInfo, groupState: GroupState) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
        }

        override fun onStopped(groupId: Long, download: DownloadInfo, groupState: GroupState) {
            groupProcessorRecyclerViewAdapter.updateItem(download)
        }

    }

    private inner class OnGroupDownloadButtonsClickListener : GroupDownloadButtonClickListener {
        override fun onCancelClicked(downloadId: Long) {
            groupDownloader?.stopDownload(downloadId)
        }

        override fun onPauseClicked(downloadId: Long) {
            groupDownloader?.freezeDownload(downloadId)
        }

        override fun onRestartClicked(downloadId: Long) {
            groupDownloader?.restartDownload(downloadId)
        }

        override fun onResumeClicked(downloadId: Long) {
            groupDownloader?.resumeDownload(downloadId)
        }

        override fun onDownloadClicked(download: DownloadInfo) {
            if (download.progress >= 100.0 || download.status == Download.DOWNLOADED) {
                showFileDownloadedDialog(download.filePath)
            } else {
                Toast.makeText(
                    this@GroupDownloadsActivity,
                    R.string.downloadNotComplete,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}