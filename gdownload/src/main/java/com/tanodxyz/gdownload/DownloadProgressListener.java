package com.tanodxyz.gdownload;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import kotlin.jvm.functions.Function1;


public interface DownloadProgressListener {
    /**
     * Invoked only when connection is established with remote machine.
     * Asks whether to proceed forward to download.
     * called on background thread.
     * @param contentLength of the resource we are going to download
     * @param listener callback that user should invoke.
     */
    default void shouldStartDownload(long contentLength, @NonNull Consumer<Boolean> listener) {
        listener.accept(true);
    }

    /**
     * This method will be called when connection is established after {@link DownloadProgressListener#shouldStartDownload(long, Consumer)}.
     * The executing thread will be determined by {@link Downloader}
     * @param downloadInfo necessary meta about Download
     */
    default void onConnectionEstablished(DownloadInfo downloadInfo) {
    }

    /**
     * This method will be called each time a downloadable connection is made to the remote server/machine.
     * Any connection that supports reading data is downloadable.
     * @param downloadInfo necessary meta about Download
     * @param slice it is the data range that downloadable connection have.
     */
    default void onConnection(@NonNull DownloadInfo downloadInfo, @Nullable Slice slice) {
    }

    /**
     * It is called each time when some bytes are successfully read and then written to the disk
     * @param downloadInfo necessary meta about Download
     */
    default void onDownloadProgress(DownloadInfo downloadInfo) {
    }

    /**
     * Invoked on download failure
     * @param downloadInfo necessary meta about Download
     * @param ex error message
     */
    default void onDownloadFailed(@NonNull DownloadInfo downloadInfo, @NonNull String ex) {
    }

    default void onDownloadSuccess(@NonNull DownloadInfo downloadInfo) {
    }

    /**
     * Invoked as a result of {@link Downloader#stopDownload(BiConsumer)} method.
     * @param downloadInfo necessary meta about Download
     * @param stopped whether downloading stopped
     * @param reason programmer friendly string.
     */
    default void onStop(@NonNull DownloadInfo downloadInfo, @NonNull Boolean stopped, @NonNull String reason) {
    }

    /**
     * Invoked as a result of {@link Downloader#restart(BiConsumer)} method.
     * @param downloadInfo necessary meta about Download
     * @param restarted whether download restarted
     * @param reason programmer friendly string.
     */
    default void onRestart(@NonNull DownloadInfo downloadInfo, @NonNull Boolean restarted, @NonNull String reason) {
    }
    /**
     * Invoked as a result of {@link Downloader#freezeDownload(BiConsumer)} method.
     * @param downloadInfo necessary meta about Download
     * @param paused whether downloading paused
     * @param reason programmer friendly string.
     */
    default void onPause(@NonNull DownloadInfo downloadInfo, @NonNull Boolean paused, @NonNull String reason) {
    }
    /**
     * Invoked as a result of {@link Downloader#resumeDownload(BiConsumer)}method.
     * @param downloadInfo necessary meta about Download
     * @param resume whether downloading resumed
     * @param reason programmer friendly string.
     */
    default void onResume(@NonNull DownloadInfo downloadInfo, @NonNull Boolean resume, @NonNull String reason) {
    }

    /**
     * If user requests for concurrent download and whether it was successful is determined by this contract.
     * @param downloadInfo necessary meta about Download
     * @param multiConnection indicates whether this download is multi connection.
     *                        A download is multi connection if it is requested by user specifying
     *                        connection count and server also supports partial or ranged byte requests.
     */
    default void onDownloadIsMultiConnection(@NonNull DownloadInfo downloadInfo, boolean multiConnection) {
    }

    /**
     * Invoked as a result when {@link Downloader#loadDownloadFromDatabase(int, Function1)} is called.
     * @param id download id
     * @param fetchedDownloadFromLocalDatabase fetched download form database.
     */
    default void onDownloadLoadedFromDatabase(int id, @Nullable Download fetchedDownloadFromLocalDatabase) {
    }
    /**
     * Invoked as a result when {@link Downloader#loadDownloadFromDatabase(int, Function1)} is called.
     * @param filePath download filePath
     * @param fetchedDownloadFromLocalDatabase fetched download form database.
     */
    default void onDownloadLoadedFromDatabase(String filePath, @Nullable Download fetchedDownloadFromLocalDatabase) {
    }
}
