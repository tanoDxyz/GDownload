package com.tanodxyz.gdownload.executors

import android.os.Handler
import android.os.HandlerThread

class HandlerThreadBackgroundExecutor(name:String): BackgroundExecutor {
    private val handlerThread: HandlerThread = HandlerThread(name)
    private var executor: Handler

    init {
        handlerThread.start()
        val looper = handlerThread.looper
        executor = Handler(looper)
    }

    override fun execute(runnable: Runnable): BackgroundExecutor.Cancelable {
        executor.post(runnable)
        return object : BackgroundExecutor.Cancelable {
            override fun cancel() {
                executor.removeCallbacks(runnable)
            }
        }
    }

    override fun shutDown() {
        executor.removeCallbacksAndMessages(null)
        handlerThread.quit()
    }

    override fun cleanUp() {
        executor.removeCallbacksAndMessages(null)
    }
}