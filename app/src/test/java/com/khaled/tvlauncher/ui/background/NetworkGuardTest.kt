package com.khaled.tvlauncher.ui.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGuardTest {
    @Test fun homeWifiIsAllowed() = assertTrue(NetworkGuard.decide(hasInternet = true, notMetered = true, cellular = false))
    @Test fun phoneHotspotIsMeteredSoBlocked() = assertFalse(NetworkGuard.decide(hasInternet = true, notMetered = false, cellular = false))
    @Test fun mobileDataIsBlocked() = assertFalse(NetworkGuard.decide(hasInternet = true, notMetered = false, cellular = true))
    @Test fun cellularIsBlockedEvenIfFlaggedUnmetered() = assertFalse(NetworkGuard.decide(hasInternet = true, notMetered = true, cellular = true))
    @Test fun noInternetIsBlocked() = assertFalse(NetworkGuard.decide(hasInternet = false, notMetered = true, cellular = false))
}
