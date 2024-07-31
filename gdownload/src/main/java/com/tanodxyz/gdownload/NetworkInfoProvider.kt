package com.tanodxyz.gdownload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL


//courtesy Fetch
class NetworkInfoProvider constructor(private val applicationContext: Context) {

    private val lock = Any()
    private val networkChangeListenerSet = hashSetOf<NetworkChangeListener>()
    private val connectivityManager: ConnectivityManager? =
        applicationContext.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkChangeBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            notifyNetworkChangeListeners()
        }
    }
    private var broadcastRegistered = false
    private var networkCallback: Any? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && connectivityManager != null) {
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()
            val networkCallback: ConnectivityManager.NetworkCallback =
                object : ConnectivityManager.NetworkCallback() {

                    override fun onLost(network: Network) {
                        notifyNetworkChangeListeners()
                    }

                    override fun onAvailable(network: Network) {
                        notifyNetworkChangeListeners()
                    }

                }
            this.networkCallback = networkCallback
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } else {
            try {
                applicationContext.registerReceiver(
                    networkChangeBroadcastReceiver,
                    IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                )
                broadcastRegistered = true
            } catch (e: Exception) {

            }
        }
    }

    private fun notifyNetworkChangeListeners() {
        synchronized(lock) {
            networkChangeListenerSet.iterator().forEach { listener ->
                listener.onNetworkChanged()
            }
        }
    }

    fun registerNetworkChangeListener(networkChangeListener: NetworkChangeListener) {
        synchronized(lock) {
            networkChangeListenerSet.add(networkChangeListener)
        }
    }

    fun unregisterNetworkChangeListener(networkChangeListener: NetworkChangeListener) {
        synchronized(lock) {
            networkChangeListenerSet.remove(networkChangeListener)
        }
    }

    fun unregisterAllNetworkChangeListeners() {
        synchronized(lock) {
            networkChangeListenerSet.clear()
            if (broadcastRegistered) {
                try {
                    applicationContext.unregisterReceiver(networkChangeBroadcastReceiver)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && connectivityManager != null) {
                val networkCallback = this.networkCallback
                if (networkCallback is ConnectivityManager.NetworkCallback) {
                    kotlin.runCatching { connectivityManager.unregisterNetworkCallback(networkCallback);}
                }
            }
        }
    }

    fun isOnAllowedNetwork(networkType: NetworkType): Boolean {
        if (networkType == NetworkType.WIFI_ONLY && applicationContext.isOnWiFi()) {
            return true
        }
        if (networkType == NetworkType.UNMETERED && !applicationContext.isOnMeteredConnection()) {
            return true
        }
        if (networkType == NetworkType.ALL && applicationContext.isNetworkAvailable()) {
            return true
        }
        return false
    }

    val isNetworkAvailable: Boolean
        get() {
            val url = NETWORK_CHECK_URL
            return if (url.isNotBlank()) {
                var connected = false
                try {
                    val urlConnection = URL(url)
                    val connection = urlConnection.openConnection() as HttpURLConnection
                    connection.connectTimeout = DEF_CONNECTION_TIMEOUT
                    connection.readTimeout = DEF_CONNECTION_READ_TIMEOUT
                    connection.instanceFollowRedirects = true
                    connection.useCaches = false
                    connection.defaultUseCaches = false
                    connection.connect()
                    connected = connection.responseCode != -1
                    connection.disconnect()
                } catch (e: Exception) {
                }
                connected
            } else {
                applicationContext.isNetworkAvailable()
            }
        }

}