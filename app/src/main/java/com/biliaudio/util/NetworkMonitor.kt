package com.biliaudio.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkStatus {
    AVAILABLE,
    UNAVAILABLE,
    LOSING,
    LOST
}

object NetworkMonitor {

    fun observe(context: Context): Flow<NetworkStatus> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkStatus.AVAILABLE)
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus.LOST)
            }

            override fun onUnavailable() {
                trySend(NetworkStatus.UNAVAILABLE)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(NetworkStatus.LOSING)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val initialStatus = if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            NetworkStatus.AVAILABLE
        } else {
            NetworkStatus.UNAVAILABLE
        }
        trySend(initialStatus)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
