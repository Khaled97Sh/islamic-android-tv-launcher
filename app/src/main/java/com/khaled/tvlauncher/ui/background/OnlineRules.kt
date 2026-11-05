package com.khaled.tvlauncher.ui.background

/** What Wikimedia Commons tells us about one candidate image. */
class Candidate(val title: String, val width: Int, val height: Int, val mime: String, val categories: List<String>, val description: String)

/**
 * The rules an online photo must pass before it is downloaded. They mirror the rules used for the bundled photos:
 * at least 4K, a mosque / Islamic-architecture subject, no people, nothing from Shia / Iran / Iraq sites, and only
 * well-photographed pictures (Commons' own Quality / Featured / Valued marks). Pure text rules, unit-tested with real
 * Commons results. A last check on the downloaded pixels (faces) lives in [FaceCheck].
 */
object OnlineRules {
    const val MIN_WIDTH = 3840
    const val MIN_HEIGHT = 2160

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** Places and topics that are not wanted: Shia / Iran / Iraq sites, graves and shrines, other religions, non-photos. */
    private val BLOCK = rx("""\b(iran\w*|iraq\w*|isfahan|esfahan|ispahan|mashhad|karbala|najaf|qom|shiraz|tehran|kashan|yazd|tabriz|persian|baghdad|kufa|samarra|kadhimiya|kazimiyya|shia|shi'?ite|shi'?a|imam\s+(ali|reza|hussein|husayn|hossein)|husayn|hussain|hosseiniyeh|ashura|arbaeen|shrine|mausoleum|tomb|grave|cemetery|funeral|vakil|nasir[- ]al[- ]mulk|sheikh\s+lotf\w*|jameh|masjed|zoroastrian|ayatollah|khomeini|khamenei|soleimani|church|cathedral|synagogue|temple|hindu|buddh\w*|christian|jewish|war|military|soldier|protest|rally|election|flag|map|logo|coat of arms|diagram|poster|portrait)\b""")

    /** Words that say a person is in the picture. */
    private val PEOPLE_TEXT = rx("""\b(people|person|persons|man|men|woman|women|child|children|boy|boys|girl|girls|crowd|crowds|worshipp?ers?|pilgrims?|visitors?|tourists?|prayers?|praying|pray|congregation|family|selfie|group|muslims|devotees|students|imam|iftar|ramadan|hajj|umrah|eid|friday prayer|tarawih|wedding|ceremony|festival|market|bazaar|street|shopping|vendor|seller|musician|dancer|crowded|queue)\b""")
    private val PEOPLE_CAT = rx("""(people|persons|\bmen\b|women|children|crowd|worship|pilgrim|hajj|umrah|prayer|praying|tourists|visitors|portrait|ceremon|festival|ramadan|iftar|\beid\b)""")

    /** The subject must be a mosque or Islamic architecture. */
    private val SUBJECT = rx("""(mosque|masjid|mezquita|moschee|mosquée|\bcami\b|camii|alhambra|generalife|alcazar|alcázar|minaret|mihrab|mihrap|muqarnas|islamic|dome of the rock|kaaba|haram|nabaw|aqsa|madrasa|medrese|registan|bibi.?khanym|ulugh|kalyan|sheikh zayed|hassan ii|selimiye|s[uü]leymaniye|sultan ahmed|blue mosque)""")

    /** Commons' own quality marks: Quality image, Featured picture, Valued image. */
    fun hasQualityMark(categories: List<String>) = categories.any {
        it == "Quality images" || it == "Featured pictures on Wikimedia Commons" || it == "Valued images" ||
            it.startsWith("Featured pictures of") || it.startsWith("Quality images of") || it.startsWith("Valued images of")
    }

    /** null = accepted, otherwise the reason it was rejected (for the log). */
    fun reject(c: Candidate): String? {
        if (c.mime != "image/jpeg") return "not jpeg"
        if (c.width < MIN_WIDTH || c.height < MIN_HEIGHT) return "below 4K"
        if (c.width < c.height) return "portrait"
        if (!hasQualityMark(c.categories)) return "no quality mark"
        val text = (listOf(c.title, c.description) + c.categories).joinToString(" ")
        BLOCK.find(text)?.let { return "blocked: ${it.value}" }
        PEOPLE_TEXT.find(c.title + " " + c.description)?.let { return "people: ${it.value}" }
        c.categories.firstOrNull { PEOPLE_CAT.containsMatchIn(it) }?.let { return "people category: $it" }
        if (!SUBJECT.containsMatchIn(text)) return "subject not a mosque / Islamic architecture"
        return null
    }
}
