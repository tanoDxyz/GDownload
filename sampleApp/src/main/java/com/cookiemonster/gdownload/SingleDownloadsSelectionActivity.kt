package com.cookiemonster.gdownload

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SingleDownloadsSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_downloads_selection)
        init()
    }

    private fun init() {
        findViewById<View>(R.id.easyPeasyButton).setOnClickListener {
            launchActivity<SingleEasyDownloadActivity>()
        }
        findViewById<View>(R.id.detailedButton).setOnClickListener {
            launchActivity<SingleNormalDownloadActivity>()
        }
    }
}