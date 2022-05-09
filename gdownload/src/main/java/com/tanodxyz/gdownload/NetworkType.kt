package com.tanodxyz.gdownload

/**
 * Enumeration which contains the different network types
 * a download can have and be downloaded over.
 * //courtesy fetch
 * */
enum class NetworkType(val value: Int) {

    /** Indicates that a download can be downloaded over mobile or wifi networks.*/
    ALL(0),

    /** Indicates that a download can be downloaded only on wifi networks.*/
    WIFI_ONLY(1),

    /** Indicates that a download can be downloaded only on an unmetered connection.*/
    UNMETERED(2);

    companion object {
        @JvmStatic
        fun valueOf(value: Int): NetworkType {
            return when (value) {
                0 -> ALL
                1 -> WIFI_ONLY
                2 -> UNMETERED
                else -> ALL
            }
        }
        @JvmStatic
        fun valueOf(networkType: NetworkType):Int {
            return when(networkType) {
                ALL -> 0
                WIFI_ONLY -> 1
                UNMETERED -> 2
            }
        }
    }
}