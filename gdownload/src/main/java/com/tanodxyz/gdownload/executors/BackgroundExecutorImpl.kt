package com.tanodxyz.gdownload.executors

import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

open class BackgroundExecutorImpl : BackgroundExecutor {
    val TAG = "BackgroundExecutor-${System.nanoTime()}"
    private var executorService = Executors.newCachedThreadPool()
    override fun execute(runnable: Runnable): BackgroundExecutor.Cancelable {
        val future = executorService.submit(runnable)
        return object : BackgroundExecutor.Cancelable {
            override fun cancel() {
                future.cancel(true)
            }
        }
    }

    override fun shutDown() {
        executorService.shutdownNow()
    }

    override fun cleanUp() {
        val threadPoolExecutor = executorService as ThreadPoolExecutor
        threadPoolExecutor.purge()
    }

    override fun isTerminated(): Boolean {
        return executorService?.isShutdown?:true
    }
}