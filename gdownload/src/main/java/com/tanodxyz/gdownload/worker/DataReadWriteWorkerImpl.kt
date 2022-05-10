package com.tanodxyz.gdownload.worker

import com.tanodxyz.gdownload.BiConsumer
import com.tanodxyz.gdownload.DefaultLogger
import com.tanodxyz.gdownload.closeResource
import com.tanodxyz.gdownload.connection.ConnectionManager
import com.tanodxyz.gdownload.io.InputResourceWrapper
import com.tanodxyz.gdownload.io.OutputResourceWrapper
import com.tanodxyz.gdownload.io.RandomAccessOutputResourceWrapper
import com.tanodxyz.gdownload.io.StreamOutputResourceWrapper
import java.io.InterruptedIOException
import java.util.concurrent.locks.LockSupport

class DataReadWriteWorkerImpl(
    private val inputConnectionData: ConnectionManager.ConnectionData,
    private val output: OutputResourceWrapper,
    private val randomAccess: Boolean,
    private val readBufferSize: Int = DEFAULT_BUFFER_SIZE
) : DataReadWriteWorker {
    private var stateObserver: BiConsumer<Int, DataReadWriteWorker.WorkerState>? = null
    private lateinit var thread: Thread
    private var dataReadWriteWorkerState: DataReadWriteWorker.WorkerState =
        DataReadWriteWorker.WorkerState.IDLE
    val TAG = "DRWW${System.nanoTime()}"
    private val logger = DefaultLogger(TAG)

    override fun init() {
        if (isAlive()) {
            throw IllegalStateException("can't initialize it again. reason -> has thread")
        }
        this.thread = Thread.currentThread()
    }

    override fun pause() {
        if (isRunning()) {
            setWorkerState(DataReadWriteWorker.WorkerState.PAUSING)
            interruptThread()
        }
    }

    override fun stop() {
        if (isAlive()) {
            setWorkerState(DataReadWriteWorker.WorkerState.STOPPING)
            interruptThread()
        }
    }

    private fun isPausing(): Boolean {
        val workerState = getWorkerState()
        return (workerState == DataReadWriteWorker.WorkerState.PAUSING)
    }

    override fun resume() {
        when {
            isPaused() || isPausing() -> {
                setWorkerState(DataReadWriteWorker.WorkerState.RUNNING)
                unParkThread()
            }
        }
    }

    @Synchronized
    fun getWorkerState(): DataReadWriteWorker.WorkerState {
        return dataReadWriteWorkerState
    }

    @Synchronized
    fun setWorkerState(state: DataReadWriteWorker.WorkerState) {
        this.dataReadWriteWorkerState = state
        stateObserver?.accept(getId(), this.dataReadWriteWorkerState)
    }

    @Synchronized
    override fun registerObserverForStateChanges(
        observer: BiConsumer<Int, DataReadWriteWorker.WorkerState>?
    ) {
        this.stateObserver = observer
    }

    @Synchronized
    override fun unRegisterObserverForStateChanges() {
        stateObserver = null
    }

    override fun isAlive(): Boolean {
        val workerState = getWorkerState()
        return workerState == DataReadWriteWorker.WorkerState.RUNNING || workerState == DataReadWriteWorker.WorkerState.PAUSE || workerState == DataReadWriteWorker.WorkerState.PAUSING
    }

    override fun isRunning(): Boolean {
        val workerState = getWorkerState()
        return workerState == DataReadWriteWorker.WorkerState.RUNNING
    }

    override fun isPaused(): Boolean {
        val workerState = getWorkerState()
        return workerState == DataReadWriteWorker.WorkerState.PAUSE
    }

    override fun isDead(): Boolean {
        return isStopped() || isSuccess() || isError()
    }

    override fun isStopped(): Boolean {
        val workerState = getWorkerState()
        return workerState == DataReadWriteWorker.WorkerState.STOP
    }

    fun isStopping(): Boolean {
        val workerState = getWorkerState()
        return workerState == DataReadWriteWorker.WorkerState.STOPPING
    }

    override fun isSuccess(): Boolean {
        val workerState = getWorkerState()
        return workerState == DataReadWriteWorker.WorkerState.SUCCESS
    }

    override fun isError(): Boolean {
        val workerState = getWorkerState()
        return workerState == DataReadWriteWorker.WorkerState.ERROR
    }

    override fun getId(): Int {
        return inputConnectionData.slice?.id ?: System.currentTimeMillis().toInt()
    }

    override fun doJob(): Exception? {
        logger.d("Data read write worker doing read/write op start")
        setWorkerState(DataReadWriteWorker.WorkerState.RUNNING)
        val slice = inputConnectionData.slice
            ?: throw IllegalStateException("don't know where to start writing bytes --> slice not provided")
        val remoteConnection = inputConnectionData.remoteConnection
            ?: throw IllegalStateException("input stream from remote resource seems null")
        var exception: Exception? = null
        exception = try {
            val readBuffer = ByteArray(readBufferSize)
            var bytesRead: Int
            if (randomAccess) {
                logger.d("worker job is RandomAccess write")
                val randomAccessOutputWrapper =
                    output as RandomAccessOutputResourceWrapper
                var readResult =
                    inputConnectionData.remoteConnection!!.inputResourceWrapper.readAndCatchException(
                        readBuffer
                    )
                bytesRead = readResult.first
                var nextWriteOffset = slice.startByte + slice.downloaded.get()
                while (bytesRead > -1) {

                    parkThreadIfNecessary()
                    if (isStopping()) {
                        setWorkerState(DataReadWriteWorker.WorkerState.STOP)
                        break
                    }
                    if (readResult.second) {
                        reconnect(
                            slice.startByte,
                            slice.endByte,
                            slice.downloaded.get() + bytesRead
                        )
                    }
                    val input = inputConnectionData.remoteConnection!!.inputResourceWrapper
                    slice.downloaded.addAndGet(bytesRead.toLong())
                    synchronized(lock) {
                        randomAccessPointer = nextWriteOffset
                        randomAccessOutputWrapper.setWriteOffset(randomAccessPointer)
                        randomAccessOutputWrapper.write(readBuffer, 0, bytesRead)
                        randomAccessOutputWrapper.flush()
                        nextWriteOffset += bytesRead
                    }
                    readResult = input.readAndCatchException(readBuffer)
                    bytesRead = readResult.first
                }
                if (!isStopped()) {
                    slice.downloadComplete.set(true)
                    setWorkerState(DataReadWriteWorker.WorkerState.SUCCESS)
                }
                logger.d("JOB Finished result ---")
            } else {
                logger.d("worker job is serial write")
                val streamOutputResourceWrapper = output as StreamOutputResourceWrapper
                remoteConnection.inputResourceWrapper.apply {
                    bytesRead = read(readBuffer)
                    while (bytesRead > -1) {
                        slice.downloaded.addAndGet(bytesRead.toLong())
                        streamOutputResourceWrapper.write(readBuffer, 0, bytesRead)
                        streamOutputResourceWrapper.flush()
                        bytesRead = read(readBuffer)
                    }
                    slice.downloadComplete.set(true)
                    setWorkerState(DataReadWriteWorker.WorkerState.SUCCESS)
                }
            }
            null
        } catch (ex: Exception) {
            logger.e("Error occured in worker -> $ex")
            ex.printStackTrace()
            setWorkerState(DataReadWriteWorker.WorkerState.ERROR)
            ex
        } finally {
            closeResource(inputConnectionData.remoteConnection?.inputResourceWrapper)
        }
        return exception
    }

    private fun parkThreadIfNecessary() {
        if (getWorkerState() == DataReadWriteWorker.WorkerState.PAUSING) {
            setWorkerState(DataReadWriteWorker.WorkerState.PAUSE)
            while (getWorkerState() == DataReadWriteWorker.WorkerState.PAUSE) {
                LockSupport.park()
            }
        }
    }

    @Synchronized
    private fun unParkThread() {
        LockSupport.unpark(thread)
    }

    @Synchronized
    private fun interruptThread() {
        thread.interrupt()
    }

    private fun reconnect(startByte: Long, endByte: Long, downloaded: Long): InputResourceWrapper? {
        inputConnectionData.connectionFactory.addByteRangeHeader(
            startByte,
            endByte,
            downloaded
        )
        inputConnectionData.remoteConnection =
            inputConnectionData.connectionFactory.reconnect()
        return inputConnectionData.remoteConnection?.inputResourceWrapper
    }

    private fun InputResourceWrapper.readAndCatchException(buffer: ByteArray): Pair<Int, Boolean> {
        val slice = inputConnectionData.slice!!
        var bytesRead = 0
        var needsReconnection = false
        try {
            bytesRead = this.read(buffer)
        } catch (ex: Exception) {
            ex.printStackTrace()
            if (ex is InterruptedIOException) {
                inputConnectionData.remoteConnection?.apply { closeResource(this.inputResourceWrapper) }
                bytesRead = ex.bytesTransferred
                Thread.interrupted()
                needsReconnection = true
            } else {
                reconnect(slice.startByte, slice.endByte, slice.downloaded.get())
            }
        }
        return Pair(bytesRead, needsReconnection)
    }

    companion object {
        private var randomAccessPointer = 0L
        private var lock = Any()
    }
}