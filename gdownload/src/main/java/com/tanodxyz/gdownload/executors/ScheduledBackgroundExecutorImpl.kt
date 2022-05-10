package com.tanodxyz.gdownload.executors

import androidx.lifecycle.Lifecycle
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ScheduledBackgroundExecutorImpl(corePoolSize: Int = -1, lifecycle: Lifecycle? = null) :
    ScheduledBackgroundExecutor {
    private var executorService: ScheduledExecutorService? = null
    private var callbackExecutor: CallbacksScheduler? = null

    init {
        setExecutor(corePoolSize)
        setCallbackScheduler(lifecycle)
    }

    override fun activeWorkersCount(): Int {
        val scheduledThreadPoolExecutor = executorService as ScheduledThreadPoolExecutor
        return scheduledThreadPoolExecutor.activeCount
    }

    override fun isExecutorAssigned(): Boolean {
        return executorService != null
    }

    override fun setExecutor(corePoolSize: Int) {
        if (corePoolSize < 1) {
            return
        }
        executorService?.apply {
            shutdownNow()
        }
        executorService = Executors.newScheduledThreadPool(corePoolSize)
        callbackExecutor?.setExecutor(executorService as ScheduledThreadPoolExecutor)
    }

    override fun setCallbackScheduler(lifecycle: Lifecycle?) {
        callbackExecutor = CallbacksScheduler(lifecycle).apply {
            executorService?.let {
                this.setExecutor(it as ScheduledThreadPoolExecutor)
            }
        }
    }

    override fun executeAtFixRateAfter(
        runnable: Runnable,
        interval: Long,
        timeUnit: TimeUnit
    ): ScheduledBackgroundExecutor.CallbackState {
        val schedule = callbackExecutor?.schedule({
            runnable.run()
        }, interval, timeUnit)
        return object : ScheduledBackgroundExecutor.CallbackState {
            override fun getId(): Int {
                return schedule!!.uniqueId
            }

            override fun cancel() {
                callbackExecutor?.stopAndRemove(getId())
            }

            override fun restart() {
                callbackExecutor?.restart(getId())
            }

            override fun pause() {
                callbackExecutor?.pause(getId())
            }

            override fun resume() {
                callbackExecutor?.resume(getId())
            }

            override fun stop() {
                callbackExecutor?.stop(getId())
            }

            override fun getState(): ScheduledBackgroundExecutor.CallbackState.State {
                return when (schedule!!.currentState) {
                    CallbacksScheduler.CallbackState.STARTED -> ScheduledBackgroundExecutor.CallbackState.State.STARTED
                    CallbacksScheduler.CallbackState.STOPPED -> ScheduledBackgroundExecutor.CallbackState.State.STOPPED
                    CallbacksScheduler.CallbackState.PAUSED -> ScheduledBackgroundExecutor.CallbackState.State.PAUSED
                    else -> ScheduledBackgroundExecutor.CallbackState.State.IDLE
                }
            }
        }
    }

    override fun execute(
        runnable: Runnable,
        timeOutMilliSecs: Long
    ): BackgroundExecutor.Cancelable {
        val cancelable = execute(runnable)
        executorService?.schedule({
            cancelable.cancel()
        }, timeOutMilliSecs, TimeUnit.MILLISECONDS)
        return cancelable
    }

    override fun execute(runnable: Runnable): BackgroundExecutor.Cancelable {
        val submit = executorService?.submit(runnable)
        return object : BackgroundExecutor.Cancelable {
            override fun cancel() {
                submit?.cancel(true)
            }
        }
    }

    override fun shutDown() {
        executorService?.shutdownNow()
    }

    override fun cleanUp() {
        val scheduledThreadPoolExecutor = executorService as ScheduledThreadPoolExecutor
        scheduledThreadPoolExecutor.purge()
    }

    override fun toString(): String {
        return executorService.toString()
    }
}