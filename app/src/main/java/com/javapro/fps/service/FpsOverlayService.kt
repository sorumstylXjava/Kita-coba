package com.javapro.fps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.javapro.R
import com.javapro.fps.monitor.FpsMonitorManager
import com.javapro.fps.parser.FpsParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class FpsOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var fpsText: TextView? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var monitorManager: FpsMonitorManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        monitorManager = FpsMonitorManager(this)

        createNotificationChannel()
        startForeground(1, createNotification())

        setupOverlay()
    }

    private fun setupOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_fps, null)
        fpsText = overlayView?.findViewById(R.id.tv_fps)

        windowManager.addView(overlayView, params)

        // Update FPS logic
        serviceScope.launch {
            monitorManager.frameSamples.collect { samples ->
                if (samples.isNotEmpty()) {
                    val stats = FpsParser.calculateStats(samples, 60f)
                    fpsText?.text = "%.1f".format(stats.currentFps)

                    // Color based on FPS
                    val color = when {
                        stats.currentFps >= 55f -> 0xFF66BB6A.toInt()
                        stats.currentFps >= 30f -> 0xFFFFCA28.toInt()
                        else -> 0xFFEF5350.toInt()
                    }
                    fpsText?.setTextColor(color)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra("package_name") ?: "com.android.settings"
        monitorManager.startMonitoring(packageName)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorManager.stopMonitoring()
        overlayView?.let { windowManager.removeView(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fps_monitor",
                "FPS Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "fps_monitor")
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("FPS Monitor Active")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }
}
