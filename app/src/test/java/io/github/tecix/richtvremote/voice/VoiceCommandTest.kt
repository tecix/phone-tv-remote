package io.github.tecix.richtvremote.voice

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceCommandTest {
    @Test fun vietnamese() {
        assertEquals(VoiceCommand(VoiceAction.PLAY, "Tom và Jerry tập 5"), VoiceCommandParser.parse("Mở Tom và Jerry tập 5"))
        assertEquals(VoiceCommand(VoiceAction.PLAY, "nhạc Sơn Tùng MTP"), VoiceCommandParser.parse("Phát nhạc Sơn Tùng MTP"))
        assertEquals(VoiceCommand(VoiceAction.SEARCH, "phim hoạt hình Disney"), VoiceCommandParser.parse("Tìm phim hoạt hình Disney"))
        assertEquals(VoiceCommand(VoiceAction.SEARCH, "Đen Vâu"), VoiceCommandParser.parse("TÌM KIẾM Đen Vâu"))
    }
    @Test fun englishAndBareQueries() {
        assertEquals(VoiceCommand(VoiceAction.PLAY, "Baby Shark"), VoiceCommandParser.parse("Play Baby Shark"))
        assertEquals(VoiceCommand(VoiceAction.SEARCH, "Tom & Jerry"), VoiceCommandParser.parse("  Search for Tom & Jerry  "))
        assertEquals(VoiceCommand(VoiceAction.PLAY, "Playground"), VoiceCommandParser.parse("Playground"))
        assertEquals(VoiceCommand(VoiceAction.PLAY, "Sơn Tùng"), VoiceCommandParser.parse("Sơn Tùng"))
    }
    @Test fun rejectsEmptyAndInvalid() {
        for (text in listOf("", "   ", "Mở", "tìm kiếm ", "play ", "abc\u0000def")) assertNull(text, VoiceCommandParser.parse(text))
    }
    @Test fun preservesUnicodePunctuationAndEncodesParameters() {
        val query = "Sơn Tùng & 'Tom' \"Jerry\" + 100% #5? / 🎵"
        val parsed = VoiceCommandParser.parse("Mở $query")!!
        assertEquals(query, parsed.query)
        val link = SmartTubeLink.build(parsed)
        val uri = Uri.parse(link)
        assertEquals("youtube", uri.scheme)
        assertEquals("search", uri.host)
        assertEquals(query, uri.getQueryParameter("query"))
        assertEquals(setOf("query", "method"), uri.queryParameterNames)
        assertTrue(link.contains("%26"))
        assertTrue(link.contains("%20"))
        assertTrue(link.contains("%22"))
        assertTrue(link.contains("%C6%A1"))
    }
    @Test fun autoplayOnlyForPlay() {
        assertNull(Uri.parse(SmartTubeLink.build(VoiceCommand(VoiceAction.SEARCH, "Baby Shark"))).getQueryParameter("method"))
        assertEquals("play", Uri.parse(SmartTubeLink.build(VoiceCommand(VoiceAction.PLAY, "Baby Shark"))).getQueryParameter("method"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidLinkRejected() {
        SmartTubeLink.build(VoiceCommand(VoiceAction.PLAY, " "))
    }
}
