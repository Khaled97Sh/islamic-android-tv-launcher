package com.khaled.tvlauncher.ui.background

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkGuardTest {
    @Test fun homeWifiIsUnmetered() = assertEquals(NetClass.UNMETERED, NetworkGuard.decide(hasInternet = true, notMetered = true, cellular = false))
    @Test fun phoneHotspotIsMetered() = assertEquals(NetClass.METERED, NetworkGuard.decide(hasInternet = true, notMetered = false, cellular = false))
    @Test fun mobileDataIsMetered() = assertEquals(NetClass.METERED, NetworkGuard.decide(hasInternet = true, notMetered = false, cellular = true))
    @Test fun cellularCountsAsMeteredEvenIfFlaggedNotMetered() = assertEquals(NetClass.METERED, NetworkGuard.decide(hasInternet = true, notMetered = true, cellular = true))
    @Test fun noInternetIsNone() = assertEquals(NetClass.NONE, NetworkGuard.decide(hasInternet = false, notMetered = true, cellular = false))
}
