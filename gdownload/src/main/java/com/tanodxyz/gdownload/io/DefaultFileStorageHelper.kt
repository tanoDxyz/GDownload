package com.tanodxyz.gdownload.io

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.tanodxyz.gdownload.DefaultLogger
import com.tanodxyz.gdownload.getFilePath
import com.tanodxyz.gdownload.isAndroid10Plus
import com.tanodxyz.gdownload.isUriPath
import java.io.*

class DefaultFileStorageHelper(private val appContext: Context) :
    FileStorageHelper {
    private var filesRoot = appContext.filesDir

    private val TAG = "FileSaveHelper"
    private val logger = DefaultLogger(TAG)

    private fun resolveFilesRoot(context: Context): File? {
        val externalStorageMounted =
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        return if (isAndroid10Plus()) {
            if (externalStorageMounted) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                appContext.filesDir
            }
        } else {
            if (externalStorageMounted) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                appContext.filesDir
            }
        }
    }

    override fun setFilesRoot(filesRoot: File) {
        if (filesRoot.isDirectory) {
            this@DefaultFileStorageHelper.filesRoot = filesRoot
        } else {
            throw IllegalArgumentException("only directory can be set as root")
        }
    }

    override fun setFilesRootToDownloadsOrFallbackToInternalDirectory() {
        filesRoot = resolveFilesRoot(appContext)
    }

    override fun setFilesRootToInternalDirectory() {
        filesRoot = appContext.filesDir
    }


    override fun getFilesRoot(): File? = filesRoot
    override fun createFile(fileName: String, replaceExisting: Boolean): File {
        return if (fileName.isUriPath()) {
            createFileUsingUri(fileName, replaceExisting)
        } else {
            createFile(File(filesRoot, fileName), replaceExisting)
        }
    }


    private fun createFileUsingUri(fileName: String, replaceExisting: Boolean): File {
        var fileToCreate = ""
        val uri = Uri.parse(fileName)
        when (uri.scheme) {
            "file" -> {
                fileToCreate = uri.path ?: fileName
            }
            "content" -> {
                fileToCreate = uri.getFilePath(appContext.contentResolver) ?: fileName
            }
        }
        return createFile(File(fileToCreate), replaceExisting)
    }

    override fun createFile(file: File, replaceExisting: Boolean): File {
        logger.d("Creating file $file")
        if (file.isDirectory) {
            throw IOException("File creation failed. provided file is driectory ->$file")
        }
        var fileToCreate = file
        if (fileToCreate.exists() && !replaceExisting) {
            val modifiedName = StringBuilder()
            changeFileNameForExistingFile(file, modifiedName)
            fileToCreate = File(file.parent, modifiedName.toString())
        }
        fileToCreate.parentFile?.also { if (!it.exists()) it.mkdirs() }
        logger.d("File created for $file is $fileToCreate")
        return fileToCreate
    }

    private fun changeFileNameForExistingFile(existingFile: File, modifiedName: StringBuilder) {
        // better to replace it with regex
        val blankFileExtension = existingFile.extension.isBlank()
        val extension =
            if (blankFileExtension) "" else ".${existingFile.extension}"
        val existedFileName = existingFile.name.substring(
            0,
            if (blankFileExtension) existingFile.name.count() else existingFile.name.lastIndexOf(
                existingFile.extension
            ) - 1
        )
        val cbcIdx = existedFileName.lastIndexOf(')')
        val cboIdx = if (cbcIdx > -1) existedFileName.lastIndexOf('(', cbcIdx) else -1
        val lastBFound = (cboIdx > -1 && cbcIdx > -1)
        val strBBrckts = if (lastBFound) existedFileName.substring((cboIdx + 1), cbcIdx) else ""
        var numBBrckts = try {
            strBBrckts.toInt()
        } catch (ex: Exception) {
            -1
        }
        val lastBracketsExistsInName = lastBFound && numBBrckts > -1
        val modifiedFileName = if (lastBracketsExistsInName) {
            numBBrckts += 1
            "${existedFileName.substring(0, cboIdx)}($numBBrckts)$extension"
        } else {
            numBBrckts = 1
            "${existedFileName}($numBBrckts)$extension"
        }
        val modifiedFile = File(existingFile.parent, modifiedFileName)
        if (modifiedFile.exists()) {
            changeFileNameForExistingFile(modifiedFile, modifiedName)
        } else {
            modifiedName.append(modifiedFileName)
        }
    }

    private fun scanFile(file: File, callback: (Uri?) -> Unit) {
        MediaScannerConnection.scanFile(
            appContext, arrayOf(file.absolutePath), null
        ) { path: String?, uri: Uri? ->
            callback(uri)
        }
    }


    override fun existsFile(fileName: String): Boolean {
        return File(filesRoot, fileName).exists()
    }

    override fun getOutputResourceWrapper(
        file: File,
        randomAccess: Boolean
    ): OutputResourceWrapper {
        return if (randomAccess) RandomAccessOutputResourceWrapper(
            RandomAccessFile(file, "rwd")
        ) else StreamOutputResourceWrapper(
            BufferedOutputStream(
                appContext.contentResolver.openOutputStream(Uri.fromFile(file))
            )
        )
    }

    override fun getFileWriterOutputResouceWrappter(file: File): FileWriteOutputResourceWrapper {
        return FileWriteOutputResourceWrapper(
            BufferedOutputStream(
                appContext.contentResolver.openOutputStream(Uri.fromFile(file))
            )
        )
    }

    override fun getStreamInputResourceWrapper(file: File): StreamInputResourceWrapper {
        return StreamInputResourceWrapper(
            appContext.contentResolver.openInputStream(
                Uri.fromFile(file)
            )!!

        )
    }

    override fun getFileInputResourceWrapper(file: File): FileReaderInputResourceWrapper {
        return FileReaderInputResourceWrapper(
            BufferedInputStream(
                appContext.contentResolver.openInputStream(
                    Uri.fromFile(file)
                )
            )
        )
    }


    override fun deleteFile(file: File?): Boolean {
        var deleted = false
        file?.apply {
            if (file.exists()) {
                deleted = file.delete()
                scanFile(file) {}
            }
        }

        return deleted
    }


}