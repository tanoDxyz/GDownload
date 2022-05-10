package com.tanodxyz.gdownload.executors

/**
 * An abstraction of standard executor
 */
interface BackgroundExecutor {
    fun execute(runnable: Runnable): Cancelable
    fun shutDown()
    fun cleanUp()
    fun isTerminated(): Boolean
    interface Cancelable {
        fun cancel()
    }
}