package com.tanodxyz.gdownload.worker

import com.tanodxyz.gdownload.BiConsumer
import com.tanodxyz.gdownload.connection.ConnectionManager
import com.tanodxyz.gdownload.io.OutputResourceWrapper

/**
 * The general contract of this class is to act as a Manager for individual
 * [DataReadWriteWorker]s.
 * As Each download may have multiple connections and each connection has [DataReadWriteWorker]
 * so this class will manage the overall state of all the [DataReadWriteWorker]s for a single download.
 */
interface DataReadWriteWorkersManager {
    /**
     * It will add new [DataReadWriteWorker] for the [connectionData].
     */
    fun addWorker(
        connectionData: ConnectionManager.ConnectionData
    ): Exception?

    /**
     * It will cause all the [DataReadWriteWorker]s to stop irrespective of the state of the
     * [DataReadWriteWorker]
     */
    fun release()

    fun init(outputIsRandomAccess: Boolean, outputResourceWrapper: OutputResourceWrapper)

    /**
     * It will stop all the [DataReadWriteWorker]s and the result will be given in the form of
     * [callback].
     * Note:- Best possible attempt is made to stop all the workers immediately and notify the user
     * with [callback].
     */
    fun stopAllWorkers(callback: BiConsumer<Boolean, String>)

    /**
     * It will pause/freeze all the [DataReadWriteWorker]s and the result will be given in the form of
     * [callback].
     * Note:- Best possible attempt is made to freeze/pause all the workers immediately and notify the user
     * with [callback].
     */
    fun pauseAllWorkers(callback: BiConsumer<Boolean, String>)

    /**
     * It will resume all the [DataReadWriteWorker]s and the result will be given in the form of
     * [callback].
     * Note:- Best possible attempt is made to resume all the workers immediately and notify the user
     * with [callback].
     */
    fun resumeAllWorkers(callback: BiConsumer<Boolean, String>)
}

