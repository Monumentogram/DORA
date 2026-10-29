package com.monumentogram.dora.transcript

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Read only OS state, only when the user explicitly requests Cloud recognition. */
@Suppress("ReturnCount") // Fail closed before any Cloud work.
internal fun cloudConnectionAvailable(context: Context): Boolean {
    if (
        context.checkSelfPermission(Manifest.permission.INTERNET) !=
            PackageManager.PERMISSION_GRANTED
    )
        return false
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
