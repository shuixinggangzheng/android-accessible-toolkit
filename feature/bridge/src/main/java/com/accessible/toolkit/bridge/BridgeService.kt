package com.accessible.toolkit.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accessible.toolkit.engine.AsrCallback
import com.accessible.toolkit.engine.VadCallback
import com.accessible.toolkit.engine.model.TranscriptResult
import com.accessible.toolkit.engine.AsrError
import com.accessible.toolkit.vad.EnergyVadDetector
import com.accessible.toolkit.vosk.VoskAsrEngine
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class BridgeService : Service() {

    companion object {
        private const val TAG = "BridgeService"
        private const val CHANNEL_ID = "bridge_channel"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.accessible.toolkit.bridge.START"
        const val ACTION_STOP = "com.accessible.toolkit.bridge.STOP"
        const val ACTION_SET_PORT = "com.accessible.toolkit.bridge.SET_PORT"
        const val EXTRA_PORT = "port"

        var isRunning = false
            private set

        private var serviceListener: ServiceListener? = null

        interface ServiceListener {
            fun onStateChanged(running: Boolean)
            fun onTranscriptUpdate(text: String, isFinal: Boolean)
            fun onVadStateChange(state: SubtitleWebSocketServer.VadState)
        }

        fun setServiceListener(listener: ServiceListener?) {
            serviceListener = listener
        }

        fun start(context: Context) {
            val intent = Intent(context, BridgeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BridgeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setPort(context: Context, port: Int) {
            val intent = Intent(context, BridgeService::class.java).apply {
                action = ACTION_SET_PORT
                putExtra(EXTRA_PORT, port)
            }
            context.startService(intent)
        }
    }

    private var webSocketServer: SubtitleWebSocketServer? = null
    private var httpServer: SubtitleHttpServer? = null
    private var asrEngine: VoskAsrEngine? = null
    private var vadDetector: EnergyVadDetector? = null
    private var actionReceiver: BroadcastReceiver? = null
    private var clientCount = 0
    private var serverPort = SubtitleWebSocketServer.DEFAULT_PORT
    private var asrReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerActionReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startBridge()
                }
            }
            ACTION_STOP -> {
                stopBridge()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_PORT -> {
                val port = intent.getIntExtra(EXTRA_PORT, SubtitleWebSocketServer.DEFAULT_PORT)
                serverPort = port
                if (isRunning) {
                    restartServer()
                }
            }
        }
        return START_STICKY
    }

    private fun startBridge() {
        webSocketServer = SubtitleWebSocketServer(serverPort).apply {
            setListener(object : SubtitleWebSocketServer.ServerListener {
                override fun onClientConnected(count: Int) {
                    clientCount = count
                    updateNotification()
                    Log.d(TAG, "Client connected: $count")
                }

                override fun onClientDisconnected(count: Int) {
                    clientCount = count
                    updateNotification()
                    Log.d(TAG, "Client disconnected: $count")
                }

                override fun onServerError(error: Exception) {
                    Log.e(TAG, "Server error", error)
                }
            })
            startServer()
        }

        // Start HTTP server for browser subtitle page
        try {
            httpServer = SubtitleHttpServer(serverPort + 1, this)
            httpServer?.start()
            Log.d(TAG, "HTTP server started on port ${serverPort + 1}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server", e)
        }

        asrReady = false
        asrEngine = VoskAsrEngine(this).apply {
            setCallback(createAsrCallback())
            loadDefaultModel()
        }

        vadDetector = EnergyVadDetector(this).apply {
            setCallback(createVadCallback())
        }

        isRunning = true
        serviceListener?.onStateChanged(true)
        updateNotification()
        Log.d(TAG, "Bridge service started on port $serverPort")
    }

    private fun stopBridge() {
        vadDetector?.stop()
        asrEngine?.stopListening()
        asrEngine?.destroy()

        httpServer?.stop()
        httpServer = null

        webSocketServer?.stopServer()
        webSocketServer?.destroy()
        webSocketServer = null

        vadDetector = null
        asrEngine = null

        isRunning = false
        clientCount = 0
        serviceListener?.onStateChanged(false)
        Log.d(TAG, "Bridge service stopped")
    }

    private fun restartServer() {
        webSocketServer?.stopServer()
        webSocketServer?.destroy()
        webSocketServer = SubtitleWebSocketServer(serverPort).apply {
            setListener(object : SubtitleWebSocketServer.ServerListener {
                override fun onClientConnected(count: Int) {
                    clientCount = count
                    updateNotification()
                    Log.d(TAG, "Client connected after restart: $count")
                }

                override fun onClientDisconnected(count: Int) {
                    clientCount = count
                    updateNotification()
                    Log.d(TAG, "Client disconnected after restart: $count")
                }

                override fun onServerError(error: Exception) {
                    Log.e(TAG, "Server error after restart", error)
                }
            })
            startServer()
        }
        Log.d(TAG, "Server restarted on port $serverPort")
    }

    private fun createAsrCallback(): AsrCallback {
        return object : AsrCallback {
            override fun onPartialResult(result: TranscriptResult) {
                webSocketServer?.broadcastPartialResult(result.text)
                serviceListener?.onTranscriptUpdate(result.text, isFinal = false)
            }

            override fun onFinalResult(result: TranscriptResult) {
                webSocketServer?.broadcastFinalResult(result.text)
                serviceListener?.onTranscriptUpdate(result.text, isFinal = true)
            }

            override fun onError(error: AsrError) {
                Log.e(TAG, "ASR error: $error")
            }

            override fun onReady() {
                Log.d(TAG, "ASR engine ready")
                asrReady = true
                vadDetector?.start()
            }
        }
    }

    private fun createVadCallback(): VadCallback {
        return object : VadCallback {
            override fun onVoiceStart() {
                asrEngine?.startListening()
                webSocketServer?.broadcastVadState(SubtitleWebSocketServer.VadState.VOICE_START)
                serviceListener?.onVadStateChange(SubtitleWebSocketServer.VadState.VOICE_START)
            }

            override fun onVoiceEnd() {
                asrEngine?.stopListening()
                webSocketServer?.broadcastVadState(SubtitleWebSocketServer.VadState.VOICE_END)
                serviceListener?.onVadStateChange(SubtitleWebSocketServer.VadState.VOICE_END)
            }

            override fun onSilenceDuration(seconds: Int) {
                if (seconds > 3) {
                    webSocketServer?.broadcastVadState(SubtitleWebSocketServer.VadState.SILENCE)
                    serviceListener?.onVadStateChange(SubtitleWebSocketServer.VadState.SILENCE)
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "VAD error: $error")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "跨设备字幕服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "跨设备字幕广播服务"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = createPendingIntent(ACTION_STOP)

        val title = "跨设备字幕服务运行中"
        val text = if (clientCount > 0) {
            "已连接 $clientCount 个设备 | 端口: $serverPort"
        } else {
            "等待设备连接... | 端口: $serverPort"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(createOpenAppIntent())
            .addAction(android.R.drawable.ic_delete, "停止", stopIntent)
            .build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun registerActionReceiver() {
        actionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_STOP -> stopBridge()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }
    }

    private fun unregisterActionReceiver() {
        actionReceiver?.let { unregisterReceiver(it) }
        actionReceiver = null
    }

    override fun onDestroy() {
        unregisterActionReceiver()
        stopBridge()
        super.onDestroy()
    }

    /**
     * NanoHTTPD server that serves the subtitle HTML page from assets
     * and acts as a reverse proxy for WebSocket connections.
     */
    private class SubtitleHttpServer(
        port: Int,
        private val service: BridgeService
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri

            return when {
                uri == "/" || uri == "/index.html" -> serveSubtitlePage()
                uri == "/api/status" -> serveStatusJson()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain", "Not Found"
                )
            }
        }

        private fun serveSubtitlePage(): Response {
            return try {
                val inputStream = service.assets.open("subtitle.html")
                val html = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()
                newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    html
                )
            } catch (e: Exception) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Failed to load subtitle page: ${e.message}"
                )
            }
        }

        private fun serveStatusJson(): Response {
            val json = """{"running":true,"port":${service.serverPort},"clients":${service.clientCount}}"""
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                json
            )
        }
    }
}
