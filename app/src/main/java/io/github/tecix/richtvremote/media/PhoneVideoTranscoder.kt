package io.github.tecix.richtvremote.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.metrics.LogSessionId
import androidx.media3.common.Format
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

/** Local files only. Transformer and its MediaCodec lifecycle are confined to the main looper. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PhoneVideoTranscoder {
    fun inspect(file: File): VideoSource? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return null
            fun int(key: String) = if (track.containsKey(key)) runCatching { track.getInteger(key) }.getOrDefault(0) else 0
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                fun meta(key: Int) = retriever.extractMetadata(key)?.toDoubleOrNull() ?: 0.0
                val fps = if (track.containsKey(MediaFormat.KEY_FRAME_RATE))
                    runCatching { track.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrElse { runCatching { track.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrDefault(0.0) } else 0.0
                return VideoSource(track.getString(MediaFormat.KEY_MIME).orEmpty(),
                    int(MediaFormat.KEY_WIDTH), int(MediaFormat.KEY_HEIGHT),
                    meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt(),
                    fps.takeIf { it > 0 } ?: meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE))
            } finally { retriever.release() }
        } finally { extractor.release() }
    }

    suspend fun transcode(context: Context, source: File, info: VideoSource, output: File,
                          progress: (Int?) -> Unit) = withContext(Dispatchers.Main.immediate) {
        require(info.width > 0 && info.height > 0) { "Cannot read the video dimensions" }
        val portrait = info.rotation % 180 != 0
        val width = if (portrait) info.height else info.width
        val height = if (portrait) info.width else info.height
        val scale = minOf(1.0, 1920.0 / width, 1080.0 / height)
        fun even(value: Double) = ((value / 2).roundToInt() * 2).coerceAtLeast(2)
        val videoEffects = listOf(
            Presentation.createForWidthAndHeight(even(width * scale), even(height * scale), Presentation.LAYOUT_SCALE_TO_FIT),
            FrameDropEffect.createDefaultFrameDropEffect(30f),
        )
        val mixing = ChannelMixingAudioProcessor().apply {
            for (channels in 1..8) {
                val coefficients = FloatArray(channels * 2) { index ->
                    val input = index / 2; val out = index % 2
                    if (channels == 1) 1f else if (input % 2 == out) 1f / ((channels + 1 - out) / 2) else 0f
                }
                putChannelMixingMatrix(ChannelMixingMatrix(channels, 2, coefficients))
            }
        }
        val sonic = SonicAudioProcessor().apply { setOutputSampleRateHz(48_000) }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
            .setEffects(Effects(listOf(mixing, sonic), videoEffects)).build()
        val encoder = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(7_000_000)
                .setEncodingProfileLevel(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel41).build())
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder()
                .setProfile(MediaCodecInfo.CodecProfileLevel.AACObjectLC).setBitrate(192_000).build())
            .setEnableFallback(false).build()
        // Cap the encoder's declared rate too; dropping frames alone retains the input format's fps.
        val encoderFactory = object : Codec.EncoderFactory by encoder {
            override fun audioNeedsEncoding() = true
            override fun videoNeedsEncoding() = true
            override fun createForVideoEncoding(format: Format, logSessionId: LogSessionId?): Codec =
                encoder.createForVideoEncoding(format.buildUpon()
                    .setFrameRate(if (format.frameRate > 0) minOf(format.frameRate, 30f) else 30f).build(), logSessionId)
        }
        val done = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC).setPortraitEncodingEnabled(true)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(Unit) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done.completeExceptionally(exportException)
                }
            }).build()
        try {
            // Transformer creates the output itself; no partial output survives cancellation or failure.
            transformer.start(item, output.path)
            val holder = ProgressHolder()
            while (!done.isCompleted) {
                check(output.parentFile!!.usableSpace > 32L * 1024 * 1024) { "Not enough phone storage to transcode this video" }
                progress(if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null)
                delay(500)
            }
            done.await()
        } finally { transformer.cancel() }
    }
}
