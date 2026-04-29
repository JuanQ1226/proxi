package com.jq.proxi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class ProxyService : Service() {

    private var socksServer: Socks5Server? = null
    private var httpServer: HttpProxyServer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopProxy()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }

                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                startForeground(1, buildNotification())
                startProxy()
                return START_STICKY
            }

            else -> {
                startForeground(1, buildNotification())
                startProxy()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        stopProxy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProxy() {
        if (socksServer != null || httpServer != null) {
            Log.d(TAG, "Proxy already running")
            ProxyStats.isRunning.set(true)
            return
        }

        ProxyStats.reset()
        ProxyStats.isRunning.set(true)

        Log.d(TAG, "Starting SOCKS5 proxy on 127.0.0.1:1080")
        socksServer = Socks5Server(
            host = "127.0.0.1",
            port = 1080
        )
        socksServer?.start()

        Log.d(TAG, "Starting HTTP proxy on 127.0.0.1:8080")
        httpServer = HttpProxyServer(
            host = "127.0.0.1",
            port = 8080
        )
        httpServer?.start()
    }

    private fun stopProxy() {
        Log.d(TAG, "Stopping proxies")

        ProxyStats.isRunning.set(false)

        try {
            socksServer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping SOCKS proxy", e)
        }

        try {
            httpServer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping HTTP proxy", e)
        }

        socksServer = null
        httpServer = null
    }

    private fun buildNotification(): Notification {
        val channelId = "proxi_channel"

        val stopIntent = Intent(this, ProxyService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Proxi",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("Proxi running")
            .setContentText("SOCKS5 :1080 and HTTP :8080 active")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.jq.proxi.action.START"
        const val ACTION_STOP = "com.jq.proxi.action.STOP"

        private const val TAG = "ProxyService"
        private const val STOP_REQUEST_CODE = 1002
    }
}
