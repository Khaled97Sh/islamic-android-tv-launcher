package com.khaled.tvlauncher.ui.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** What kind of connection the TV has right now, for deciding how much it may download. */
enum class NetClass { NONE, METERED, UNMETERED }

/**
 * UNMETERED = home Wi-Fi / Ethernet. METERED = mobile data, or a phone hotspot (Android flags a hotspot as metered).
 * Metered connections get a much smaller daily download budget ([OnlineBackgrounds]).
 */
object NetworkGuard {
    /** Any doubt means [NetClass.NONE]. */
    fun classify(context: Context): NetClass = runCatching {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetClass.NONE
        decide(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        )
    }.getOrDefault(NetClass.NONE)

    /** The rule itself, separated so it can be unit-tested. Cellular always counts as metered. */
    fun decide(hasInternet: Boolean, notMetered: Boolean, cellular: Boolean): NetClass = when {
        !hasInternet -> NetClass.NONE
        notMetered && !cellular -> NetClass.UNMETERED
        else -> NetClass.METERED
    }
}
