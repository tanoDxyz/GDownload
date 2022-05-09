package com.tanodxyz.gdownload.io

import java.io.Closeable
import java.io.IOException

interface InputResourceWrapper:Closeable {
    @Throws(IOException::class)
    fun read(buffer: ByteArray,offset:Int = 0,len:Int = buffer.size):Int
    override fun close() {}
}