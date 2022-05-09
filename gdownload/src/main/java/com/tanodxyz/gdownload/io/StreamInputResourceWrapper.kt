package com.tanodxyz.gdownload.io

import com.tanodxyz.gdownload.closeResource
import java.io.InputStream

open class StreamInputResourceWrapper(protected val inputStream: InputStream) :
    InputResourceWrapper {
    override fun read(buffer: ByteArray,start:Int,len:Int): Int {
        var offset = start
        while (offset < len) {
            val bytesRead = inputStream.read(buffer, offset, len - offset)
            if (bytesRead == -1) {
                break
            }
            offset += bytesRead
        }

        if(offset <= 0) { offset =-1}
        return offset
    }

    override fun close() {
        closeResource(inputStream)
    }
}