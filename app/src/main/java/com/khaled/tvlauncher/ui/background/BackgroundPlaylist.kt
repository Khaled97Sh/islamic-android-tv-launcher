package com.khaled.tvlauncher.ui.background

import kotlin.random.Random

/** One background to show: a photo bundled in the app, or a downloaded one (by file name in the cache). */
sealed interface BgItem {
    data class Static(val index: Int) : BgItem
    data class Online(val name: String) : BgItem
}

/**
 * The order backgrounds are shown in. One cycle = every bundled photo once (shuffled), THEN every downloaded photo
 * once (shuffled); the next cycle starts again with the bundled ones. With nothing downloaded (offline, first run)
 * a cycle is just the bundled photos, exactly as before. Two identical items never follow each other.
 */
object BackgroundPlaylist {
    fun cycle(staticCount: Int, online: List<String>, previous: BgItem?, random: Random = Random.Default): List<BgItem> {
        val list = (0 until staticCount).shuffled(random).map<Int, BgItem> { BgItem.Static(it) } +
            online.shuffled(random).map { BgItem.Online(it) }
        return if (list.size > 1 && list.first() == previous) list.drop(1) + list.first() else list
    }

    /** Downloads start when only this many bundled photos of the current cycle have not been shown yet. */
    const val START_WHEN_UNSEEN = 5

    /** True while showing item [index] of [cycle] if at most [START_WHEN_UNSEEN] bundled photos are still to come. */
    fun downloadsShouldRun(cycle: List<BgItem>, index: Int): Boolean =
        cycle.drop(index + 1).count { it is BgItem.Static } <= START_WHEN_UNSEEN
}
