package com.pratul.mmplayer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNamesTest {

    @Test
    fun `detects season and episode with release tags`() {
        val e = SmartNames.parseEpisode("The.Office.S03E07.1080p.WEB-DL.x264.mkv")
        assertNotNull(e)
        assertEquals("The Office", e!!.show)
        assertEquals(3, e.season)
        assertEquals(7, e.episode)
    }

    @Test
    fun `detects other episode styles`() {
        assertEquals(2 to 5, SmartNames.parseEpisode("Show Name 2x05.mp4")!!.let { it.season to it.episode })
        assertEquals(12, SmartNames.parseEpisode("Anime - Episode 12 [720p].mkv")!!.episode)
        assertEquals(4, SmartNames.parseEpisode("Lecture Ep.04.mp4")!!.episode)
        assertEquals(2, SmartNames.parseEpisode("Movie Part 2.avi")!!.episode)
        assertEquals(7, SmartNames.parseEpisode("Course 07.mp4")!!.episode)
    }

    @Test
    fun `episodes of the same show share a key`() {
        val a = SmartNames.parseEpisode("my.show.s01e01.mkv")!!
        val b = SmartNames.parseEpisode("My Show - S01E02 - Title.mkv")!!
        assertEquals(a.showKey, b.showKey)
    }

    @Test
    fun `ignores names without episodes and resolution numbers`() {
        assertNull(SmartNames.parseEpisode("Holiday video.mp4"))
        assertNull(SmartNames.parseEpisode("Concert 1080p.mp4"))
    }

    @Test
    fun `natural order sorts numbers and episodes as people expect`() {
        val sorted = listOf("Ep 10.mp4", "Ep 2.mp4", "Ep 1.mp4").sortedWith(SmartNames.naturalOrder)
        assertEquals(listOf("Ep 1.mp4", "Ep 2.mp4", "Ep 10.mp4"), sorted)
        val seasons = listOf("Show S02E01.mkv", "Show S01E10.mkv", "Show S01E02.mkv").sortedWith(SmartNames.naturalOrder)
        assertEquals(listOf("Show S01E02.mkv", "Show S01E10.mkv", "Show S02E01.mkv"), seasons)
        assertTrue(SmartNames.compareNatural("track2", "Track10") < 0)
    }
}

class SmartSearchTest {

    private fun score(query: String, text: String) = SmartSearch.score(SmartSearch.tokens(query), SmartSearch.normalize(text))

    @Test
    fun `matches prefixes separators and case`() {
        assertTrue(score("aveng", "Avengers.Endgame.2019.mkv") > 0)
        assertTrue(score("endgame aven", "Avengers.Endgame.2019.mkv") > 0)
    }

    @Test
    fun `tolerates a typo and accents`() {
        assertTrue(score("avangers", "Avengers Endgame") > 0)
        assertTrue(score("pokemon", "Pokémon Movie") > 0)
    }

    @Test
    fun `requires every word to match`() {
        assertEquals(0, score("avengers titanic", "Avengers Endgame"))
        assertEquals(0, score("xyz", "Avengers Endgame"))
    }

    @Test
    fun `exact phrase ranks above scattered words`() {
        assertTrue(score("end game", "The End Game") > score("end game", "Game of the End"))
    }
}

class NaturalSortTest {
    @Test
    fun `numbers sort by value not text`() {
        val names = listOf("Class 11", "Class 2", "Class 1", "Class 12", "Class 10", "Class 3")
        assertEquals(listOf("Class 1", "Class 2", "Class 3", "Class 10", "Class 11", "Class 12"), names.sortedNatural { it })
        assertEquals(listOf("Class 12", "Class 11", "Class 10", "Class 3", "Class 2", "Class 1"), names.sortedNatural(descending = true) { it })
    }
}
