package com.tanodxyz.gdownload

//courtesy [fetch]
interface Logger {

    var enabled: Boolean

    /** Log debug information.
     * @param message message
     * */
    fun d(message: String)

    /** Log debug information with throwable.
     * @param message message
     * @param throwable throwable
     * */
    fun d(message: String, throwable: Throwable)

    /** Log error information.
     * @param message message
     * */
    fun e(message: String)

    /** Log error information with throwable.
     * @param message message
     * @param throwable throwable
     * */
    fun e(message: String, throwable: Throwable)

}