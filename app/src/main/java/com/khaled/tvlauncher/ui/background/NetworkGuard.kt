package com.khaled.tvlauncher.ui.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Big downloads (the online background photos) only run on an UNMETERED connection: home Wi-Fi or Ethernet.
 * Mobile data, and also a phone hotspot (Android flags it as metered), never qualify, so they cost no data.
 */
object NetworkGuard {
    /** True only when the active network has internet and is not metered. Any doubt means false. */
    fun isUnmetered(context: Context): Boolean = runCatching {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        decide(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        )
    }.getOrDefault(false)

    /** The rule itself, separated so it can be unit-tested. */
    fun decide(hasInternet: Boolean, notMetered: Boolean, cellular: Boolean) = hasInternet && notMetered && !cellular
}
