package com.tanodxyz.gdownload

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import com.tanodxyz.gdownload.connection.ConnectionManagerImpl
import com.tanodxyz.gdownload.connection.URLConnectionFactory
import com.tanodxyz.gdownload.connection.URLConnectionHandler
import com.tanodxyz.gdownload.database.DownloadDatabaseManager
import com.tanodxyz.gdownload.database.GroupDownloadDatabaseFetcher
import com.tanodxyz.gdownload.database.SQLiteManager
import com.tanodxyz.gdownload.executors.BackgroundExecutorImpl
import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutorImpl

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/**
 * Implementation of  [Group] that runs a cycle after [groupLoopTimeMilliSecs] and it has the
 * capacity to run [concurrentDownloadsCapacity] concurrent downloads.
 */
class GroupImpl(
    val context: Context,
    val name: String,
    val id: Long,
    val groupLoopTimeMilliSecs: Long = DEF_GROUP_LOOP_INTERVAL_MILLISECONDS,
    val concurrentDownloadsCapacity: Int = GROUP_DEFAULT_DOWNLOAD_CAPACITY,
    /**
     * Network type that each [Downloader] will use to download files.
     */
    val networkType: NetworkType = NetworkType.ALL,
    /**
     * @see Download.connectionRetryCount
     */
    val connectionRetryCount: Int = DEF_RETRIES_PER_CONNECTION,
    /**
     * @see Download.maxNumberOfConnections
     */
    val maxConnectionPerDownload: Int = DEF_MAX_CONNECTIONS_PER_DOWNLOAD,
    /**
     * @see Download.progressUpdateTimeMilliSec
     */
    val progressUpdateTimeMilliSecs: Long = DEF_PROGRESS_UPDATE_MILLISECONDS,
    /**
     * This indicates whether to use main thread for [GroupListener] and [DownloadProgressListener] or
     * run the callbacks in worker threads.
     */
    val progressCallbacksOnMainThread: Boolean = true,
    /**
     * This will bind [GroupListener] and [DownloadProgressListener] callbacks to the lifecycle
     */
    val progressCallbackLifeCycle: Lifecycle? = null,
    /**
     * This path will be used as a default storage dir/folder
     */
    val filesSaveRootPath: File? = null,
    /**
     * This indicates that how connections will be made to the remote resources.
     * one can modify it to use okhttp or other methods.
     * @see DefaultURLConnectionHandler
     */
    val urlConnectionFactory: Factory<URLConnectionHandler>? = null,
    /**
     * Database manager used in storing [Download] objects.
     */
    val databaseManager: DownloadDatabaseManager? = null,

    val networkInfoProvider: NetworkInfoProvider,

    ) : Runnable, Group {

    private var groupCallbaHandler = GroupCallbackHandler(
        progressCallbacksOnMainThread,
        progressCallbackLifeCycle,
        BackgroundExecutorImpl()
    )
    private var running: Boolean = false
    private lateinit var thread: Thread
    private var downloaders: MutableList<DownloadManager> = mutableListOf()
    private var downloadsQueue = mutableListOf<GroupDownload>()
    private var priorityCounter = AtomicInteger(0)
    private val blocker = Any()
    private val executor =
        BackgroundExecutorImpl()

    init {
        createAllDownloaderWithoutWorkers()
    }


    private fun createAllDownloaderWithoutWorkers() {
        for (i: Int in 0 until concurrentDownloadsCapacity) {
            val createdDownloader = createDownloaderFromCurrentSetting()
            downloaders.add(createdDownloader)
        }
    }


    private fun createDownloaderFromCurrentSetting(): DownloadManager {
        val scheduledBackgroundExecutorImpl =
            ScheduledBackgroundExecutorImpl(
                lifecycle = progressCallbackLifeCycle
            )
        val downloadManagerBuilder = DownloadManager.Builder(context)
            .setCallbacksHandler(progressCallbacksOnMainThread)
            .setScheduledBackgroundExecutor(scheduledBackgroundExecutorImpl)
            .setConnectionManager(
                ConnectionManagerImpl(
                    urlConnectionFactory ?: URLConnectionFactory(),
                    scheduledBackgroundExecutorImpl
                )
            )
            .setNetworkInfoProvider(networkInfoProvider)
            .setDownloadDatabaseManager(
                databaseManager ?: SQLiteManager.getInstance(context.applicationContext)
            )
        if (filesSaveRootPath != null) downloadManagerBuilder.setStorageHelper(
            context.applicationContext,
            filesSaveRootPath!!
        ) else downloadManagerBuilder.setStorageHelper(
            context.applicationContext
        )
        val downloader = downloadManagerBuilder.build()
        downloader.registerNetworkChangeListener()
        return downloader
    }

    override fun run() {
        if (Thread.currentThread().equals(thread)) {
            while (isRunning()) {
                groupLoopCallback()
                LockSupport.parkNanos(blocker, (groupLoopTimeMilliSecs * 1000000))
            }
        }
    }

    private val groupLoopCallback = {
        withLocks(
            downloadQueueLock = true,
            downloaderLock = true,
            callback = {
                syncGroupDownloadStates()
                rearrangeQueueBasedOnHighestPriority()
                downloadsQueue.forEach { groupDownload ->
                    handleDownload(groupDownload)
                }
            }
        )
        parkGroupLoopThreadIfThereIsNothingToDo()
    }


    private fun unParkGroupLoopThread() {
        synchronized(blocker) {}
        LockSupport.unpark(thread)
    }

    private fun parkGroupLoopThreadIfThereIsNothingToDo() {
        if (shouldParkLooper()) {
            synchronized(blocker) {}
            LockSupport.park()
        }
    }

    private fun shouldParkLooper(): Boolean {
        var shouldParkThread = true
        withLocks(downloadQueueLock = true) {
            downloadsQueue.forEach { groupDownload ->
                when (groupDownload.desiredState) {
                    GroupDownloadStates.START,
                    GroupDownloadStates.STARTED,
                    GroupDownloadStates.PAUSE, GroupDownloadStates.STOP,
                    GroupDownloadStates.RUNNING, GroupDownloadStates.RESTART -> {
                        shouldParkThread = false
                        return@forEach
                    }
                    else -> {
                        if (groupDownload.currentState == GroupDownloadStates.RUNNING
                            || groupDownload.currentState == GroupDownloadStates.ENQUEUED ||
                            groupDownload.currentState == GroupDownloadStates.STARTED
                        ) {
                            shouldParkThread = false
                            return@forEach
                        }
                    }
                }
            }
        }
        return shouldParkThread
    }

    private fun handleDownload(groupDownload: GroupDownload) {
        when (groupDownload.currentState) {
            GroupDownloadStates.ENQUEUED -> {
                groupDownload.desiredState.apply {
                    if (this == GroupDownloadStates.START
                        || this == GroupDownloadStates.RUNNING || this == GroupDownloadStates.RESTART) {
                        startDownload(groupDownload, true)
                    }
                }
            }
            GroupDownloadStates.RUNNING, GroupDownloadStates.STARTED -> {
                groupDownload.desiredState.apply {
                    if (this == GroupDownloadStates.PAUSE) {
                        pauseDownload(groupDownload)
                    } else if (this == GroupDownloadStates.STOP) {
                        stopDownload(groupDownload)
                    }
                }
            }
            GroupDownloadStates.PAUSED -> {
                groupDownload.desiredState.apply {
                    if (this == GroupDownloadStates.RUNNING) {
                        resumeDownload(groupDownload)
                    }
                    if (this == GroupDownloadStates.STOP) {
                        stopDownload(groupDownload)
                    }
                }
            }
            GroupDownloadStates.STOPPED, GroupDownloadStates.FAILURE, GroupDownloadStates.SUCCESS -> {
                groupDownload.desiredState.apply {
                    if (this == GroupDownloadStates.RESTART) {
                        startDownload(groupDownload, true)
                    } else if (this == GroupDownloadStates.START) {
                        startDownload(groupDownload, true)
                    }
                }
            }
            else -> {
                throw IllegalArgumentException("Unknown condition i don't understand it ......  ")
            }
        }
    }

    private fun restartDownload(groupDownload: GroupDownload) {
        groupDownload.download.getDownloader()?.apply {
            restart()
            groupDownload.desiredState = GroupDownloadStates.STARTED
        }
    }

    private fun resumeDownload(groupDownload: GroupDownload) {
        groupDownload.download.getDownloader()?.apply {
            resumeDownload()
        }
    }

    private fun startDownload(
        groupDownload: GroupDownload,
        removePreviousListeners: Boolean = false
    ) {
        fun DownloadManager.startDownload(groupDownload: GroupDownload) {
            groupDownload.desiredState = GroupDownloadStates.STARTED
            download0(groupDownload.download, groupDownload.listener, removePreviousListeners)
        }

        var allDownloadersBusy = false
        val runningDownloadsCount = getRunningDownloadsCount()
        if (runningDownloadsCount < concurrentDownloadsCapacity) {
            val aFreeDownloader = getAFreeDownloader()
            if (aFreeDownloader == null) {
                allDownloadersBusy = true
            } else {
                aFreeDownloader.startDownload(groupDownload)
            }
        } else {
            val aFreeDownloader = findIdleLowerPriorityDownloader(groupDownload.priority)
            if (aFreeDownloader != null) {
                aFreeDownloader.startDownload(groupDownload)
            } else {
                allDownloadersBusy = true
            }
        }
        if (allDownloadersBusy) {
            groupCallbaHandler.notifyStateDownloadWaiting(
                state,
                groupDownload.download.getDownloadInfo(),
            )
        }
    }


    private fun findIdleLowerPriorityDownloader(priority: Int): DownloadManager? {
        var lowerPriorityDownloader: DownloadManager? = null
        val lowerPriorityDownloaders: MutableList<DownloadManager> = mutableListOf()

        val lowerPriorityDownloads = downloadsQueue.filter { it.priority < priority }
        lowerPriorityDownloads.forEach { lowerPriorityDownload ->
            val dwdrs =
                downloaders.filter { lowerPriorityDownload.download == it.activeDownloadPayload }
            lowerPriorityDownloaders.addAll(dwdrs)
        }

        val idleDownloaders = lowerPriorityDownloaders.filter { it.isBusy.not() }
        if (idleDownloaders.isNotEmpty()) {
            lowerPriorityDownloader = idleDownloaders[0]
        }
        return lowerPriorityDownloader
    }

    private fun getAFreeDownloader(): DownloadManager? {
        var freeDownloader: DownloadManager? = null
        downloaders.forEach { downloadManager ->
            if (!downloadManager.isBusy) {
                freeDownloader = downloadManager
                return@forEach
            }
        }
        freeDownloader?.apply {
            if (!this.executor.isExecutorAssigned()) {
                executor.setExecutor(DEF_MAX_THREADS_PER_EXECUTOR)
            }
        }
        return freeDownloader
    }

    private fun pauseDownload(groupDownload: GroupDownload) {
        groupDownload.download.getDownloader()?.apply {
            this.freezeDownload()
        }
    }

    private fun getRunningDownloadsCount(): Int {
        return downloaders.filter { it.isBusy }.count()

    }

    private fun stopDownload(groupDownload: GroupDownload) {
        groupDownload.download.getDownloader()?.apply {
            this.stopDownload()
        }
    }

    private fun rearrangeQueueBasedOnHighestPriority() {
        downloadsQueue.sortWith { o1, o2 -> o2.priority.compareTo(o1.priority) }
    }

    private fun withLocks(
        downloadQueueLock: Boolean = false,
        downloaderLock: Boolean = false,
        callback: () -> Unit
    ) {
        if (downloadQueueLock && downloaderLock) {
            synchronized(downloadsQueue) {
                synchronized(downloaders) {
                    callback()
                }
            }
        } else if (downloaderLock) {
            synchronized(downloaders) {
                callback()
            }
        } else if (downloadQueueLock) {
            synchronized(downloadsQueue) {
                callback()
            }
        }
    }

    private fun syncGroupDownloadStates() {

        downloaders.forEach { downloadManager ->
            val activeDownloadPayload = downloadManager.activeDownloadPayload
            activeDownloadPayload?.apply {
                downloadsQueue.find { it.download == this && (!it.cannotUpdateListeners()) }?.let {
                    when (this.getStatus()) {
                        Download.ENQUEUED -> {
                            it.currentState = (GroupDownloadStates.ENQUEUED)
                            groupCallbaHandler.notifyStateDownloadEnqueued(
                                state,
                                this.getDownloadInfo()
                            )
                        }
                        Download.STOPPED -> {
                            it.currentState = (GroupDownloadStates.STOPPED)
                            groupCallbaHandler.notifyStateDownloadStopped(
                                state,
                                this.getDownloadInfo()
                            )
                        }
                        Download.PAUSED -> {
                            it.currentState = (GroupDownloadStates.PAUSED)
                            groupCallbaHandler.notifyStateDownloadPaused(
                                state,
                                this.getDownloadInfo()
                            )
                        }
                        Download.DOWNLOADED -> {
                            it.currentState = (GroupDownloadStates.SUCCESS)
                            it.desiredState = it.currentState
                            groupCallbaHandler.notifyStateDownloadSuccess(
                                state,
                                this.getDownloadInfo()
                            )
                        }
                        Download.DOWNLOADING -> {
                            it.currentState = (GroupDownloadStates.RUNNING)
                            groupCallbaHandler.notifyStateDownloadRunning(
                                state,
                                this.getDownloadInfo()
                            )
                        }
                        Download.FAILED -> {
                            it.currentState = (GroupDownloadStates.FAILURE)
                            groupCallbaHandler.notifyStateDownloadFailed(
                                state,
                                this.getDownloadInfo(),
                                downloadManager.downloadFailure
                            )
                        }
                        Download.STARTING -> {
                            it.currentState = GroupDownloadStates.STARTED
                            groupCallbaHandler.notifyStateDownloadStarting(
                                state,
                                this.getDownloadInfo()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun Download.getDownloader(): Downloader? {
        val dc = downloaders.filter { it.activeDownloadPayload == this }
        return if (dc.isNullOrEmpty()) {
            null
        } else {
            dc[0]
        }
    }

    override fun start() {
        if (isRunning()) {
            return
        }
        thread = Thread(this, "Group-$name-$id-Thread")
        thread.start()
        setRunningState(true)
    }

    @Synchronized
    fun isRunning(): Boolean {
        return running
    }

    @Synchronized
    private fun setRunningState(running: Boolean) {
        this.running = running
    }

    override fun stop() {
        stopAll()
    }

    override fun add(download: Download, listener: DownloadProgressListener?): Long {
        runOnBackground {
            withLocks(downloadQueueLock = true) {
                downloadsQueue.add(
                    GroupDownload(
                        download,
                        priority = priorityCounter.incrementAndGet(),
                        listener = listener
                    )
                )
                groupCallbaHandler.notifyStateDownloadAdded(state, download.getDownloadInfo())
            }
            unParkGroupLoopThread()
        }
        return download.id
    }

    override fun add(url: String, name: String, listener: DownloadProgressListener?): Long {
        val download =
            Download(
                System.nanoTime(),
                url = url,
                filePath = name,
                networkType = networkType.value,
                queueId = id,
                connectionRetryCount = connectionRetryCount,
                maxNumberOfConnections = maxConnectionPerDownload,
                progressUpdateTimeMilliSec = progressUpdateTimeMilliSecs
            )
        add(download, listener)
        return download.id
    }

    override fun addAllWithListeners(
        downloadsListenerPairs: MutableList<Pair<Download, DownloadProgressListener?>>,
        idsCallback: Consumer<MutableList<Long>>?
    ) {
        runOnBackground {
            val idsList =
                MutableList(downloadsListenerPairs.count()) { index -> downloadsListenerPairs[index].first.id }
            groupCallbaHandler.runOnMain {
                idsCallback?.accept(idsList)
            }
            val downloads = MutableList(downloadsListenerPairs.count()) { index ->
                GroupDownload(
                    downloadsListenerPairs[index].first,
                    priority = priorityCounter.incrementAndGet(),
                    listener = downloadsListenerPairs[index].second
                )
            }
            withLocks(downloadQueueLock = true) {
                downloadsQueue.addAll(downloads)
            }
            unParkGroupLoopThread()
        }
    }

    override fun addAll(
        downloads: MutableList<Download>,
        idsCallback: Consumer<MutableList<Long>>?
    ) {
        MutableList(downloads.count()) { index ->
            Pair<Download, DownloadProgressListener?>(
                downloads[index],
                object : DownloadProgressListener {}
            )
        }.apply {
            runOnBackground { addAllWithListeners(this, idsCallback) }
        }

    }

    override fun startDownload(id: Long) {
        startDownloads(id)
    }

    override fun restartDownload(id: Long) {
        runOnBackground {
            id.findGroupDownload()?.let {
                it.desiredState = GroupDownloadStates.RESTART
            }
            unParkGroupLoopThread()
        }
    }

    override fun startDownloads(vararg ids: Long) {
        startDownloads(ids.toMutableList())
    }

    override fun startDownloads(ids: MutableList<Long>) {
        runOnBackground {
            ids.forEach { downloadId ->
                downloadId.findGroupDownload()?.let {
                    it.desiredState = GroupDownloadStates.START
                }
            }
            unParkGroupLoopThread()
        }
    }

    override fun stopDownload(id: Long) {
        runOnBackground {
            id.findGroupDownload()?.let {
                it.desiredState = GroupDownloadStates.STOP
            }
            unParkGroupLoopThread()
        }
    }

    private fun Long.findGroupDownload(): GroupDownload? {
        var groupDownload: GroupDownload? = null
        withLocks(downloadQueueLock = true) {
            groupDownload = downloadsQueue.find { it.download.id == this }
        }
        return groupDownload
    }

    private fun forAllDownloaders(callback: (Downloader) -> Unit) {
        withLocks(downloaderLock = true) {
            downloaders.forEach(callback)
        }
    }

    override fun stopAll() {
        runOnBackground {
            forAllDownloads {
                it.desiredState = GroupDownloadStates.STOP
            }
            unParkGroupLoopThread()
        }
    }

    override fun freezeDownload(id: Long) {
        runOnBackground {
            id.findGroupDownload()?.let {
                it.desiredState = GroupDownloadStates.PAUSE
            }
            unParkGroupLoopThread()
        }
    }

    override fun freezeAll() {
        runOnBackground {
            forAllDownloads {
                it.desiredState = GroupDownloadStates.PAUSE
            }
            unParkGroupLoopThread()
        }
    }

    private fun forAllDownloads(callback: (GroupDownload) -> Unit) {
        withLocks(downloadQueueLock = true) {
            downloadsQueue.forEach {
                callback(it)
            }
        }
    }

    override fun resumeDownload(id: Long) {
        runOnBackground {
            id.findGroupDownload()?.let {
                it.desiredState = GroupDownloadStates.RUNNING
            }
            unParkGroupLoopThread()
        }
    }

    override fun resumeAll() {
        runOnBackground {
            forAllDownloads {
                it.desiredState = GroupDownloadStates.RUNNING
            }
            unParkGroupLoopThread()
        }
    }

    override fun shutDown() {
        unParkGroupLoopThread()
        setRunningState(false)
        forAllDownloaders {
            it.stopDownload()
            it.shutDown { }
        }
        executor.shutDown()
        groupCallbaHandler.clean()
    }

    override fun attachProgressListener(id: Long, listener: DownloadProgressListener) {
        runOnBackground {
            id.findGroupDownload()?.apply {
                if (this.currentState == GroupDownloadStates.RUNNING) {
                    this.download.getDownloader()?.addListener(listener)
                }
                this.listener = listener
            }
        }

    }

    override fun removeProgressListener(id: Long) {
        runOnBackground {
            id.findGroupDownload()?.apply {
                if (this.currentState == GroupDownloadStates.RUNNING && this.listener != null) {
                    this.download.getDownloader()?.removeListener(listener!!)
                }
                this.listener = null
            }
        }
    }

    override fun addGroupProgressListener(groupListener: GroupListener) {
        runOnBackground {
            groupCallbaHandler.addListener(groupListener)
        }
    }

    override fun removeGroupProgressListener(groupListener: GroupListener) {
        runOnBackground {
            groupCallbaHandler.removeListener(groupListener)
        }
    }

    override fun purge(downloadStates: GroupDownloadStates) {
        runOnBackground {
            withLocks(downloadQueueLock = true) {
                var idx = 0
                while (idx > -1) {
                    val groupDownload = downloadsQueue[idx]
                    if (groupDownload.currentState == downloadStates) {
                        downloadsQueue.removeAt(idx)
                    } else {
                        ++idx
                    }
                    if (idx >= downloadsQueue.count()) {
                        idx = -1
                    }
                }
            }
        }
    }

    private fun runOnBackground(callback: Runnable) {
        executor.execute(callback)
    }

    @WorkerThread
    override fun getAllGroupDownloadsFromDatabase(): List<Download>? {
        return GroupDownloadDatabaseFetcher(databaseManager!!).fetchAllGroupDownloads(id)
    }

    @WorkerThread
    override fun getAllInCompleteDownloadsFromDatabase(): List<Download> {
        return GroupDownloadDatabaseFetcher(databaseManager!!).fetchAllGroupInCompleteDownloads(id)
    }

    override fun loadDownloadsFromDatabase(listener: Consumer<Int>?) {
        runOnBackground {
            val allInCompleteDownloadsFromDatabase = allInCompleteDownloadsFromDatabase
            val loadedDownloadsFromDatabase =
                MutableList(
                    allInCompleteDownloadsFromDatabase.count()
                ) { index ->
                    val download = allInCompleteDownloadsFromDatabase[index]
                    Pair<Download, DownloadProgressListener?>(download, null)
                }
            addAllWithListeners(loadedDownloadsFromDatabase) {
                listener?.accept(loadedDownloadsFromDatabase.count())
            }
        }
    }

    override fun isBusy(): Boolean {
        var allDownloaderBusy = false
        withLocks(downloaderLock = true) {
            allDownloaderBusy =
                concurrentDownloadsCapacity == downloaders.filter { it.isBusy }.count()
        }
        return allDownloaderBusy
    }

    override fun isTerminated(): Boolean {
        return isRunning()
    }


    @WorkerThread
    override fun getState(): GroupState {
        val INTERMEDIATE_PROGRESS = -1.0
        fun Number.isIntermediate() = this.toDouble() <= INTERMEDIATE_PROGRESS
        val groupId = id
        val groupName = name
        var groupProgress = 0.0
        val downloads = ArrayList<DownloadInfo>(downloadsQueue.count())
        val queuedDownloads = mutableListOf<DownloadInfo>()
        val pausedDownloads = mutableListOf<DownloadInfo>()
        val runningDownloads = mutableListOf<DownloadInfo>()
        val completedDownloads = mutableListOf<DownloadInfo>()
        val cancelledDownloads = mutableListOf<DownloadInfo>()
        val failedDownloads = mutableListOf<DownloadInfo>()
        var intermediateProgress = false
        var allGroupDownloadsProgress = 0.0
        withLocks(downloadQueueLock = true) {
            downloadsQueue.forEach { groupDownload ->
                val downloadInfo = DownloadInfo.newInstance(groupDownload.download)
                downloads.add(downloadInfo)
                when (downloadInfo.status) {
                    Download.ENQUEUED -> queuedDownloads.add(downloadInfo)
                    Download.PAUSED -> pausedDownloads.add(downloadInfo)
                    Download.DOWNLOADING -> runningDownloads.add(downloadInfo)
                    Download.DOWNLOADED -> completedDownloads.add(downloadInfo)
                    Download.STOPPED -> cancelledDownloads.add(downloadInfo)
                    Download.FAILED -> failedDownloads.add(downloadInfo)
                }

                // calculate group download progress
                val downloadProgress = groupDownload.download.getProgress()
                if (!intermediateProgress) {
                    intermediateProgress =
                        groupDownload.download.getContentLengthBytes().isIntermediate()
                    allGroupDownloadsProgress += downloadProgress
                } else if (!groupProgress.isIntermediate()) {
                    groupProgress = INTERMEDIATE_PROGRESS
                }
            }
            if (!groupProgress.isIntermediate()) {
                groupProgress = (allGroupDownloadsProgress / downloadsQueue.count().toDouble())
            }
        }
        return GroupState(
            groupId,
            groupName,
            downloads,
            queuedDownloads,
            pausedDownloads,
            runningDownloads,
            completedDownloads,
            cancelledDownloads,
            failedDownloads,
            groupProgress
        )
    }

    override fun getId(): androidx.core.util.Pair<Long, String> {
        return androidx.core.util.Pair(id, name)
    }


    class Builder(private val context: Context) {
        private var id: Long = 0
        private lateinit var name: String
        private var concurrentDownloadsCapacity = GROUP_DEFAULT_DOWNLOAD_CAPACITY
        private var networkType: NetworkType = NetworkType.ALL
        private var connectionRetryCount: Int = DEF_RETRIES_PER_CONNECTION
        private var maxConnectionPerDownload: Int = DEF_MAX_CONNECTIONS_PER_DOWNLOAD
        private var progressUpdateTimeMilliSecs: Long = DEF_PROGRESS_UPDATE_MILLISECONDS
        private var progressCallbacksOnMainThread: Boolean = true
        private var progressCallbackLifeCycle: Lifecycle? = null
        private var filesSaveRootPath: File? = null
        private var urlConnectionFactory: Factory<URLConnectionHandler>? = null
        private var databaseManager: DownloadDatabaseManager? = null
        private var groupLoopTimeMilliSecs = DEF_GROUP_LOOP_INTERVAL_MILLISECONDS
        private lateinit var networkInfoProvider: NetworkInfoProvider

        fun setGroupLoopTimeMilliSecs(milliSecs: Long): Builder {
            if (milliSecs < 1) {
                throw IllegalArgumentException("loop intervalid is not valid")
            }
            this.groupLoopTimeMilliSecs = milliSecs
            return this
        }

        fun setIdAndName(id: Long, name: String): Builder {
            this.id = id
            this.name = name
            return this
        }

        fun setNetworkInfoProvider(networkInfoProvider: NetworkInfoProvider): Builder {
            this.networkInfoProvider = networkInfoProvider
            return this
        }

        fun setConcurrentDownloadsCapacity(limit: Int): Builder {
            if (limit < 1) {
                throw IllegalArgumentException(" at lease one download limit")
            }
            this.concurrentDownloadsCapacity = limit
            return this
        }

        fun setNetworkType(networkType: NetworkType): Builder {
            this.networkType = networkType
            return this
        }

        fun setConnectionRetryCount(count: Int): Builder {
            if (count < 0) {
                throw IllegalArgumentException("invalid arg $count")
            }
            this.connectionRetryCount = count
            return this
        }

        fun setMaxConnectionsPerDownload(count: Int): Builder {
            if (count < 1) {
                throw IllegalArgumentException("at least one connection is required to download.")
            }
            if (count > DEF_MAX_CONNECTIONS_PER_DOWNLOAD) {
                throw IllegalArgumentException("supported max connections are 32")
            }
            maxConnectionPerDownload = count
            return this
        }

        fun setProgressUpdateTimeMilliSecs(milliSecs: Long): Builder {
            if (milliSecs <= 0) {
                throw IllegalArgumentException("milliSecs must be greater then zero. ")
            }
            progressUpdateTimeMilliSecs = milliSecs
            return this
        }

        fun setProgressCallbacksOnMainThread(mainThread: Boolean): Builder {
            this.progressCallbacksOnMainThread = mainThread
            return this
        }

        fun setProgressCallbackLifeCycle(lifecycle: Lifecycle?): Builder {
            this.progressCallbackLifeCycle = lifecycle
            return this
        }

        fun setFileSaveRootPath(path: File?): Builder {
            this.filesSaveRootPath = path
            return this
        }


        fun setUrlConnectionFactory(urlConnectionFactory: Factory<URLConnectionHandler>?): Builder {
            this.urlConnectionFactory = urlConnectionFactory
            return this
        }

        fun setDatabaseManager(databaseManager: DownloadDatabaseManager): Builder {
            this.databaseManager = databaseManager
            return this
        }

        fun build(): Group {
            if (id == 0L) {
                throw IllegalStateException("provide valid and unique id as this is ID")
            }

            return GroupImpl(
                context,
                name,
                id,
                groupLoopTimeMilliSecs,
                concurrentDownloadsCapacity,
                networkType,
                connectionRetryCount,
                maxConnectionPerDownload,
                progressUpdateTimeMilliSecs,
                progressCallbacksOnMainThread,
                progressCallbackLifeCycle,
                filesSaveRootPath,
                urlConnectionFactory,
                databaseManager,
                networkInfoProvider
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        return (other is Group && other.id.first == id)
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    enum class GroupDownloadStates {
        /**
         * When [Download] is added to the internal queue
         */
        ENQUEUED,

        /**
         * Indicates that download should be started.
         */
        START,
        /**
         * Indicates that download is going to start
         */
        STARTED,

        /**
         * Download is paused/freeze
         */
        PAUSED,

        /**
         * Indication that download should pause
         */
        PAUSE,

        /**
         * Download is stopped
         */
        STOPPED,

        /**
         * Download should stop
         */
        STOP,

        /**
         * Download is running
         */
        RUNNING,

        /**
         * Download success
         */
        SUCCESS,

        /**
         * Download failure
         */
        FAILURE,

        /**
         * Download should restart
         */
        RESTART
    }

    data class GroupDownload(
        val download: Download,
        var currentState: GroupDownloadStates = GroupDownloadStates.ENQUEUED,
        var desiredState: GroupDownloadStates = GroupDownloadStates.ENQUEUED,
        val priority: Int = 0,
        var listener: DownloadProgressListener? = null
    ) {

        fun cannotUpdateListeners(): Boolean {
            val cannotUpdateListeners = (currentState == GroupDownloadStates.FAILURE
                    || currentState == GroupDownloadStates.SUCCESS
                    || currentState == GroupDownloadStates.STOPPED
                    || currentState == GroupDownloadStates.PAUSED
                    || currentState == GroupDownloadStates.ENQUEUED)
                    &&
                    (desiredState != GroupDownloadStates.START
                            && desiredState != GroupDownloadStates.STARTED
                            && desiredState != GroupDownloadStates.RUNNING
                            && desiredState != GroupDownloadStates.RESTART)
            return cannotUpdateListeners
        }
    }

    companion object {
        const val TAG = "group"
    }
}