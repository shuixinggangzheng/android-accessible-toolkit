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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accessible.toolkit.engine.AsrCallback
import com.accessible.toolkit.engine.VadCallback
import com.accessible.toolkit.engine.model.TranscriptResult
import com.accessible.toolkit.engine.AsrError
import com.accessible.toolkit.engine.EventBus
import com.accessible.toolkit.vad.EnergyVadDetector
import com.accessible.toolkit.vosk.VoskAsrEngine
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

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

        var currentLanIp: String = "127.0.0.1"
            private set

        private var serviceListener: ServiceListener? = null

        interface ServiceListener {
            fun onStateChanged(running: Boolean)
            fun onTranscriptUpdate(text: String, isFinal: Boolean)
            fun onVadStateChange(state: SubtitleWebSocketServer.VadState)
            fun onServerAddressChanged(ip: String, httpPort: Int)
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

        fun getDeviceLanIp(context: Context): String {
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipAddress != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                }
            } catch (_: Exception) {}

            try {
                NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { intf ->
                    if (intf.isUp && !intf.isLoopback) {
                        intf.inetAddresses.toList()
                            .filterIsInstance<Inet4Address>()
                            .filter { !it.isLoopbackAddress }
                            .forEach { return it.hostAddress ?: "127.0.0.1" }
                    }
                }
            } catch (_: Exception) {}

            return "127.0.0.1"
        }
    }

    private var webSocketServer: SubtitleWebSocketServer? = null
    private var httpServer: SubtitleHttpServer? = null
    private var asrEngine: VoskAsrEngine? = null
    private var vadDetector: EnergyVadDetector? = null
    private var actionReceiver: BroadcastReceiver? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private var wasStoppedByNetwork = false
    private var clientCount = 0
    private var serverPort = SubtitleWebSocketServer.DEFAULT_PORT
    private var asrReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerActionReceiver()
        registerNetworkCallback()
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
        val lanIp = getDeviceLanIp(this)
        currentLanIp = lanIp
        val httpPort = serverPort + 1
        wasStoppedByNetwork = false

        webSocketServer = SubtitleWebSocketServer(serverPort, bindAddress = lanIp).apply {
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
        Log.d(TAG, "WebSocket server started on $lanIp:$serverPort")

        try {
            httpServer = SubtitleHttpServer(httpPort, this)
            httpServer?.start()
            Log.d(TAG, "HTTP server started on $lanIp:$httpPort")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server", e)
        }

        serviceListener?.onServerAddressChanged(lanIp, httpPort)

        asrReady = false
        asrEngine = VoskAsrEngine(this).apply {
            setCallback(createAsrCallback())
            loadDefaultModel()
        }

        vadDetector = EnergyVadDetector(this).apply {
            setCallback(createVadCallback())
        }

        isRunning = true
        EventBus.setBridgeRunning(true, lanIp, clientCount)
        serviceListener?.onStateChanged(true)
        updateNotification()
        Log.d(TAG, "Bridge service started")
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
        EventBus.setBridgeRunning(false)
        serviceListener?.onStateChanged(false)
        Log.d(TAG, "Bridge service stopped")
    }

    private fun restartServer() {
        val lanIp = getDeviceLanIp(this)
        currentLanIp = lanIp

        webSocketServer?.stopServer()
        webSocketServer?.destroy()
        httpServer?.stop()

        webSocketServer = SubtitleWebSocketServer(serverPort, bindAddress = lanIp).apply {
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

        val httpPort = serverPort + 1
        try {
            httpServer = SubtitleHttpServer(httpPort, this)
            httpServer?.start()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to restart HTTP server", e)
        }

        serviceListener?.onServerAddressChanged(lanIp, httpPort)
        Log.d(TAG, "Server restarted on $lanIp:$serverPort")
    }

    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            connectivityCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "WiFi connected")
                    if (wasStoppedByNetwork && !isRunning) {
                        Log.d(TAG, "WiFi restored, re-enabling bridge service")
                        startBridge()
                    } else if (isRunning) {
                        restartServer()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "WiFi disconnected")
                    if (isRunning) {
                        wasStoppedByNetwork = true
                        stopBridge()
                    }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                    val hasWifi = networkCapabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    )
                    if (hasWifi && !isRunning && wasStoppedByNetwork) {
                        Log.d(TAG, "WiFi capabilities restored")
                        startBridge()
                    }
                }
            }
            connectivityManager.registerNetworkCallback(request, connectivityCallback!!)
        }
    }

    private fun unregisterNetworkCallback() {
        connectivityCallback?.let {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        connectivityCallback = null
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
            "已连接 $clientCount 个设备 | $currentLanIp:$serverPort"
        } else {
            "$currentLanIp:$serverPort"
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
        unregisterNetworkCallback()
        stopBridge()
        super.onDestroy()
    }

    private class SubtitleHttpServer(
        port: Int,
        private val service: BridgeService
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/", "/index.html" -> serveSubtitlePage()
                "/api/status" -> serveStatusJson()
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
            val ip = currentLanIp
            val json = """
                {"running":true,"ip":"$ip","wsPort":${service.serverPort},"httpPort":${port},"clients":${service.clientCount}}
            """.trimIndent()
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                json
            )
        }
    }
}
