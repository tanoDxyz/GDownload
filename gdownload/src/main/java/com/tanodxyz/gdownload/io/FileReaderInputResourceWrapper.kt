package com.tanodxyz.gdownload.io

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * An extension of [StreamInputResourceWrapper] that will read the lines as a list from [BufferedInputStream]
 */
class FileReaderInputResourceWrapper(bufferedInputStream: BufferedInputStream) :
    StreamInputResourceWrapper(bufferedInputStream) {
    fun readTotally():List<String>? {
        var linesList:List<String>? = null
        BufferedReader(InputStreamReader(super.inputStream)).useLines {
            linesList = it.toList()
        }
        return linesList
    }
}