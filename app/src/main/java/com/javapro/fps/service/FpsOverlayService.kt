package com.javapro.fps.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.javapro.MainActivity
import com.javapro.R
import com.javapro.fps.monitor.FpsMonitorManager
import com.javapro.fps.parser.FpsParser
import com.javapro.fps.utils.SystemInfoUtils
import kotlinx.coroutines.*

class FpsOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var fpsTextView: TextView? = null
    private var detailTextView: TextView? = null

    private var monitorManager: FpsMonitorManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null

    private val NOTIF_ID = 1001
    private val CHANNEL_ID = "fps_overlay_channel"

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        monitorManager = FpsMonitorManager(this)

        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification())

        setupOverlay()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FPS Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_fps_overlay_title))
            .setContentText(getString(R.string.notif_fps_overlay_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupOverlay() {
        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_fps, null)
            fpsTextView = overlayView?.findViewById(R.id.fps_text)
            detailTextView = overlayView?.findViewById(R.id.detail_text)

            windowManager.addView(overlayView, layoutParams)

            overlayView?.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            initialX = layoutParams.x
                            initialY = layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(overlayView, layoutParams)
                            return true
                        }
                    }
                    return false
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra("TARGET_PACKAGE") ?: "com.android.settings"
        monitorManager?.startMonitoring(packageName)
        startUpdating()
        return START_STICKY
    }

    private fun startUpdating() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            launch {
                monitorManager?.frameSamples?.collect { samples ->
                    if (samples.isNotEmpty()) {
                        val stats = FpsParser.calculateStats(samples, 60f)
                        fpsTextView?.text = "FPS: %.1f".format(stats.currentFps)
                    }
                }
            }

            while (isActive) {
                val gpuUsage = SystemInfoUtils.getGpuUsage()
                val cpuUsage = SystemInfoUtils.getCpuUsage()
                val temp = SystemInfoUtils.getTemperature()

                val gpuStr = if (gpuUsage >= 0) "%.0f%%".format(gpuUsage) else "--"

                detailTextView?.text = "CPU: %.0f%% | GPU: %s | Temp: %.1f°C".format(cpuUsage, gpuStr, temp)
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        monitorManager?.stopMonitoring()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
