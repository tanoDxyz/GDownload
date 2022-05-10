package com.tanodxyz.gdownload

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.tanodxyz.gdownload.executors.BackgroundExecutor
import java.util.*

/**
 *The [CallbacksHandler] is used to run the callbacks based on flag [callbackThreadMain].
 *It will run the callbacks on main thread or background thread.
 *
 * if [Lifecycle] object is provided - [CallbacksHandler] will behave in the following manner.
 *
 * if current state of [Lifecycle] object is [Lifecycle.State.RESUMED]
 * -all callbacks provided via [runOnSelectedThread] will be immediately executed.
 *  else if callbacks are provided but current state was other then [Lifecycle.State.RESUMED]
 *  then all callbacks will accumulate in the list un till [Lifecycle] = [Lifecycle.State.RESUMED]
 *
 */
open class CallbacksHandler(
    internal val callbackThreadMain: Boolean = false,
    internal val executor: BackgroundExecutor?,
    lifecycle: Lifecycle? = null
) {
    protected val mainThreadHandler = Handler(Looper.getMainLooper())
    private val internalHandler = InternalHandler(lifecycle)

    fun Runnable.runOnSelectedThread() {
        internalHandler.handleCallback(this)
    }

    open fun clean() {
        executor?.shutDown()
        mainThreadHandler.removeCallbacksAndMessages(null)
        internalHandler.destroy()
    }

    private inner class InternalHandler(val lifecycle: Lifecycle?) : DefaultLifecycleObserver {
        private val callbacksList = Collections.synchronizedList(mutableListOf<Runnable>())

        init {
            lifecycle?.addObserver(this)
        }

        override fun onResume(owner: LifecycleOwner) {
            super.onResume(owner)
            Runnable {
                callbacksList.forEach { callback ->
                    callback.run()
                }
                callbacksList.clear()
            }.runOn()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            destroy()
        }

        fun destroy() {
            callbacksList.clear()
        }

        fun handleCallback(runnable: Runnable) {
            if (lifecycle == null) {
                runnable.runOn()
            } else {
                if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                    runnable.runOn()
                } else if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    callbacksList.add(runnable)
                }
            }
        }

        private fun Runnable.runOn() {
            if (callbackThreadMain) mainThreadHandler.post(this)
            else if (!isMainThread()) this.run() else executor?.execute(this)
        }
    }
}