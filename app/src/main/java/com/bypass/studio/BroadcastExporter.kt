package com.bypass.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

data class BroadcastLine(
    val speaker: String,
    val text: String,
    val lang: String = "bn",
    val persona: String = "Emma",
    val speed: Float = 1.0f
)

private data class MixResult(val pcm: ShortArray, val frames: List<Int>)

object BroadcastExporter {
    private const val SAMPLE_RATE = 44100
    private const val GAP_FRAMES = SAMPLE_RATE / 5

    fun export(
        context: Context,
        voice: VoiceEngine,
        lines: List<BroadcastLine>,
        out: File,
        ratio: String,
        allVoices: Boolean,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit
    ) {
        if (lines.isEmpty()) {
            onLog("No broadcast script")
            return
        }
        val dir = File(context.codeCacheDir, "broadcast/${System.currentTimeMillis()}").apply { mkdirs() }
        val audioFiles = lines.mapIndexed { i, _ -> File(dir, "voice_$i.wav") }
        val results = IntArray(lines.size)
        val latch = CountDownLatch(lines.size)
        try {
            onLog("Synthesizing ${lines.size} voice lines")
            lines.forEachIndexed { i, line ->
                voice.synthesizeLineToFile(
                    line.text,
                    audioFiles[i],
                    line.lang,
                    line.persona,
                    line.speed
                ) { code ->
                    results[i] = code
                    latch.countDown()
                }
            }
            if (!latch.await(120, TimeUnit.SECONDS)) {
                onLog("TTS timeout")
                return
            }
            if (results.any { it < 0 }) {
                onLog("TTS failed on one or more lines")
                return
            }
            onProgress(20)

            val parts = audioFiles.mapIndexed { i, file ->
                val pcm = decodePcm(file)
                onLog("Decoded line ${i + 1}: ${pcm.size / 2} frames")
                pcm
            }
            onProgress(35)

            val mixed = mix(parts, allVoices)
            if (mixed.pcm.isEmpty()) {
                onLog("No decoded audio")
                return
            }

            val audioOnly = File(dir, "audio.m4a")
            encodeAudioM4a(mixed.pcm, audioOnly, onLog)
            onProgress(55)

            val durations = mixed.frames.map { frames ->
                ((frames + GAP_FRAMES) * 1000L / SAMPLE_RATE).coerceIn(1200L, 12000L).toInt()
            }
            val (w, h) = if (ratio == "16:9") 1280 to 720 else 720 to 1280
            val videoOnly = File(dir, "video.mp4")
            writeVideoMp4(lines, durations, w, h, videoOnly, onProgress, onLog)

            if (audioOnly.exists() && audioOnly.length() > 1024) {
                onLog("Muxing audio + video")
                muxTogether(videoOnly, audioOnly, out, onLog)
            } else {
                onLog("Audio unavailable, exporting video only")
                videoOnly.copyTo(out, overwrite = true)
            }
            onProgress(100)
            onLog("Broadcast exported: ${out.name}")
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun mix(parts: List<ShortArray>, overlay: Boolean): MixResult {
        val frames = parts.map { it.size / 2 }
        if (overlay) {
            val stagger = SAMPLE_RATE * 2 / 3
            val totalFrames = (frames.indices).maxOf { i -> frames[i] + i * stagger } + GAP_FRAMES
            val out = ShortArray(totalFrames * 2)
            parts.forEachIndexed { i, part ->
                val offset = i * stagger
                for (f in 0 until frames[i]) {
                    val idx = (offset + f) * 2
                    if (idx + 1 >= out.size) break
                    val left = part[f * 2].toInt()
                    val right = part[f * 2 + 1].toInt()
                    out[idx] = ((out[idx].toInt() + left) / 2).coerceIn(-32768, 32767).toShort()
                    out[idx + 1] = ((out[idx + 1].toInt() + right) / 2).coerceIn(-32768, 32767).toShort()
                }
            }
            return MixResult(out, frames)
        }

        var totalFrames = 0
        frames.forEach { totalFrames += it + GAP_FRAMES }
        val out = ShortArray(totalFrames * 2)
        var cursor = 0
        parts.forEachIndexed { i, part ->
            for (f in 0 until frames[i]) {
                val idx = (cursor + f) * 2
                if (idx + 1 >= out.size) break
                out[idx] = part[f * 2]
                out[idx + 1] = part[f * 2 + 1]
            }
            cursor += frames[i] + GAP_FRAMES
        }
        return MixResult(out, frames)
    }

    private fun decodePcm(file: File): ShortArray {
        if (!file.exists() || file.length() < 64) return ShortArray(0)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var out = ShortArray(0)
        var baseFrames = 0L
        try {
            extractor.setDataSource(file.path)
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    break
                }
            }
            if (audioTrack < 0) return ShortArray(0)
            val format = extractor.getTrackFormat(audioTrack)
            extractor.selectTrack(audioTrack)
            var srcRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SAMPLE_RATE
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputDone = false
            while (!inputEos || !outputDone) {
                if (!inputEos) {
                    val inId = codec.dequeueInputBuffer(10000)
                    if (inId >= 0) {
                        val buf = codec.getInputBuffer(inId) ?: break
                        buf.clear()
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(inId, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                while (true) {
                    when (val outId = codec.dequeueOutputBuffer(info, 0)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            srcRate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else srcRate
                            channels = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else channels
                        }
                        in 0..Int.MAX_VALUE -> {
                            if (outId >= 0) {
                                codec.getOutputBuffer(outId)?.let { buf ->
                                    out = appendResampled(out, buf, info, srcRate, channels, baseFrames)
                                }
                                val frameCount = info.size / (channels * 2).coerceAtLeast(2)
                                baseFrames += frameCount.toLong()
                                val done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(outId, false)
                                if (done) outputDone = true
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        else -> break
                    }
                }
                if (inputEos && outputDone) break
            }
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
        return out
    }

    private fun appendResampled(
        target: ShortArray,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        srcRate: Int,
        channels: Int,
        baseFrames: Long
    ): ShortArray {
        if (info.size <= 0 || srcRate <= 0) return target
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val frames = info.size / (channels * 2).coerceAtLeast(2)
        var out = target
        for (f in 0 until frames) {
            val srcFrame = baseFrames + f
            val dstFrame = (srcFrame.toDouble() * SAMPLE_RATE / srcRate).toInt()
            val needed = (dstFrame + 1) * 2
            if (needed > out.size) {
                out = out.copyOf(max(needed, out.size * 2 + 1024))
            }
            var sum = 0
            val offset = info.offset + f * channels * 2
            repeat(channels) { c ->
                sum += try { buffer.getShort(offset + c * 2).toInt() } catch (_: Exception) { 0 }
            }
            val value = (sum / channels).coerceIn(-32768, 32767).toShort()
            out[dstFrame * 2] = value
            out[dstFrame * 2 + 1] = value
        }
        return out
    }

    private fun encodeAudioM4a(pcm: ShortArray, out: File, onLog: (String) -> Unit) {
        if (pcm.size < 4) return
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var track = -1
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val totalFrames = pcm.size / 2
            var framePos = 0
            var pts = 0L
            var eos = false
            while (framePos < totalFrames || !eos) {
                val inId = codec.dequeueInputBuffer(10000)
                if (inId >= 0) {
                    val buf = codec.getInputBuffer(inId) ?: break
                    buf.clear()
                    buf.order(ByteOrder.LITTLE_ENDIAN)
                    val count = min(1024, totalFrames - framePos)
                    if (count > 0) {
                        repeat(count) { i -> buf.putShort(pcm[(framePos + i) * 2]) }
                        codec.queueInputBuffer(inId, 0, count * 2, pts, 0)
                        pts += count * 1_000_000L / SAMPLE_RATE
                        framePos += count
                    } else {
                        codec.queueInputBuffer(inId, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eos = true
                    }
                }
                var done = false
                while (!done) {
                    when (val outId = codec.dequeueOutputBuffer(info, 0)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (track < 0) {
                                track = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                            }
                        }
                        in 0..Int.MAX_VALUE -> {
                            if (outId >= 0) {
                                if (track >= 0 && info.size > 0) {
                                    codec.getOutputBuffer(outId)?.let { muxer.writeSampleData(track, it, info) }
                                }
                                done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(outId, false)
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> done = true
                        else -> done = true
                    }
                }
            }
            if (track >= 0) {
                var flushed = false
                while (!flushed) {
                    when (val outId = codec.dequeueOutputBuffer(info, 10000)) {
                        in 0..Int.MAX_VALUE -> {
                            if (outId >= 0) {
                                if (info.size > 0) {
                                    codec.getOutputBuffer(outId)?.let { muxer.writeSampleData(track, it, info) }
                                }
                                flushed = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(outId, false)
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> flushed = true
                        else -> flushed = true
                    }
                }
            } else {
                onLog("Audio track not initialized")
            }
        } finally {
            try { if (track >= 0) muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
    }

    private fun writeVideoMp4(
        lines: List<BroadcastLine>,
        durations: List<Int>,
        w: Int,
        h: Int,
        out: File,
        onProgress: (Int) -> Unit,
        onLog: (String) -> Unit
    ) {
        val fps = 24
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var surface: Surface? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
            val track = muxer.addTrack(codec.outputFormat)
            muxer.start()
            lines.forEachIndexed { i, line ->
                val canvas: Canvas = surface!!.lockCanvas(Rect(0, 0, w, h))
                drawSlide(canvas, line, durations.getOrElse(i) { 2000 }, i + 1, lines.size, w, h)
                surface!!.unlockCanvasAndPost(canvas)
                drainVideo(codec, muxer, track, info)
                try { Thread.sleep(durations.getOrElse(i) { 2000 }.toLong()) } catch (_: InterruptedException) {}
                onProgress(60 + ((i + 1) * 15 / lines.size))
            }
            codec.signalEndOfInputStream()
            drainVideo(codec, muxer, track, info, end = true)
            onLog("Video rendered: ${w}x${h}")
        } finally {
            try { muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            surface?.release()
        }
    }

    private fun drawSlide(canvas: Canvas, line: BroadcastLine, durationMs: Int, index: Int, total: Int, w: Int, h: Int) {
        val p = paletteFor(StudioTheme.OBSIDIAN)
        val scale = w / 720f
        val bg = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            p.bg.toArgb(), p.surface.toArgb(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = bg })

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent.toArgb() }
        paint.textSize = 30f * scale
        canvas.drawText("BYPASS STUDIO  /  BROADCAST", 48f * scale, 76f * scale, paint)

        paint.color = p.muted.toArgb()
        paint.textSize = 18f * scale
        canvas.drawText("SLIDE $index / $total", 48f * scale, 112f * scale, paint)

        paint.color = p.accent.toArgb()
        paint.textSize = 28f * scale
        canvas.drawText(line.speaker, 48f * scale, 190f * scale, paint)

        paint.color = p.text.toArgb()
        paint.textSize = 34f * scale
        val charsPerLine = if (w > h) 52 else 26
        line.text.chunked(charsPerLine).take(12).forEachIndexed { i, chunk ->
            canvas.drawText(chunk, 48f * scale, (260 + i * 54) * scale, paint)
        }

        val barY = h - 70f * scale
        paint.color = p.border.toArgb()
        canvas.drawRect(48f * scale, barY, (w - 48f * scale), barY + 6f * scale, paint)
        paint.color = p.green.toArgb()
        val progress = (durationMs.coerceAtMost(12000) / 12000f)
        canvas.drawRect(48f * scale, barY, 48f * scale + (w - 96f * scale) * progress, barY + 6f * scale, paint)
    }

    private fun drainVideo(codec: MediaCodec, muxer: MediaMuxer, track: Int, info: MediaCodec.BufferInfo, end: Boolean = false) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (end) 10000L else 0L)
            when {
                index >= 0 -> {
                    if (info.size > 0) {
                        codec.getOutputBuffer(index)?.let { muxer.writeSampleData(track, it, info) }
                    }
                    val done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                    if (done) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                else -> return
            }
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(prefix)) return i
        }
        return -1
    }

    private fun muxTogether(videoIn: File, audioIn: File, out: File, onLog: (String) -> Unit) {
        if (!audioIn.exists() || audioIn.length() < 1024) {
            onLog("Audio missing; copying video only")
            videoIn.copyTo(out, overwrite = true)
            return
        }
        val vExt = MediaExtractor()
        val aExt = MediaExtractor()
        try {
            vExt.setDataSource(videoIn.path)
            aExt.setDataSource(audioIn.path)
            val vt = findTrack(vExt, "video/")
            val at = findTrack(aExt, "audio/")
            if (vt < 0 || at < 0) {
                onLog("Track missing; copying video only")
                videoIn.copyTo(out, overwrite = true)
                return
            }
            val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val vi = muxer.addTrack(vExt.getTrackFormat(vt))
                val ai = muxer.addTrack(aExt.getTrackFormat(at))
                muxer.start()
                copyTrack(vExt, vt, vi, muxer)
                copyTrack(aExt, at, ai, muxer)
            } finally {
                try { muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
        } finally {
            vExt.release()
            aExt.release()
        }
    }

    private fun copyTrack(extractor: MediaExtractor, sourceTrack: Int, muxTrack: Int, muxer: MediaMuxer) {
        extractor.selectTrack(sourceTrack)
        val buffer = ByteBuffer.allocateDirect(512 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
            muxer.writeSampleData(muxTrack, buffer, info)
            extractor.advance()
        }
    }
}
