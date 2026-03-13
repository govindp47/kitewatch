package com.kitewatch.network.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits [Boolean] connectivity state via [isConnected].
 *
 * `true` = at least one network with [NetworkCapabilities.NET_CAPABILITY_INTERNET] is available.
 */
@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val isConnected: Flow<Boolean> =
            callbackFlow {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(true)
                        }

                        override fun onLost(network: Network) {
                            trySend(connectivityManager.hasActiveInternetCapability())
                        }

                        override fun onUnavailable() {
                            trySend(false)
                        }
                    }

                val request =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                connectivityManager.registerNetworkCallback(request, callback)

                // Emit the current state immediately on collection start.
                trySend(connectivityManager.hasActiveInternetCapability())

                awaitClose {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }.distinctUntilChanged()

        private fun ConnectivityManager.hasActiveInternetCapability(): Boolean =
            activeNetwork?.let { network ->
                getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false
    }
