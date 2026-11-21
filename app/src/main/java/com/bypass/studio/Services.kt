package com.bypass.studio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class VoiceService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1101, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bypass_voice", "Background Assistant", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Bypass Studio voice assistant" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, "bypass_voice")
            .setContentTitle("Bypass Studio")
            .setContentText("Background assistant active")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }
}

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var capsule: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1102, buildOverlayNotification())
        if (Settings.canDrawOverlays(this)) showCapsule() else stopSelf()
        return START_STICKY
    }

    private fun buildOverlayNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bypass_overlay", "Dynamic Island Capsule", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Bypass Studio floating capsule" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, "bypass_overlay")
            .setContentTitle("Bypass Studio")
            .setContentText("Dynamic Island capsule active")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun showCapsule() {
        if (capsule != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.CYAN)
                setStroke(2, Color.WHITE)
            }
            alpha = 0.88f
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            130, 46, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 60
        }
        windowManager?.addView(view, params)
        capsule = view
    }

    override fun onDestroy() {
        capsule?.let { runCatching { windowManager?.removeView(it) } }
        capsule = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }
}
