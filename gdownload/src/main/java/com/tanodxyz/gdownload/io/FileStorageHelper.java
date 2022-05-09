package com.tanodxyz.gdownload.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Utility Interface for performing CUD(create,update,delete) operations on Files.
 */
public interface FileStorageHelper {
    /**
     * Create a file with given file name in the root directory as returned by {@link FileStorageHelper#getFilesRoot()}
     * If it points to file uri, content uri or just plain File name.
     *
     * @param fileName        file name to create
     * @param replaceExisting if true will replace the existing file or else (1,....n) is added to the end of file name.
     *                        e.g. file name = "health.trace" ; new create = "health(1).trace"
     * @return The newly create File
     * @throws Exception On some platforms this method may block if permission to write is not granted
     *                   or throw exception for some other reasons.
     */
    @NonNull
    File createFile(@NonNull String fileName, boolean replaceExisting) throws Exception;

    /**
     * {@link FileStorageHelper#createFile(String, boolean)}
     */
    @NonNull
    File createFile(@NonNull File file, boolean replaceExisting) throws Exception;

    /**
     * Deleted the given file if it exists and scan it with media scanner
     *
     * @param file File to be deleted
     * @return the result based on operation success.
     */
    boolean deleteFile(@Nullable File file);

    /**
     * As aforementioned all files created through {@link FileStorageHelper} will have some root
     * path that could be set via {@link FileStorageHelper#setFilesRoot(File)} ,
     * {@link FileStorageHelper#setFilesRootToDownloadsOrFallbackToInternalDirectory()} or
     * {@link FileStorageHelper#setFilesRootToInternalDirectory()}
     *
     * @return the root path where all the files will be stored.
     */
    File getFilesRoot();

    /**
     * set Files Root.
     *
     * @param file root directory where all the files will be stored.
     * @throws IllegalArgumentException If the path is not a directory
     */
    void setFilesRoot(@NonNull File file);

    /**
     * The general contract of this method is to set Files Root Directory in the following manner.
     * if Android version >= 10 the root path will point to the path returned by
     * {@link Context#getExternalFilesDir(String)}
     * else the Files will be stored in {@link android.os.Environment#getExternalStoragePublicDirectory(String)}
     * for both of the methods the argument is Environment.DIRECTORY_DOWNLOADS.
     * if in either case the SD Card is not available the root path will point to
     * {@link Context#getFilesDir()}
     */
    void setFilesRootToDownloadsOrFallbackToInternalDirectory();

    /**
     * set files root to {@link Context#getFilesDir()}.
     */
    void setFilesRootToInternalDirectory();

    /**
     * checks whether file exists in the root folder/directory
     *
     * @param fileName fileName/path
     * @return file exists or not
     */
    boolean existsFile(@NonNull String fileName);

    /**
     * Create output resource wrapper to the provided file.
     * the result may be either {@link RandomAccessOutputResourceWrapper} or
     * {@link StreamOutputResourceWrapper} based on the parameter randomAccess
     *
     * @param file         file to which output resource wrapper will be created
     * @param randomAccess indicate the type of OutputResourceWrapper
     * @return OutputResourceWrapper
     */
    @NonNull
    OutputResourceWrapper getOutputResourceWrapper(File file, boolean randomAccess);

    /**
     * Create FileWriterOutputResourceWrapper to the specified file
     *
     * @param file to which output resource wrapper is going to be created
     * @return output resource wrapper
     */
    @NonNull
    FileWriteOutputResourceWrapper getFileWriterOutputResouceWrappter(@NonNull File file);

    /**
     * Create StreamInputResourceWrapper to the specified file
     *
     * @param file to which input resource wrapper is going to be created
     * @return input resource wrapper
     */
    @NonNull
    StreamInputResourceWrapper getStreamInputResourceWrapper(@NonNull File file);

    /**
     * Create FileReaderInputResourceWrapper to the specified file
     *
     * @param file to which input resource wrapper is going to be created
     * @return input resource wrapper
     */
    @NonNull
    FileReaderInputResourceWrapper getFileInputResourceWrapper(@NonNull File file);
}
