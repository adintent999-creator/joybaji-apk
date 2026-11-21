package com.bypass.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File

object BroadcastExporter {
    fun exportSlides(context: Context, text: String, out: File, onLog: (String) -> Unit) {
        val width = 720
        val height = 1280
        val fps = 24
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface: Surface = codec.inputSurface
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        try {
            codec.start()
            val track = muxer.addTrack(codec.outputFormat)
            muxer.start()
            val slides = text.chunked(96).take(40).ifEmpty { listOf("Bypass Studio") }
            slides.forEachIndexed { index, raw ->
                val canvas: Canvas = surface.lockCanvas(Rect(0, 0, width, height))
                drawSlide(canvas, raw, index + 1, slides.size, width, height)
                surface.unlockCanvasAndPost(canvas)
                drain(codec, muxer, track, info)
                Thread.sleep(900)
            }
            codec.signalEndOfInputStream()
            drain(codec, muxer, track, info, end = true)
            onLog("MP4 exported: ${out.name}")
        } finally {
            try { muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            surface.release()
        }
    }

    private fun drawSlide(canvas: Canvas, text: String, index: Int, total: Int, w: Int, h: Int) {
        canvas.drawColor(Color.rgb(6, 7, 10))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 217, 245)
            textSize = 42f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("BYPASS STUDIO", 56f, 100f, paint)
        paint.color = Color.rgb(126, 139, 150)
        paint.textSize = 24f
        canvas.drawText("SLIDE $index / $total", 56f, 148f, paint)
        paint.color = Color.rgb(234, 246, 248)
        paint.textSize = 38f
        val lines = text.chunked(30)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, 56f, 300f + i * 62f, paint)
        }
        paint.color = Color.rgb(0, 217, 245)
        canvas.drawRect(56f, h - 160f, w - 56f, h - 156f, paint)
    }

    private fun drain(
        codec: MediaCodec,
        muxer: MediaMuxer,
        track: Int,
        info: MediaCodec.BufferInfo,
        end: Boolean = false
    ) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
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
}
