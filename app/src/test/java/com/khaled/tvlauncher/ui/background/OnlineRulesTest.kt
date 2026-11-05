package com.khaled.tvlauncher.ui.background

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRulesTest {
    private fun c(title: String, cats: List<String> = listOf("Quality images"), desc: String = "", w: Int = 5000, h: Int = 3300, mime: String = "image/jpeg") =
        Candidate(title, w, h, mime, cats, desc)

    @Test fun acceptsAGoodMosquePhoto() = assertNull(OnlineRules.reject(c("Blue Mosque Courtyard at Dusk.jpg")))
    @Test fun acceptsAlhambraEvenWithoutTheWordMosque() = assertNull(OnlineRules.reject(c("Alcazaba, Alhambra, Granada, Spain.jpg")))

    @Test fun rejectsBelow4K() = assertEquals("below 4K", OnlineRules.reject(c("Blue Mosque.jpg", w = 3000, h = 2000)))
    @Test fun rejectsPortrait() = assertEquals("portrait", OnlineRules.reject(c("Blue Mosque minaret.jpg", w = 3900, h = 6000)))
    @Test fun rejectsNonJpeg() = assertEquals("not jpeg", OnlineRules.reject(c("Mosque.png", mime = "image/png")))
    @Test fun rejectsWithoutQualityMark() = assertEquals("no quality mark", OnlineRules.reject(c("Blue Mosque.jpg", cats = listOf("Mosques in Turkey"))))

    @Test fun rejectsIranIraqShia() {
        assertNotNull(OnlineRules.reject(c("Agha Bozorg Mosque, Kashan, Iran.jpg")))
        assertNotNull(OnlineRules.reject(c("Dome of a mosque.jpg", cats = listOf("Quality images", "Mosques in Isfahan"))))
        assertNotNull(OnlineRules.reject(c("Mosque in Karbala.jpg")))
        assertNotNull(OnlineRules.reject(c("Mosque dome.jpg", desc = "Shia shrine of Imam Reza")))
    }

    @Test fun rejectsPeople() {
        assertNotNull(OnlineRules.reject(c("Worshippers inside the mosque.jpg")))
        assertNotNull(OnlineRules.reject(c("Mosque courtyard.jpg", desc = "Tourists walking in the courtyard")))
        assertNotNull(OnlineRules.reject(c("Mosque dome.jpg", cats = listOf("Quality images", "Friday prayer in mosques"))))
    }

    @Test fun rejectsNonMosqueSubjects() = assertEquals("subject not a mosque / Islamic architecture", OnlineRules.reject(c("Sunset over the sea.jpg")))

    @Test fun realCommonsResultsAreSortedSensibly() {
        val root = JSONObject(javaClass.classLoader!!.getResourceAsStream("commons_search_sample.json")!!.bufferedReader().readText())
        val page = OnlineSearch.parse(root)
        assertEquals(477, page.candidates.size)
        assertEquals(50, page.nextOffset)
        val accepted = page.candidates.filter { OnlineRules.reject(it) == null }
        println("real sample: ${accepted.size} accepted of ${page.candidates.size}")
        assertTrue(accepted.size in 10..100)
        assertTrue(accepted.none { Regex("iran|iraq|isfahan|kashan|karbala|shia", RegexOption.IGNORE_CASE).containsMatchIn(it.title) })
        assertTrue(accepted.any { it.title.contains("Alhambra") })
        assertTrue(accepted.all { it.width >= 3840 && it.height >= 2160 })
    }

    @Test fun imageUrlAsksForTheSized3840Version() =
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Blue_Mosque_%C3%A9.jpg?width=3840", OnlineSearch.imageUrl("Blue Mosque é.jpg"))
}
