package com.tanodxyz.gdownload;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import java.util.List;


/**
 * Sometimes it is desired to group downloads and treat them like a single entity.
 * A {@link Group} is the group of downloads.
 * Each {@link Group} has a looper thread that runs a cycle after time T and it has the
 * capacity to run N concurrent downloads.
 * Although! there is no restriction to enqueuing downloads.
 * Downloads in {@link Group} are called {@link GroupImpl.GroupDownload}s
 * Each group has a specific network type.
 * {@link Group#stop()} is used to stop group loop thread.
 * {@link Group#start()} is used to start group loop thread.
 */
public interface Group {
    /**
     * Start a group loop thread.
     */
    void start();

    /**
     * Stop a group loop thread.
     */
    void stop();

    /**
     * Add a download to group. it won't be started automatically unless {@link Group#startDownload(long)}
     * is called.
     *
     * @param download download meta.
     * @param listener progress listener
     * @return id which is used to query download inside {@link Group}
     */
    Long add(@NonNull Download download, @Nullable DownloadProgressListener listener);

    /**
     * Add multiple downloads with listeners. Downloads won't be started unless {@link Group#startDownload(long)} or
     * {@link Group#startDownloads(long...)} is called.
     *
     * @param downloadsListenerPair list of Downloads and listener pairs
     * @param idsCallback           invoked on main thread - contains list of added download ids
     */
    void addAllWithListeners(@NonNull List<kotlin.Pair<Download, DownloadProgressListener>> downloadsListenerPair, @Nullable Consumer<List<Long>> idsCallback);

    /**
     * @param downloads   list of downloads
     * @param idsCallback idsCallback invoked on main thread - contains list of added download ids
     * @see Group#addAllWithListeners(List, Consumer)
     */
    void addAll(@NonNull List<Download> downloads, @Nullable Consumer<List<Long>> idsCallback);

    /**
     * @param url      url pointing to remote resource
     * @param name     file name
     * @param listener progress listener
     * @return download id
     * @see Group#add(Download, DownloadProgressListener)
     */
    Long add(@NonNull String url, @NonNull String name, @Nullable DownloadProgressListener listener);

    /**
     * This method will start the download if it is already added.
     *
     * @param id download id
     */
    void startDownload(long id);

    /**
     * This method will restart download if it is either {@link GroupImpl.GroupDownloadStates#STOPPED},
     * {@link GroupImpl.GroupDownloadStates#FAILURE} or {@link GroupImpl.GroupDownloadStates#SUCCESS}.
     * If download is not yet started and in {@link GroupImpl.GroupDownloadStates#ENQUEUED} state. calling
     * this method won't make any difference
     *
     * @param id download id
     */
    void restartDownload(long id);

    /**
     * Start download or downloads.
     *
     * @param ids download or downloads list
     * @see Group#startDownload(long)
     */
    void startDownloads(long... ids);

    /**
     * Start download or downloads.
     *
     * @param ids download id list
     */
    void startDownloads(@NonNull List<Long> ids);

    /**
     * Stop download if it is already running.
     *
     * @param id download id
     */
    void stopDownload(long id);

    /**
     * Calling this will stop the running downloads.
     * Downloads that are not running and just enqueued will not run.
     * Such downloads can be started later via {@link Group#resumeDownload(long)} or
     * {@link Group#restartDownload(long)} or just simply calling {@link Group#startDownload(long)}
     */
    void stopAll();

    /**
     * This method will freeze the running download.
     * Freezing Download that is not started will prevent it from starting so it is necessary to
     * call any of these to start it back.
     *
     * @param id download id
     * @see Group#resumeDownload(long)
     * @see Group#restartDownload(long)
     * @see Group#startDownload(long)
     */
    void freezeDownload(long id);

    /**
     * This will freeze/pause all the running downloads.
     *
     * @see Group#freezeDownload(long)
     */
    void freezeAll();

    /**
     * This will resume download if was paused/freeze previously.
     * Resuming download that is just added or Enqueued will start normally.
     *
     * @param id download id.
     */
    void resumeDownload(long id);

    /**
     * This will resume all the paused/freeze downloads.
     *
     * @see Group#resumeDownload(long)
     */
    void resumeAll();

    /**
     * This will shut down group loop thread and stop all the running downloads.
     */
    void shutDown();

    /**
     * If there are multiple downloads in the {@link Group} and some are running and others are
     * waiting.
     * this method will attach the {@link DownloadProgressListener} to the specific download if it is
     * running or idle.
     * if the {@link DownloadProgressListener} is added to the running download. api client may lose
     * some callbacks depending upon the state of {@link Download}
     *
     * @param id       download id
     * @param listener progress listener
     */
    void attachProgressListener(long id, @NonNull DownloadProgressListener listener);

    /**
     * Remove progress listener from Running/Idle download.
     *
     * @param id download id.
     */
    void removeProgressListener(long id);

    /**
     * This will add a listener to the registered list of listeners.
     *
     * @param groupListener group callbacks listener
     */
    void addGroupProgressListener(@NonNull GroupListener groupListener);

    /**
     * Remove the listener from registered list of listeners.
     *
     * @param groupListener group callbacks listener
     */
    void removeGroupProgressListener(@NonNull GroupListener groupListener);

    /**
     * This method removes the {@link Download} objects from internal Lists.
     * it does not  care if the {@link Download}s are running or idle.
     * Moreover, passing {@link GroupImpl.GroupDownloadStates#RUNNING} will cause unexpected behaviour.
     *
     * @param state it is the state for which we will be removing all the {@link Download} objects from
     *              internal queues. in order words remove all the downloads whose state matches the parameter value.
     */
    void purge(GroupImpl.GroupDownloadStates state);

    /**
     * @return the list of downloads whose queue id matches this group.
     */
    @WorkerThread
    List<Download> getAllGroupDownloadsFromDatabase();

    /**
     * @return the list of all incomplete downloads whose queue id matches this group.
     */
    @WorkerThread
    List<Download> getAllInCompleteDownloadsFromDatabase();

    /**
     * This method will load all the downloads that belong to this group and add it to this group.
     * @param listener will be called on main thread and indicates number of downloads added to the group.
     */
    void loadDownloadsFromDatabase(@Nullable Consumer<Integer> listener);

    /**
     * @return whether all the running {@link Downloader}s are busy in processing downloads.
     */
    boolean isBusy();

    /**
     * @return if this group's {@link Group#shutDown()} is called
     */
    boolean isTerminated();

    /**
     * @return group state
     */
    GroupState getState();

    /**
     * @return group id and name pair
     */
    Pair<Long, String> getId();
}
