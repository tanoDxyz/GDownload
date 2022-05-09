package com.tanodxyz.gdownload.io

import java.io.BufferedOutputStream
import java.nio.charset.Charset

/**
 * An extension of [StreamOutputResourceWrapper] that will write the provided list in the form
 * of lines.
 */
class FileWriteOutputResourceWrapper(outputStream: BufferedOutputStream) :
    StreamOutputResourceWrapper(
        outputStream
    ) {
    /**
     * write [data] to the file in the form of lines.
     */
    fun write(data: List<Any>) {
        super.bos.bufferedWriter(Charset.forName("utf-8")).apply {
            data.forEach { lineData ->
                write(lineData.toString())
                newLine()
            }
        }
    }
}