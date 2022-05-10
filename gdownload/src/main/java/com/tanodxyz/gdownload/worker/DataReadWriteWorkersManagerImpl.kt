package com.tanodxyz.gdownload.worker

 import com.tanodxyz.gdownload.BiConsumer
 import com.tanodxyz.gdownload.connection.ConnectionManager
 import com.tanodxyz.gdownload.io.OutputResourceWrapper
 import java.util.concurrent.atomic.AtomicBoolean
 import java.util.concurrent.atomic.AtomicReference

class DataReadWriteWorkersManagerImpl : DataReadWriteWorkersManager {
    private var outputIsRandomAccess: Boolean = false
    private var outputResourceWrapper: OutputResourceWrapper? = null
    private val dataDownloadWorkers = mutableListOf<DataReadWriteWorker>()
    private var released = AtomicBoolean(false)

    override fun init(
        outputIsRandomAccess: Boolean,
        outputResourceWrapper: OutputResourceWrapper
    ) {
        this.outputIsRandomAccess = outputIsRandomAccess
        this.outputResourceWrapper = outputResourceWrapper
        released.set(false)
    }

    override fun release() {
        synchronized(dataDownloadWorkers) {
            dataDownloadWorkers.forEach { worker ->
                worker.stop()
            }
            dataDownloadWorkers.clear()
        }
        outputIsRandomAccess = false
        outputResourceWrapper = null
        released.set(true)
    }

    override fun addWorker(
        connectionData: ConnectionManager.ConnectionData
    ): Exception? {
        if(released.get()) {
            return Exception("Worker manager shutdown!")
        }
        val worker =
            DataReadWriteWorkerImpl(
                connectionData,
                outputResourceWrapper!!,
                outputIsRandomAccess
            )
        synchronized(dataDownloadWorkers) {
            dataDownloadWorkers.add(worker)
        }

        worker.init()
        return worker.doJob()
    }

    override fun stopAllWorkers(callback: BiConsumer<Boolean, String>) {
        if(released.get()) {
            return
        }
        synchronized(dataDownloadWorkers) {
            val aliveWorkers = dataDownloadWorkers.filter { it.isAlive() }
            if (aliveWorkers.isEmpty()) {
                callback.accept(false, "No worker to stop")
                return@synchronized
            }
            val stopCount = AtomicReference(mutableListOf<Int>())
            aliveWorkers.forEach { worker ->
                worker.registerObserverForStateChanges { workerId, workerState ->
                    if (workerState.isCompleted()) {
                        val alreadyAdded = stopCount.get().contains(workerId)
                        if (!alreadyAdded) {
                            stopCount.get().add(workerId)
                        }
                        worker.registerObserverForStateChanges(null)
                    }
                    if (aliveWorkers.count() == stopCount.get().count()) {
                        callback.accept(true, "stopped")
                    }
                }
                worker.stop()
            }
        }
    }

    override fun pauseAllWorkers(callback: BiConsumer<Boolean, String>) {
        if(released.get()) {
            return
        }
        synchronized(dataDownloadWorkers) {
            val runningWorkers = dataDownloadWorkers.filter { it.isRunning() }
            val runningCount = runningWorkers.count()
            val pausedStoppedCount = AtomicReference(mutableListOf<Int>())
            if (runningCount == 0) {
                callback.accept(false, "no running workers to pause")
                return@synchronized
            }
            var iShouldCheckAllWorkers = true
            var index = 0
            while (iShouldCheckAllWorkers) {
                val worker = runningWorkers[index]
                worker.registerObserverForStateChanges { workerId, workerState ->
                    val (stoppedWorker, stopMsg) = Pair(
                        workerState.isStopped(),
                        "can't pause workers. workers stopped initiated already"
                    )
                    val (errorInWorker, errorMsg) = Pair(
                        workerState.isError(),
                        "can't pause workers. some workers failed. download failed"
                    )
                    var noPauseMsg = ""
                    if (stoppedWorker) {
                        noPauseMsg = stopMsg
                    }
                    if (errorInWorker) {
                        noPauseMsg = errorMsg
                    }
                    if (noPauseMsg.isNotBlank()) {
                        callback.accept(false, noPauseMsg)
                        iShouldCheckAllWorkers = false
                        worker.registerObserverForStateChanges(null)
                        return@registerObserverForStateChanges
                    }
                    if (workerState.isPaused()) {
                        val alreadyAdded = pausedStoppedCount.get().contains(workerId)
                        if (!alreadyAdded) {
                            pausedStoppedCount.get().add(workerId)
                        }
                        worker.registerObserverForStateChanges(null)
                    }
                    if (runningCount == pausedStoppedCount.get().count()) {
                        callback.accept(true, "paused")
                    }

                }
                worker.pause()
                ++index
                if (index >= runningCount) {
                    iShouldCheckAllWorkers = false
                }
            }

        }
    }

    override fun resumeAllWorkers(callback: BiConsumer<Boolean, String>) {
        if(released.get()) {
            return
        }
        synchronized(dataDownloadWorkers) {
            val pausedWorkers = dataDownloadWorkers.filter { it.isPaused() }
            val pausedCount = pausedWorkers.count()
            if (pausedCount == 0) {
                callback.accept(false, "no paused workers to resume.")
                return@synchronized
            }
            val resumeCount = AtomicReference(mutableListOf<Int>())
            pausedWorkers.forEach { worker ->
                worker.registerObserverForStateChanges { workerId, workerState ->
                    if (workerState.isRunning()) {
                        val alreadyAdded = resumeCount.get().contains(workerId)
                        if (!alreadyAdded) {
                            resumeCount.get().add(workerId)
                        }
                        worker.registerObserverForStateChanges(null)
                    }
                    if (pausedCount == resumeCount.get().count()) {
                        callback.accept(true, "resumed")
                    }
                }
                worker.resume()
            }

        }
    }
}