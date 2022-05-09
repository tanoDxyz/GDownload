package com.tanodxyz.gdownload

import java.io.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * It is the chunk of downloadable data.
 */
data class Slice(
    val id: Int,
    val startByte: Long = 0,
    val endByte: Long = 0,
    var downloaded: AtomicLong = AtomicLong(0),
    var downloadComplete: AtomicBoolean = AtomicBoolean(false)
) : Serializable {
    constructor(
        id: Int, startByte: Long = 0,
        endByte: Long = 0L,
        downloaded: Long = 0L,
        downloadComplete: Boolean = false
    ) : this(
        id, startByte, endByte,
        AtomicLong(downloaded), AtomicBoolean(downloadComplete)
    )
    fun copy():Slice {
        return Slice(id,startByte,endByte,downloaded,downloadComplete)
    }

    fun isSame(otherSlice: Slice):Boolean {
        return otherSlice.id == id && otherSlice.startByte == startByte && otherSlice.endByte == endByte
    }
}


