package com.tanodxyz.gdownload

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.tanodxyz.gdownload.connection.ConnectionManager
import com.tanodxyz.gdownload.connection.ConnectionManagerImpl
import com.tanodxyz.gdownload.connection.URLConnectionFactory
import com.tanodxyz.gdownload.connection.URLConnectionHandler
import com.tanodxyz.gdownload.database.DownloadDatabaseManager
import com.tanodxyz.gdownload.database.SQLiteManager
import com.tanodxyz.gdownload.executors.BackgroundExecutor
import com.tanodxyz.gdownload.executors.BackgroundExecutorImpl
import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutor
import com.tanodxyz.gdownload.executors.ScheduledBackgroundExecutorImpl
import com.tanodxyz.gdownload.io.DefaultFileStorageHelper
import com.tanodxyz.gdownload.io.FileStorageHelper
import java.io.File

/**
 * Although! each component of the library can be used independently without any extra initialization but
 * this class provides some easy to use ways to deal with the library.
 * >
 * ### Initialization
 * > ```GDownload.init(lifeCycle:Lifecycle? = null) ```. if non-null lifecycle is provided all the
 * [Downloader] and [Group] objects that are created with [GDownload.getFreeDownloaderOrCreateNewOne],
 * [GDownload.getFreeGroupOrCreateNewOne], [GDownload.freeDownloader], [GDownload.freeGroup], [GDownload.singleDownload],
 *will shutdown when lifecycle is destroyed.
 *
 * ### Note
 * * Any method that has [LifeCycleOwner], [Activity], [FragmentActivity], [LifeCycle] as a parameter and
 * creates [Downloader] or [Group] will bound the respective [Downloader] or [Group] progress callbacks to
 * that [LifeCycle]
 * #### see [DownloadManager]
 */
object GDownload : DefaultLifecycleObserver {
    private var lifecycle: Lifecycle? = null
    var LOGGING_ENABLED = BuildConfig.DEBUG
    const val TAG = "GDownload"
    private lateinit var independentDownloaderPool: MutableList<Downloader>
    private lateinit var groupPool: MutableList<Group>
    private lateinit var backgroundExecutorImpl: BackgroundExecutor
    private lateinit var mainThreadHandler: Handler
    private var logger = DefaultLogger(TAG)

    fun init(lifecycle: Lifecycle? = null) {
        independentDownloaderPool = mutableListOf()
        groupPool = mutableListOf()
        backgroundExecutorImpl = BackgroundExecutorImpl()
        mainThreadHandler = Handler(Looper.getMainLooper())
        addLifeCycle(lifecycle)
        logger.d(initialised())
    }

    private fun addLifeCycle(lifecycle: Lifecycle? = null) {
        this.lifecycle = lifecycle
        lifecycle?.apply {
            addObserver(this@GDownload)
        }
    }

    /**
     * As the name implies create a new [Downloader] or re-use the existing one
     * @param callbackOnMainThread if true - the [callback] will be called on MainThread else background thread
     */
    fun getFreeDownloaderOrCreateNewOne(
        context: Context,
        downloaderCreateSettings: DownloaderCreateSettings,
        callbackOnMainThread: Boolean = true,
        callback: (Downloader) -> Unit
    ) {
        runOnBackground {
            withLocks(downloaderListLock = true) {
                logger.d("FreeDownloader Requested")
                var freeDownloader: Downloader? = null
                val freeDownloaders =
                    independentDownloaderPool.filter { it.isBusy.not() && it.isTerminated().not() }
                runOnMainThread {
                    freeDownloader = if (freeDownloaders.isNullOrEmpty()) {
                        logger.d("FreeDownloader Created")
                        val downloader = freeDownloader(
                            context,
                            downloaderCreateSettings.schedulerBackgroundExecutor,
                            downloaderCreateSettings.downloadCallbacksHandler,
                            downloaderCreateSettings.fileStorageHelper,
                            downloaderCreateSettings.connectionManager,
                            downloaderCreateSettings.downloadDatabaseManager,
                            downloaderCreateSettings.networkInfoProvider
                        )
                        downloader
                    } else {
                        logger.d("FreeDownloader used one")
                        freeDownloaders[0]
                    }
                    runOnBackground {
                        independentDownloaderPool.add(freeDownloader!!)
                        runOnSelectedThread(callbackOnMainThread) { callback(freeDownloader!!) }
                    }
                }
            }
        }
    }

    /**
     * As the name implies create a new [Group] or re-use the existing one
     * @param callbackOnMainThread if true - the [callback] will be called on MainThread else background thread
     */
    fun getFreeGroupOrCreateNewOne(
        context: Context,
        downloadGroupCreateSettings: GroupCreateSettings,
        callbackOnMainThread: Boolean = true,
        callback: (Group) -> Unit
    ) {
        runOnBackground {
            withLocks(groupListLock = true) {
                logger.d("FreeDownloadGroup Requested")
                var freeGroup: Group? = null
                val freeGroups = groupPool.filter { !it.isBusy && !it.isTerminated }
                runOnMainThread {
                    freeGroup = if (freeGroups.isNullOrEmpty()) {
                        logger.d("FreeDownloadGroup new group created!")
                        val group = freeGroup(context, downloadGroupCreateSettings)
                        group
                    } else {
                        freeGroups[0]
                    }
                    runOnBackground {
                        groupPool.add(freeGroup!!)
                        runOnSelectedThread(callbackOnMainThread) {
                            callback(freeGroup!!)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        runOnBackground {
            withLocks(downloaderListLock = true, groupListLock = true) {
                logger.d("Releasing resources")
                independentDownloaderPool.forEach { downloader ->
                    downloader.shutDown(null)
                }
                groupPool.forEach { group ->
                    group.shutDown()
                }
                backgroundExecutorImpl.shutDown()
                mainThreadHandler.removeCallbacksAndMessages(null)
                logger.d("Released!")
            }
        }
    }

    private fun withLocks(
        downloaderListLock: Boolean = false,
        groupListLock: Boolean = false,
        callback: () -> Unit
    ) {
        if (downloaderListLock && groupListLock) {
            synchronized(independentDownloaderPool) {
                synchronized(groupPool) {
                    callback()
                }
            }
        } else if (downloaderListLock) {
            synchronized(independentDownloaderPool) {
                callback()
            }
        } else if (groupListLock) {
            synchronized(groupPool) {
                callback()
            }
        }
    }

    private fun runOnBackground(callback: Runnable) {
        backgroundExecutorImpl.execute(callback)
    }

    private fun runOnMainThread(callback: Runnable) {
        mainThreadHandler.post(callback)
    }

    private fun runOnSelectedThread(mainThread: Boolean, runnable: Runnable) {
        if (mainThread) {
            runOnMainThread(runnable)
        } else {
            runnable.run()
        }
    }

    /**
     * This method will load all the downloads from database.
     * @param threadMain if true - the [callback] will be called on MainThread else background thread
     */
    fun loadAllDownloadsFromDatabase(
        context: Context,
        threadMain: Boolean = true,
        callback: (MutableList<Download>) -> Unit
    ) {
        runOnBackground {
            val dbManager = SQLiteManager.getInstance(context.applicationContext)
            val allDownloads = dbManager.getAll()
            runOnSelectedThread(threadMain) {
                callback(allDownloads)
            }
        }
    }

    /**
     * This method will try to load all the incomplete or failed downloads from database.
     */
    fun loadAllInCompleteDownloadsFromDatabase(
        context: Context,
        resultCallbackOnMainThread: Boolean,
        listener: (MutableList<Download>) -> Unit
    ) {
        runOnBackground {
            logger.d("Loading ALL incomplete Downloads From Database")
            val dbManager = SQLiteManager.getInstance(context.applicationContext)
            val incompleteDownloads = dbManager.findAllInCompleteDownloads()
            runOnSelectedThread(resultCallbackOnMainThread) {
                listener(incompleteDownloads)
            }
        }
    }

    /**
     * This method will try to load all the incomplete or failed downloads from database and
     * then assign each one to specific [Group] as [Download.queueId] indicates.
     * @param downloadProgressListener this listener will be attached to all the individual [Download]s
     * @param groupProgressListener this listener will be attached to all the [Group]s. In other words
     *all the groups will share single [GroupListener].
     * @param resultCallbackOnMainThread if true - the [listener] will be called on MainThread else background thread
     * >once the [List] of [Group] is returned - [Group.start] should be called.
     */
    fun loadAllInCompleteDownloadsFromDatabase(
        context: Context,
        groupCreateSettings: GroupCreateSettings? = null,
        downloadProgressListener: DownloadProgressListener? = null,
        groupProgressListener: GroupListener? = null,
        resultCallbackOnMainThread: Boolean = false,
        listener: (List<Group>?) -> Unit
    ) {
        runOnBackground {
            logger.d("Loading ALL incomplete Downloads From Database")
            val c = object : (List<Group>?) -> Unit {
                override fun invoke(p1: List<Group>?) {
                    logger.d("Loaded InComplete Downloads and Group Count is ${p1?.count() ?: 0}")
                    runOnSelectedThread(resultCallbackOnMainThread) { listener(p1) }
                }
            }
            val dbManager = SQLiteManager.getInstance(context.applicationContext)
            val incompleteDownloads = dbManager.findAllInCompleteDownloads()
            val uniqueGroupIdsSet = mutableSetOf<Long>()
            incompleteDownloads.forEach {
                uniqueGroupIdsSet.add(it.getQueueId())
            }
            val incompleteDownloadsGroups = mutableListOf<Group>()
            if (uniqueGroupIdsSet.isNullOrEmpty()) {
                c.invoke(null)
            } else {
                uniqueGroupIdsSet.forEach { groupId ->
                    val groupIncompleteDownloads =
                        incompleteDownloads.filter { it.getQueueId() == groupId }
                    if (!groupIncompleteDownloads.isNullOrEmpty()) {
                        val downloadAndProgressListenerPairList =
                            MutableList(groupIncompleteDownloads.count()) { index ->
                                kotlin.Pair(
                                    groupIncompleteDownloads[index],
                                    downloadProgressListener,
                                )
                            }
                        withLocks(groupListLock = true) {
                            val groupListInCreatedGroupPool =
                                groupPool.filter { it.id.first == groupId && (!it.isTerminated) }
                            if (groupListInCreatedGroupPool.isNullOrEmpty()) {
                                val newCreatedGroupForGroupId = freeGroup(
                                    context,
                                    groupId,
                                    groupCreateSettings,
                                )
                                groupProgressListener?.let {
                                    newCreatedGroupForGroupId.addGroupProgressListener(
                                        groupProgressListener
                                    )
                                }
                                groupPool.remove(newCreatedGroupForGroupId) // remove in case it is terminated
                                groupPool.add(newCreatedGroupForGroupId)
                                newCreatedGroupForGroupId.addAllWithListeners(
                                    downloadAndProgressListenerPairList,
                                    null
                                )
                                incompleteDownloadsGroups.add(newCreatedGroupForGroupId)
                            } else {
                                groupProgressListener?.let {
                                    groupListInCreatedGroupPool.forEach {
                                        it.addGroupProgressListener(
                                            groupProgressListener
                                        )
                                    }
                                }
                                groupListInCreatedGroupPool.forEach { group ->
                                    group.addAllWithListeners(
                                        downloadAndProgressListenerPairList,
                                        null
                                    )
                                }
                                incompleteDownloadsGroups.addAll(groupListInCreatedGroupPool)
                            }
                        }
                    }
                }
            }
            c.invoke(incompleteDownloadsGroups)
        }
    }

    fun deleteAllCompletedDownloadsFromDatabase(
        context: Context,
        callbackOnMainThread: Boolean = false,
        listener: ((Int) -> Unit)? = null
    ) {
        runOnBackground {
            logger.d("Deleting All Completed Downloads From Cache")
            val deletedDownloadsRowsAffected =
                SQLiteManager.getInstance(context).deleteDownloads(Download.DOWNLOADED)
            logger.d("Affected Rows By Deleting Completed Downloads are $deletedDownloadsRowsAffected")
            runOnSelectedThread(callbackOnMainThread) {
                listener?.invoke(deletedDownloadsRowsAffected)
            }
        }
    }

    fun deleteAllDownloadsFromDatabase(
        context: Context,
        callbackOnMainThread: Boolean = true,
        listener: ((Int) -> Unit)? = null
    ) {
        runOnBackground {
            logger.d("Deleting All Downloads from database")
            val deletedDownloadsRowsAffected =
                SQLiteManager.getInstance(context).deleteAllDownloads()
            logger.d("All Downloads deleted with affected row count is $deletedDownloadsRowsAffected")
            runOnSelectedThread(callbackOnMainThread) {
                listener?.invoke(deletedDownloadsRowsAffected)
            }
        }
    }

    /**
     * This method will not only delete the downloads from database but it will remove any files created.
     * @param callbackOnMainThread if true [listener] will be invoked on MainThread else Background thread will be used.
     * @param listener callback indicating number of files successfully deleted
     */
    fun deleteAllDownloads(
        context: Context,
        callbackOnMainThread: Boolean = true,
        listener: ((Int) -> Unit)? = null
    ) {
        loadAllDownloadsFromDatabase(context, true) { downloadsFromDatabase ->
            if (downloadsFromDatabase.isNullOrEmpty()) {
                listener?.invoke(0)
            } else {
                runOnBackground {
                    var deletedFilesCounter = 0
                    downloadsFromDatabase.forEach {
                        File(it.getFilePath()).apply {
                            if (exists()) {
                                val fileDeleted = delete()
                                if (fileDeleted) {
                                    deletedFilesCounter++
                                }
                            }
                        }
                    }
                    deleteAllDownloadsFromDatabase(context, callbackOnMainThread) {
                        listener?.invoke(deletedFilesCounter)
                    }
                }
            }
        }
    }

    /**
     * This method will either create a new [Downloader] or re-use the existing one.
     * @param lifeCycleOwner Download Progress callbacks will be attached to this lifeCycleOwner
     * and once it is destroyed no [DownloadProgressListener] callbacks will be triggered or delivered to the user.
     * @param downloadFilesRoot see [FileStorageHelper.getFilesRoot]
     */
    fun freeDownloader(
        context: Context,
        lifeCycleOwner: LifecycleOwner? = null,
        downloadFilesRoot: File? = null,
        downloadCallbacksOnMainThread: Boolean = true,
        connectionFactory: Factory<URLConnectionHandler>? = null,
        resultCallbackOnMainThread: Boolean = true,
        downloaderCreateSettings: DownloaderCreateSettings? = null,
        callback: (Downloader) -> Unit
    ) {
        val ds = downloaderCreateSettings ?: createDownloadSetting(
            context,
            lifeCycleOwner,
            downloadFilesRoot,
            downloadCallbacksOnMainThread,
            connectionFactory
        )
        getFreeDownloaderOrCreateNewOne(
            context,
            ds,
            resultCallbackOnMainThread,
            callback
        )
    }

    /**
     * @return a free group and this group is not attached to any [Lifecycle] and it won't
     * shutdown if [GDownload.lifecycle] is destroyed.
     */
    fun freeGroup(context: Context, settings: GroupCreateSettings): Group {
        val build = GroupImpl.Builder(context)
            .setConcurrentDownloadsCapacity(settings.concurrentDownloadsRunningCapacity)
            .setConnectionRetryCount(settings.connectionRetryCount)
            .setDatabaseManager(settings.databaseManager ?: SQLiteManager.getInstance(context))
            .setFileSaveRootPath(settings.filesSaveRootPath)
            .setGroupLoopTimeMilliSecs(settings.groupLoopTimeMilliSecs)
            .setIdAndName(settings.id, settings.name)
            .setMaxConnectionsPerDownload(settings.maxConnectionPerDownload)
            .setNetworkType(settings.networkType)
            .setProgressCallbackLifeCycle(settings.progressCallbackLifeCycle)
            .setProgressCallbacksOnMainThread(settings.progressCallbacksOnMainThread)
            .setUrlConnectionFactory(settings.urlConnectionFactory)
            .setProgressUpdateTimeMilliSecs(settings.progressUpdateTimeMilliSecs)
            .setNetworkInfoProvider(settings.networkInfoProvider ?: NetworkInfoProvider(context))
            .build()
        return build
    }

    /**
     * Create a new [Group] or re-use the existing one.
     */
    fun freeGroup(context: Context, props: GroupCreateSettings.() -> Unit) {
        val groupCreateSettings = GroupCreateSettings()
        props(groupCreateSettings)
        freeGroup(context, groupCreateSettings, true) { group ->
            groupCreateSettings.group = group
            props(groupCreateSettings)
        }
    }

    /**
     * Create a new [Group] or re-use the existing one.
     */
    fun freeGroup(
        context: Context,
        settings: GroupCreateSettings,
        resultCallbackOnMainThread: Boolean = true,
        callback: (Group) -> Unit
    ) {
        GDownload.getFreeGroupOrCreateNewOne(
            context,
            settings,
            resultCallbackOnMainThread,
            callback
        )
    }

    /**
     * Create a new [Downloader] or re-use the existing one.
     */
    fun singleDownload(
        context: Context,
        lifeCycleOwner: LifecycleOwner? = null,
        downloadFilesRoot: File? = null,
        downloadCallbacksOnMainThread: Boolean = true,
        connectionFactory: Factory<URLConnectionHandler>? = null,
        resultCallbackOnMainThread: Boolean = true,
        downloaderCreateSettings: DownloaderCreateSettings? = null,
        download: Download,
        progressListener: DownloadProgressListener?,
        callback: (Downloader) -> Unit
    ) {
        freeDownloader(
            context,
            lifeCycleOwner,
            downloadFilesRoot,
            downloadCallbacksOnMainThread,
            connectionFactory,
            resultCallbackOnMainThread,
            downloaderCreateSettings
        ) { downloader ->
            downloader.download(download, progressListener)
            callback(downloader)
        }
    }

    /**
     * Create a new [Downloader] or re-use the existing one and binds the [DownloadProgressListener]
     * callbacks with that of [activity]
     */
    fun singleDownload(
        activity: AppCompatActivity,
        props: DownloadProperties.() -> Unit
    ) {
        singleDownload(activity, activity, props)
    }

    /**
     * Create a new [Downloader] or re-use the existing one and binds the [DownloadProgressListener]
     * callbacks with that of [activity]
     */
    fun singleDownload(
        activity: FragmentActivity,
        props: DownloadProperties.() -> Unit
    ) {
        singleDownload(activity, activity, props)
    }

    /**
     * Create a new [Downloader] or re-use the existing one and binds the [DownloadProgressListener]
     * callbacks with that of [lifeCycleOwner]
     */
    fun singleDownload(
        context: Context,
        lifeCycleOwner: LifecycleOwner? = null,
        props: DownloadProperties.() -> Unit
    ) {
        val dProperties = DownloadProperties()
        props(dProperties)
        dProperties.apply {
            freeDownloader(
                context,
                lifeCycleOwner,
                downloadFilesRoot,
                downloadCallbacksOnMainThread,
                connectionFactory,
                true
            ) { downloader ->
                val download = Download(
                    System.nanoTime(),
                    url!!,
                    name!!,
                    connectionRetryCount = connectionRetryCount,
                    maxNumberOfConnections = maxNumberOfConnections,
                    networkType = networkType,
                    progressUpdateTimeMilliSec = progressUpdateTimeMilliSec
                )
                downloader.download(download, downloadProgressListener)
                this.downloader = downloader
                props(this)
            }
        }
    }

    fun singleDownload(
        context: Context,
        lifeCycleOwner: LifecycleOwner? = null,
        downloadFilesRoot: File? = null,
        downloadCallbacksOnMainThread: Boolean = true,
        connectionFactory: Factory<URLConnectionHandler>? = null,
        resultCallbackOnMainThread: Boolean = true,
        downloaderCreateSettings: DownloaderCreateSettings? = null,
        name: String,
        url: String,
        progressListener: DownloadProgressListener?,
        callback: (Downloader) -> Unit
    ) {
        freeDownloader(
            context,
            lifeCycleOwner,
            downloadFilesRoot,
            downloadCallbacksOnMainThread,
            connectionFactory,
            resultCallbackOnMainThread,
            downloaderCreateSettings
        ) { downloader ->
            downloader.download(name, url, NetworkType.ALL, progressListener)
            callback(downloader)
        }
    }

    /**
     * @return a free [Downloader] and that is not attached to [GDownload.lifecycle].
     */
    fun freeDownloader(
        context: Context,
        schedulerBackgroundExecutor: ScheduledBackgroundExecutor,
        downloadCallbacksHandler: DownloadCallbacksHandler,
        fileStorageHelper: FileStorageHelper,
        connectionManager: ConnectionManager,
        downloadDatabaseManager: DownloadDatabaseManager,
        networkInfoProvider: NetworkInfoProvider
    ): Downloader {
        return DownloadManager.Builder(context)
            .setScheduledBackgroundExecutor(schedulerBackgroundExecutor)
            .setCallbacksHandler(downloadCallbacksHandler).setStorageHelper(fileStorageHelper)
            .setConnectionManager(connectionManager)
            .setNetworkInfoProvider(networkInfoProvider)
            .setDownloadDatabaseManager(downloadDatabaseManager).build()
    }

    fun createDownloadSetting(
        context: Context,
        lifeCycleOwner: LifecycleOwner? = null,
        downloadFilesRoot: File? = null,
        downloadCallbacksOnMainThread: Boolean = true,
        connectionFactory: Factory<URLConnectionHandler>? = null
    ): DownloaderCreateSettings {
        val scheduledBackgroundExecutorImpl = ScheduledBackgroundExecutorImpl(
            DEF_MAX_THREADS_PER_EXECUTOR,
            lifeCycleOwner?.lifecycle
        )
        val downloadCallbacksHandler = DownloadCallbacksHandler(
            downloadCallbacksOnMainThread,
            lifeCycleOwner?.lifecycle
        )
        val defaultFileStorageHelper = DefaultFileStorageHelper(context)
        if (downloadFilesRoot == null) {
            defaultFileStorageHelper.setFilesRootToDownloadsOrFallbackToInternalDirectory()
        } else {
            defaultFileStorageHelper.setFilesRoot(downloadFilesRoot)
        }
        val connectionManager = ConnectionManagerImpl(
            connectionFactory ?: URLConnectionFactory(),
            scheduledBackgroundExecutorImpl
        )
        val databaseManager = SQLiteManager.getInstance(context)
        return DownloaderCreateSettings(
            scheduledBackgroundExecutorImpl,
            downloadCallbacksHandler,
            defaultFileStorageHelper,
            connectionManager,
            databaseManager,
            NetworkInfoProvider(context)
        )
    }

    fun createGroupSettings(groupId: Long, groupName: String): GroupCreateSettings {
        return GroupCreateSettings().apply {
            id = groupId
            name = groupName
        }
    }

    fun freeGroup(
        context: Context,
        groupId: Long,
        groupCreateSettings: GroupCreateSettings? = null
    ): Group {
        val newGroupSettings =
            if (groupCreateSettings == null) {
                createGroupSettings(groupId, getGroupName(groupId))
            } else {
                groupCreateSettings.id = groupId
                groupCreateSettings
            }
        return freeGroup(context, newGroupSettings)
    }

    class DownloaderCreateSettings(
        var schedulerBackgroundExecutor: ScheduledBackgroundExecutor,
        var downloadCallbacksHandler: DownloadCallbacksHandler,
        var fileStorageHelper: FileStorageHelper,
        var connectionManager: ConnectionManager,
        var downloadDatabaseManager: DownloadDatabaseManager,
        var networkInfoProvider: NetworkInfoProvider
    )

    class DownloadProperties {
        internal var downloader: Downloader? = null
        var name: String? = null
        var url: String? = null
        var downloadCallbacksOnMainThread = true
        var connectionFactory: Factory<URLConnectionHandler> = URLConnectionFactory()
        var downloadFilesRoot: File? = null
        var networkType: Int = NetworkType.valueOf(NetworkType.ALL)
        var connectionRetryCount: Int = DEF_RETRIES_PER_CONNECTION
        var maxNumberOfConnections: Int = DEF_MAX_CONNECTIONS_PER_DOWNLOAD
        var progressUpdateTimeMilliSec: Long = DEF_PROGRESS_UPDATE_MILLISECONDS
        fun getDownloader(): Downloader? = downloader
        var downloadProgressListener: DownloadProgressListener? = null
    }

    class GroupCreateSettings {
        internal var group: Group? = null
        fun getGroup(): Group? = group
        var id: Long = System.nanoTime()
        var name: String = getGroupName(id)
        var groupLoopTimeMilliSecs: Long = DEF_GROUP_LOOP_INTERVAL_MILLISECONDS
        var concurrentDownloadsRunningCapacity: Int = GROUP_DEFAULT_DOWNLOAD_CAPACITY
        var networkType: NetworkType = NetworkType.ALL
        var connectionRetryCount: Int = DEF_RETRIES_PER_CONNECTION
        var maxConnectionPerDownload: Int = DEF_MAX_CONNECTIONS_PER_DOWNLOAD
        var progressUpdateTimeMilliSecs: Long = DEF_PROGRESS_UPDATE_MILLISECONDS
        var progressCallbacksOnMainThread: Boolean = true
        var progressCallbackLifeCycle: Lifecycle? = null
        var filesSaveRootPath: File? = null
        var urlConnectionFactory: Factory<URLConnectionHandler>? = URLConnectionFactory()
        var databaseManager: DownloadDatabaseManager? = null
        var networkInfoProvider: NetworkInfoProvider? = null
    }
}