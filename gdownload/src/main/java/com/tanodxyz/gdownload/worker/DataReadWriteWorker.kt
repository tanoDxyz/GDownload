package com.tanodxyz.gdownload.worker

import com.tanodxyz.gdownload.BiConsumer
import com.tanodxyz.gdownload.worker.DataReadWriteWorker.WorkerState

/**
 * The contract specifies a Data Read/Write worker that does the following.
 * 1. Read chunks of data from remote resource [InputResourceWrapper]
 *
 * 2. Write the data to the output target [OutputResourceWrapper]
 *
 * 3. Any client could listen for it's state changes. @see [WorkerState]
 *
 * 4. Pausing/Freezing the worker - This Read/Write Worker also supports pausing/freezing
 *    - and by that we means if client calls pause/freeze method it will terminate
 *    or close the connection to the remote resource and park the thread.
 *
 * 5. Resuming the worker - it states that if previously somehow worker was paused it will
 *    unPark the thread and make connection to the remote resource.
 *    If contentLength = 23; before Pause DownloadedBytes = 10
 *    by resuming it will have the start byte = 11
 *
 *    Stopping the worker - it will terminate or close the connection to the remote resource and
 *    it will not park or make the thread wait.
 *    worker that is stopped can't be resume but restarted as the thread is yielded.
 *    if stopped worker is restarted the behaviours of the connection to the remote resource
 *    would be same as of Pause/freeze.
 *    If contentLength = 23; before Pause DownloadedBytes = 10
 *    by resuming it will have the start byte = 11
 *
 *    Note:- Each Connection has One instance of this class.
 */
interface DataReadWriteWorker {
    fun init()

    /**
     * freeze/pause the worker and thread would go in Parked(waiting state) until
     * it is resumed or stopped
     *
     */
    fun pause()

    /**
     * resume the previously paused worker.
     * worker that is either [stopped,success,error] is not affected
     */
    fun resume()

    /**
     * stop the worker and let the thread go where it belongs.
     * it affects only those workers who has the following states.
     * [running,paused]
     */
    fun stop()

    /**
     * Worker is in Running state means it is actively performing read/write operation.
     */
    fun isRunning(): Boolean

    /**
     * Paused worker is Alive but not actively taking part in IO operation.
     */
    fun isPaused(): Boolean

    /**
     * Worker that is stopped from doing Read/write operation and has no parked thread.
     */
    fun isStopped(): Boolean

    /**
     * Worker is considered as Dead if either of the following is true.
     *
     * Completed IO operation (Success)
     * Failed IO operation (ERROR)
     * Stopped
     */
    fun isDead(): Boolean

    /**
     * worker is alive if either is paused or actively doing IO operation
     */
    fun isAlive(): Boolean

    /**
     * Despite all the hardships worker successfully completed IO operation
     */
    fun isSuccess(): Boolean

    /**
     * when IO operation is interrupted by an error.
     */
    fun isError(): Boolean

    /**
     * initially when [init] is called. The contract of this method is to do the job and return
     * any exception occurred as it's name implies.
     */
    fun doJob(): Exception?

    /**
     * This will register a state observer and it will be called for each successful state change
     * Calling this method twice will make the previous State Listener to disappear and the new
     * one will takes it's place.
     */
    fun registerObserverForStateChanges(
        observer: BiConsumer<Int, WorkerState>?
    )

    /**
     * it's unique id
     */
    fun getId(): Int

    /**
     * UnRegister the state observer.
     * it has same effect as that of calling [registerObserverForStateChanges] with null value
     */
    fun unRegisterObserverForStateChanges()
    enum class WorkerState {
        IDLE, PAUSING, PAUSE, STOPPING, STOP, RUNNING, SUCCESS, ERROR;

        fun isCompleted(): Boolean {
            return this == STOP || this == SUCCESS || this == ERROR
        }

        fun isPaused(): Boolean {
            return this == PAUSE
        }

        fun isStopped(): Boolean {
            return this == STOP
        }

        fun isSuccess(): Boolean {
            return this == SUCCESS
        }

        fun isError(): Boolean {
            return this == ERROR
        }

        fun isRunning(): Boolean {
            return this == RUNNING
        }
    }
}