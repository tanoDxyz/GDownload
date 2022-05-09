package com.tanodxyz.gdownload.io

import com.tanodxyz.gdownload.closeResource
import java.io.BufferedOutputStream

open class StreamOutputResourceWrapper(protected var bos: BufferedOutputStream) :
    OutputResourceWrapper {
    override fun write(byteArray: ByteArray, offset: Int, len: Int) {
        bos.write(byteArray,offset,len)
    }
    override fun flush() {
        bos.flush()
    }

    override fun close() {
        closeResource(bos)
    }
}