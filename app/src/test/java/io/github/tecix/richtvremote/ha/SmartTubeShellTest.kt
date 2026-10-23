package io.github.tecix.richtvremote.ha

import org.junit.Assert.*
import org.junit.Test

class SmartTubeShellTest {
    @Test fun quotesUriAndSelectsInstalledVariant() {
        val uri = "youtube://search?query=Sơn Tùng & 'quoted' \"double\" \$(echo INJECTED)&method=play"
        for (pkg in listOf("org.smarttube.stable", "org.smarttube.beta", "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "none")) {
            // Fake Android shell tools exercise quoting and package detection, without a TV.
            val script = "pm() { if [ \"\$2\" = \"$pkg\" ]; then echo package:/test.apk; fi; }; " +
                "am() { printf '%s\\n' \"\$@\"; }; " + smartTubeViewCommand(uri)
            val process = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
            assertTrue(output, output.contains("android.intent.action.VIEW"))
            assertTrue(output, output.lines().contains(uri))
            assertTrue(output, output.endsWith("-p\n${if (pkg == "none") "org.smarttube.stable" else pkg}\n"))
        }
    }
}
