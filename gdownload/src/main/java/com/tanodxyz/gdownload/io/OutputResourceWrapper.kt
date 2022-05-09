package com.tanodxyz.gdownload.io

import java.io.Closeable
import java.io.IOException

interface OutputResourceWrapper:Closeable {
     @Throws(IOException::class)
     fun write(byteArray: ByteArray,offset:Int = 0,len:Int = byteArray.size)
     @Throws(IOException::class)
     fun flush()
     override fun close() {
     }
}