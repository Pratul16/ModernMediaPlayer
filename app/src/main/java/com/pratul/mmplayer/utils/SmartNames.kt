package com.pratul.mmplayer.utils

import java.util.Locale

/** A file name recognised as one episode of a series. [showKey] groups episodes of the same show. */
data class EpisodeInfo(
    val show: String,
    val showKey: String,
    val season: Int?,
    val episode: Int,
) {
    val label: String
        get() = if (season != null) "S$season · E$episode" else "Episode $episode"
}

/**
 * Understands media file names: detects series episodes and compares names the way people expect
 * ("Episode 2" before "Episode 10"). Pure functions, safe to call from any thread.
 */
object SmartNames {

    // Release tags that would otherwise be mistaken for episode numbers or show-name words.
    private val NOISE = Regex(
        "(?i)\\b(2160p|1440p|1080p|720p|576p|480p|360p|4k|8k|uhd|hdr10?|dv|x26[45]|h\\.?26[45]|hevc|avc|av1|10bit|8bit|" +
            "web-?dl|web-?rip|webrip|blu-?ray|brrip|bdrip|hdrip|dvdrip|hdtv|amzn|nf|dsnp|hmax|" +
            "aac(2\\.0|5\\.1)?|ac3|eac3|ddp?(5\\.1|2\\.0)?|dts|atmos|truehd|esub|multi|dual|hindi|english|proper|repack)\\b",
    )
    private val BRACKETS = Regex("[\\[(\\{][^\\])\\}]*[\\])\\}]")
    private val SEPARATORS = Regex("[._]+")
    private val SPACES = Regex("\\s+")

    private val PATTERNS = listOf(
        // Show.Name.S01E02 / Show Name - s1e2 / S01 E02
        Regex("(?i)^(.*?)[\\s\\-]*\\bs(\\d{1,2})[\\s\\-]*e(\\d{1,3})\\b") to Kind.SEASON_EPISODE,
        // Show Name 1x02
        Regex("(?i)^(.*?)[\\s\\-]*\\b(\\d{1,2})x(\\d{1,3})\\b") to Kind.SEASON_EPISODE,
        // Show Name Episode 5 / Ep 5 / Ep.05 / E05
        Regex("(?i)^(.*?)[\\s\\-]*\\b(?:episode|ep|e)[\\s\\-]*(\\d{1,3})\\b") to Kind.EPISODE,
        // Movie Part 2 / Pt 2
        Regex("(?i)^(.*?)[\\s\\-]*\\b(?:part|pt)[\\s\\-]*(\\d{1,2})\\b") to Kind.EPISODE,
        // Show Name 07 (a plain trailing number)
        Regex("^(.*?)[\\s\\-]+(\\d{1,3})$") to Kind.EPISODE,
    )

    private enum class Kind { SEASON_EPISODE, EPISODE }

    /** Base name without extension, release tags or separators: "My.Show.S01E02.1080p.mkv" -> "My Show S01E02". */
    fun clean(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        return base
            .replace(BRACKETS, " ")
            .replace(SEPARATORS, " ")
            .replace(NOISE, " ")
            .replace(SPACES, " ")
            .trim(' ', '-')
    }

    fun parseEpisode(fileName: String): EpisodeInfo? {
        val name = clean(fileName)
        for ((regex, kind) in PATTERNS) {
            val match = regex.find(name) ?: continue
            val show = match.groupValues[1].trim(' ', '-')
            if (show.isBlank()) continue
            val (season, episode) = when (kind) {
                Kind.SEASON_EPISODE -> match.groupValues[2].toInt() to match.groupValues[3].toInt()
                Kind.EPISODE -> null to match.groupValues[2].toInt()
            }
            if (episode == 0 && season == null) continue
            return EpisodeInfo(
                show = show.toDisplayCase(),
                showKey = show.lowercase(Locale.ROOT).replace(SPACES, " "),
                season = season,
                episode = episode,
            )
        }
        return null
    }

    /**
     * Orders names the way people do: by season/episode when both are episodes of the same show,
     * otherwise comparing digit runs as numbers ("Track 2" < "Track 10"), ignoring case.
     */
    val naturalOrder: Comparator<String> = Comparator { a, b ->
        val ea = parseEpisode(a)
        val eb = parseEpisode(b)
        if (ea != null && eb != null && ea.showKey == eb.showKey) {
            val bySeason = (ea.season ?: 0).compareTo(eb.season ?: 0)
            if (bySeason != 0) return@Comparator bySeason
            val byEpisode = ea.episode.compareTo(eb.episode)
            if (byEpisode != 0) return@Comparator byEpisode
        }
        compareNatural(a, b)
    }

    fun compareNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun String.toDisplayCase(): String =
        split(' ').joinToString(" ") { word ->
            if (word.length > 1 && word.all { it.isUpperCase() || it.isDigit() }) word // keep acronyms
            else word.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
}

/**
 * Natural sort for large lists: each name is analysed once (not on every comparison), so sorting
 * thousands of files stays instant. "Class 2" < "Class 11"; episodes by season and number.
 */
fun <T> List<T>.sortedNatural(descending: Boolean = false, name: (T) -> String): List<T> {
    val keyed = map { item -> val n = name(item); Triple(item, n, SmartNames.parseEpisode(n)) }
    val comparator = Comparator<Triple<T, String, EpisodeInfo?>> { a, b ->
        val ea = a.third
        val eb = b.third
        if (ea != null && eb != null && ea.showKey == eb.showKey) {
            val bySeason = (ea.season ?: 0).compareTo(eb.season ?: 0)
            if (bySeason != 0) return@Comparator bySeason
            val byEpisode = ea.episode.compareTo(eb.episode)
            if (byEpisode != 0) return@Comparator byEpisode
        }
        SmartNames.compareNatural(a.second, b.second)
    }
    return keyed.sortedWith(if (descending) comparator.reversed() else comparator).map { it.first }
}
