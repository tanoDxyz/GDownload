package com.tanodxyz.gdownload.executors

import androidx.lifecycle.Lifecycle
import com.tanodxyz.gdownload.executors.BackgroundExecutor
import java.util.concurrent.TimeUnit

interface ScheduledBackgroundExecutor: BackgroundExecutor {
    fun activeWorkersCount():Int
    fun isExecutorAssigned():Boolean
    fun setExecutor(corePoolSize:Int)
    fun setCallbackScheduler(lifecycle:Lifecycle?)
    fun executeAtFixRateAfter(runnable: Runnable,interval:Long,timeUnit: TimeUnit): CallbackState
    fun execute(runnable: Runnable,timeOutMilliSecs:Long): BackgroundExecutor.Cancelable
    interface CallbackState {
        fun getId(): Int
        fun cancel()
        fun restart()
        fun pause()
        fun resume()
        fun stop()
        fun getState():State
        enum class State {
            IDLE,STARTED, PAUSED, STOPPED
        }
    }

}