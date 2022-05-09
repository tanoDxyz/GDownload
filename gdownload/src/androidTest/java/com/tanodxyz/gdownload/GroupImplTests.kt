package com.tanodxyz.gdownload

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanodxyz.gdownload.GDownload.createGroupSettings
import com.tanodxyz.gdownload.GDownload.freeGroup
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupImplTests {
    private lateinit var appContext: Context
    private lateinit var downloadGroupsManager: Group

    @Before
    fun init() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertEquals("com.tanodxyz.jdownload.test", appContext.packageName)
        downloadGroupsManager = freeGroup(appContext, createGroupSettings(23,"DownloadsTestGroup"))

    }

    @After
    fun cleanUp() {
        downloadGroupsManager.shutDown()
    }

}