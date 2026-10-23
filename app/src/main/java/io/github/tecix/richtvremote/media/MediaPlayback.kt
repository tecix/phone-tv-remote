package io.github.tecix.richtvremote.media

import android.os.SystemClock

/** A real media-session sample. Unknown duration/seek capability must never look like volume. */
data class MediaPlayback(
    val sessionId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val canSeek: Boolean,
    val speed: Float = 1f,
    val sampledAt: Long = SystemClock.elapsedRealtime(),
) {
    fun positionAt(now: Long = SystemClock.elapsedRealtime()): Long {
        val elapsed = if (playing) ((now - sampledAt).coerceAtLeast(0) * speed).toLong() else 0
        return (positionMs + elapsed).coerceIn(0, durationMs.coerceAtLeast(0))
    }
}

internal fun formatMediaTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, s)
        else "%d:%02d".format(java.util.Locale.ROOT, m, s)
}
