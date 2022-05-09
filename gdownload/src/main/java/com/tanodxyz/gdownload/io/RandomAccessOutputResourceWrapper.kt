package com.tanodxyz.gdownload.io

import com.tanodxyz.gdownload.closeResource
import java.io.RandomAccessFile

class RandomAccessOutputResourceWrapper(private var randomAccessFile: RandomAccessFile):
    OutputResourceWrapper {
    override fun write(byteArray: ByteArray, offset: Int, len: Int) {
        randomAccessFile.write(byteArray,offset,len)
    }

    fun setWriteOffset(pointer: Long) {
        randomAccessFile.seek(pointer)
    }

    override fun flush() {
    }

    override fun close() {
        closeResource(randomAccessFile)
    }

    fun setFileLength(length:Long) {
        randomAccessFile.setLength(length)
    }
}