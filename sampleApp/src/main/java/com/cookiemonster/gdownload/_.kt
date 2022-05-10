package com.cookiemonster.gdownload

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity


inline fun <reified T : AppCompatActivity> AppCompatActivity.launchActivity() {
    Intent(this, T::class.java).apply {
        startActivity(this)
    }
}

fun Context.alertDialog(
    title: String,
    message: String,
    positiveButtonTxt: String,
    callback: (() -> Unit)? = null
) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message) // Specifying a listener allows you to take an action before dismissing the dialog.
        // The dialog is automatically dismissed when a dialog button is clicked.
        .setPositiveButton(
            positiveButtonTxt
        ) { dialog, which ->
            dialog.dismiss()
            callback?.invoke()
        }
        .setIcon(android.R.drawable.ic_dialog_alert)
        .show()
}

fun Context.alertDialog(
    title: String,
    message: String,
    positiveButtonTxt: String,
    negativeButtonTxt: String,
    callback: ((Boolean) -> Unit)? = null
) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message) // Specifying a listener allows you to take an action before dismissing the dialog.
        // The dialog is automatically dismissed when a dialog button is clicked.
        .setPositiveButton(
            positiveButtonTxt
        ) { dialog, which ->
            dialog.dismiss()
            callback?.invoke(true)
        }
        .setNegativeButton(negativeButtonTxt) { dialog, which ->
            dialog.dismiss()
            callback?.invoke(false)
        }
        .setIcon(android.R.drawable.ic_dialog_alert)
        .show()
}

fun Context.showFileDownloadedDialog(filePath: String) {
    this.alertDialog(
        getString(R.string.fileDownloaded),
        "File Path: $filePath",
        getString(R.string.ok),
    )
}


val DEFAULT_DOWNLOAD_68_MB_NAME_LINK_PAIR = Pair(
    "sateliteImage.jpg",
    "https://effigis.com/wp-content/themes/effigis_2014/img/GeoEye_GeoEye1_50cm_8bit_RGB_DRA_Mining_2009FEB14_8bits_sub_r_15.jpg"
)
val DEFAULT_DOWNLOAD_15_MB_NAME_LINK_PAIR = Pair(
    "sateliteImage_bankok.jpg",
    "https://effigis.com/wp-content/uploads/2015/02/DigitalGlobe_WorldView1_50cm_8bit_BW_DRA_Bangkok_Thailand_2009JAN06_8bits_sub_r_1.jpg"
)


val DEFAULT_DOWNLOAD_15MB_NAME_LINK_PAIR =
    Pair(
        "Toronto_Ontario_Canada.jpg",
        "https://effigis.com/wp-content/uploads/2015/02/Infoterra_Terrasar-X_1_75_m_Radar_2007DEC15_Toronto_EEC-RE_8bits_sub_r_12.jpg"
    )


val DEFAULT_DOWNLOAD_TEST_VIDEO_BUNNY_NAME_LINK_PAIR3 =
    Pair(
        "test_video_bunny.mp4",
        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_5MB.mp4"
    )