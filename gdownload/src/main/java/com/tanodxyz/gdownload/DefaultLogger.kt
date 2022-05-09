package com.tanodxyz.gdownload

import android.util.Log

open class DefaultLogger(loggingEnabled: Boolean, loggingTag: String) : Logger {

    constructor(tag:String = DEFAULT_LOGGING_TAG) : this(GDownload.LOGGING_ENABLED, tag)

    /** Enable or disable logging.*/
    override var enabled: Boolean = loggingEnabled

    /** Sets the logging tag name. If the tag
     * name is more than 23 characters the default
     * tag name will be used as the tag.*/
    var tag: String = loggingTag

    private val loggingTag: String
        get() {
            return if (tag.length > 23) {
                DEFAULT_LOGGING_TAG
            } else {
                tag
            }
        }

    override fun d(message: String) {
        if (enabled) {
            Log.d(loggingTag, message)
        }
    }

    override fun d(message: String, throwable: Throwable) {
        if (enabled) {
            Log.d(loggingTag, message, throwable)
        }
    }

    override fun e(message: String) {
        if (enabled) {
            Log.e(loggingTag, message)
        }
    }

    override fun e(message: String, throwable: Throwable) {
        if (enabled) {
            Log.e(loggingTag, message, throwable)
        }
    }

}