package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Real-Time Network Connection Monitor that listens to device Wi-Fi and Mobile Data connectivity
 * using Android's ConnectivityManager.NetworkCallback.
 */
class NetworkConnectivityMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Continuous Flow emitting true when internet connectivity is validated, false when disconnected.
     */
    val isConnected: Flow<Boolean> = callbackFlow {
        // Emit current initial connectivity status immediately
        val initialStatus = checkCurrentConnectivity()
        trySend(initialStatus)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network onAvailable: $network")
                trySend(checkCurrentConnectivity())
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network onLost: $network")
                // When network drops, check if fallback network (e.g. mobile data) is active
                trySend(checkCurrentConnectivity())
            }

            override fun onUnavailable() {
                Log.d(TAG, "Network onUnavailable")
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasTransport = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                trySend(hasInternet && hasTransport)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
            trySend(checkCurrentConnectivity())
        }

        awaitClose {
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }.distinctUntilChanged()

    /**
     * Synchronous snapshot check for current network connectivity.
     */
    fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return hasInternet && hasTransport
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
