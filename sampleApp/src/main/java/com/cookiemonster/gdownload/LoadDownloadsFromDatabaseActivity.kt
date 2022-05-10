package com.cookiemonster.gdownload

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cookiemonster.gdownload.SingleNormalDownloadActivity.Companion.launch
import com.tanodxyz.gdownload.Download
import com.tanodxyz.gdownload.GDownload
import com.tanodxyz.gdownload.getDownloadInfo

class LoadDownloadsFromDatabaseActivity : AppCompatActivity() {
    private lateinit var databaseDownloadsAdapter: DatabaseDownloadsAdapter
    private lateinit var downloadsListView: ListView
    private lateinit var addDownloadDummyButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_load_downloads_from_database)
        init()
    }

    private fun init() {
        downloadsListView = findViewById(R.id.downloadsDatabaseListView)
        addDownloadDummyButton = findViewById(R.id.addDummyDownloadButton)
        databaseDownloadsAdapter = DatabaseDownloadsAdapter(::onDownloadClickedInListView)
        addDownloadDummyButton.setOnClickListener(this::onAddDownloadToDownloadListClicked)
        downloadsListView.adapter = databaseDownloadsAdapter

        fetchFreshMeatFromStore()

    }

    private fun fetchFreshMeatFromStore() {
        GDownload.loadAllDownloadsFromDatabase(this) { downloadsFromDatabase ->
            databaseDownloadsAdapter.addDownloads(downloadsFromDatabase)
        }
    }

    private fun onDownloadClickedInListView(download: Download) {
        alertDialog(
            getString(R.string.start_Download),
            "${
                download.getDownloadInfo().getFileName()
            } \n ${getString(R.string.downloadWillBeStarted)}",
            getString(R.string.ok)
        ) {
            launch(this,download)
        }
    }

    private fun onAddDownloadToDownloadListClicked(view: View) {
        createDownload().apply {
            databaseDownloadsAdapter.addDownload(this)
        }
    }

    private fun createDownload(): Download {
        val downloadName = "bunnyVideo-${System.nanoTime()}.mp4"
        val downloadUrl = DEFAULT_DOWNLOAD_TEST_VIDEO_BUNNY_NAME_LINK_PAIR3.second
        return Download(url = downloadUrl, filePath = downloadName)
    }

}


class DatabaseDownloadsAdapter(private val onDownloadItemClick: (Download) -> Unit) :
    BaseAdapter() {
    private val downloads = mutableListOf<Download>()
    override fun getCount(): Int {
        return downloads.count()
    }

    override fun getItem(position: Int): Any {
        return downloads[position]
    }

    override fun getItemId(position: Int): Long {
        return downloads[position].id
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val downloadView: View? = convertView
            ?: LayoutInflater.from(parent!!.context)
                .inflate(R.layout.download_from_database, parent, false)
        val download = downloads[position].getDownloadInfo()
        downloadView?.apply {
            findViewById<TextView>(R.id.downloadNameTv).text = download.getFileName()
            findViewById<TextView>(R.id.downloadFilePathTv).text = download.filePath
            findViewById<TextView>(R.id.downloadStatusTv).text = download.status
            findViewById<TextView>(R.id.downloadedTotalTv).text =
                download.getDownloadedTotalStr()
            findViewById<TextView>(R.id.progressTv).text = download.getNormalizedProgressStr()
            downloadView.setOnClickListener {
                onDownloadItemClick(downloads[position])
            }
        }
        return downloadView!!
    }

    fun addDownloads(downloads: List<Download>) {
        this.downloads.clear()
        this.downloads.addAll(downloads)
        notifyDataSetChanged()
    }

    fun addDownload(download: Download) {
        this.downloads.add(download)
        this.notifyDataSetChanged()
    }
}