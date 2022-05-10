package com.cookiemonster.gdownload

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tanodxyz.gdownload.GDownload
import com.tanodxyz.gdownload.isAndroid10Plus

class MainActivity : AppCompatActivity() {

    private var runnableOnButtonClick:Runnable? = null

    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                runnableOnButtonClick?.run()
                runnableOnButtonClick = null
            } else {
                alertDialog(getString(R.string.alert),getString(R.string.storagePermissionDeniedCantWriteFiles),getString(R.string.ok))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        init()
    }

    private fun init() {
        GDownload.init(this.lifecycle)

        findViewById<View>(R.id.singleDownloadLifeCycleSurviveButton).setOnClickListener {
            Runnable {
                launchActivity<SingleDownloadLifecycleSurvivalActivity>()
            }.askWritePermissionIfRequired()
        }

        findViewById<View>(R.id.singleDownloadBasicButton).setOnClickListener {
            Runnable {
                launchActivity<SingleEasyDownloadActivity>()
            }.askWritePermissionIfRequired()
        }

        findViewById<View>(R.id.singleDownloadDetailedButton).setOnClickListener {
            Runnable {
                launchActivity<SingleNormalDownloadActivity>()
            }.askWritePermissionIfRequired()
        }

        findViewById<View>(R.id.groupDownloads).setOnClickListener {
            Runnable {
                launchActivity<GroupDownloadsActivity>()
            }.askWritePermissionIfRequired()
        }

        findViewById<View>(R.id.loadDownloadsFromDatabase).setOnClickListener {
            Runnable { launchActivity<LoadDownloadsFromDatabaseActivity>() }.askWritePermissionIfRequired()
        }

        findViewById<View>(R.id.deleteAllDownloads).setOnClickListener {
            Runnable {
                GDownload.deleteAllDownloads(this) {
                    Toast.makeText(
                        this,
                        "${getString(R.string.downloadsDeleted)} = $it",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.askWritePermissionIfRequired()
        }
    }

    private fun Runnable.askWritePermissionIfRequired() {
        if (!isAndroid10Plus()) {
            when {
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    this.run()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    alertDialog(
                        getString(R.string.alert),
                        getString(R.string.weNeedStoragePermissionToDownloadFiles),
                        getString(R.string.continuee), getString(R.string.cancel),
                    ) { continueButtonClicked ->
                        if (!continueButtonClicked) {
                            // do nothing
                        }else {
                            runnableOnButtonClick = this
                            requestPermissionLauncher.launch(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        }
                    }
                }
                else -> {
                    runnableOnButtonClick = this
                    requestPermissionLauncher.launch(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
            }
        }else {
            this.run()
        }
    }
}