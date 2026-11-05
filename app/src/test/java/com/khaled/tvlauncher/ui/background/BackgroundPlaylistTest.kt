package com.khaled.tvlauncher.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackgroundPlaylistTest {
    @Test fun bundledFirstThenDownloaded() {
        val c = BackgroundPlaylist.cycle(5, listOf("a.jpg", "b.jpg", "c.jpg"), null, Random(1))
        assertEquals(8, c.size)
        assertTrue(c.take(5).all { it is BgItem.Static })
        assertEquals(setOf(0, 1, 2, 3, 4), c.take(5).map { (it as BgItem.Static).index }.toSet())
        assertTrue(c.drop(5).all { it is BgItem.Online })
        assertEquals(setOf("a.jpg", "b.jpg", "c.jpg"), c.drop(5).map { (it as BgItem.Online).name }.toSet())
    }

    @Test fun nothingDownloadedMeansOnlyBundled() {
        val c = BackgroundPlaylist.cycle(4, emptyList(), null, Random(2))
        assertEquals(4, c.size); assertTrue(c.all { it is BgItem.Static })
    }

    @Test fun noRepeatAcrossCycles() {
        repeat(200) { seed ->
            val prev = BgItem.Static(0)
            val c = BackgroundPlaylist.cycle(3, emptyList(), prev, Random(seed))
            assertNotEquals(prev, c.first())
            assertEquals(3, c.size)
        }
    }

    @Test fun emptyWhenNothingAtAll() = assertTrue(BackgroundPlaylist.cycle(0, emptyList(), null).isEmpty())

    @Test fun cropIsCentred16by9() {
        assertEquals(Crop(0, 247, 3840, 2160), cropTo16by9(3840, 2654))   // taller than 16:9: trim top and bottom
        assertEquals(Crop(0, 0, 3840, 2160), cropTo16by9(3840, 2160))
        assertEquals(Crop(120, 0, 3840, 2160), cropTo16by9(4080, 2160))   // wider than 16:9: trim the sides
    }

    @Test fun sampleKeepsAtLeast1920Wide() {
        assertEquals(2, sampleFor(3840)); assertEquals(1, sampleFor(1920)); assertEquals(4, sampleFor(7680)); assertEquals(1, sampleFor(1000))
    }

    @Test fun downloadsStartWhenFiveBundledPhotosAreLeft() {
        val c = BackgroundPlaylist.cycle(50, listOf("a.jpg"), null, Random(5))
        // showing item i: the bundled photos still to come are 49 - i
        assertEquals(false, BackgroundPlaylist.downloadsShouldRun(c, 0))      // 49 unseen
        assertEquals(false, BackgroundPlaylist.downloadsShouldRun(c, 43))     // 6 unseen
        assertEquals(true, BackgroundPlaylist.downloadsShouldRun(c, 44))      // 5 unseen -> start
        assertEquals(true, BackgroundPlaylist.downloadsShouldRun(c, 49))      // 0 unseen
        assertEquals(true, BackgroundPlaylist.downloadsShouldRun(c, 50))      // showing a downloaded photo
    }

    @Test fun fewBundledPhotosStartImmediately() =
        assertEquals(true, BackgroundPlaylist.downloadsShouldRun(BackgroundPlaylist.cycle(4, emptyList(), null, Random(1)), 0))
}
