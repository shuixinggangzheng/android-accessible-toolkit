package com.accessible.toolkit.bridge

import android.util.Log
import com.google.gson.Gson
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SubtitleWebSocketServer(
    port: Int = DEFAULT_PORT
) : WebSocketServer(InetSocketAddress(port)) {

    companion object {
        private const val TAG = "SubtitleWebSocketServer"
        const val DEFAULT_PORT = 8765
        private const val MAX_HISTORY_SIZE = 10
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
    }

    private val clients = mutableSetOf<WebSocket>()
    private val gson = Gson()
    private val recentFinalResults = ConcurrentLinkedDeque<TranscriptMessage>()
    private var heartbeatFuture: ScheduledFuture<*>? = null
    private val heartbeatExecutor = Executors.newSingleThreadScheduledExecutor()
    private var listener: ServerListener? = null

    data class TranscriptMessage(
        val type: String,
        val text: String,
        val timestamp: Long
    )

    data class VadMessage(
        val type: String,
        val state: String,
        val timestamp: Long
    )

    data class StatusMessage(
        val type: String,
        val message: String
    )

    data class HeartbeatMessage(
        val type: String
    )

    interface ServerListener {
        fun onClientConnected(clientCount: Int)
        fun onClientDisconnected(clientCount: Int)
        fun onServerError(error: Exception)
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        super.onOpen(conn, handshake)
        synchronized(clients) {
            clients.add(conn)
        }
        Log.d(TAG, "Client connected: ${conn.remoteSocketAddress}, total: ${clients.size}")

        sendToClient(conn, StatusMessage("status", "connected"))

        sendRecentResultsToClient(conn)

        listener?.onClientConnected(clients.size)
        broadcastConnectionCount()
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        conn?.let {
            synchronized(clients) {
                clients.remove(it)
            }
        }
        Log.d(TAG, "Client disconnected: $reason, total: ${clients.size}")
        listener?.onClientDisconnected(clients.size)
        broadcastConnectionCount()
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.d(TAG, "Message received from client: $message")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "WebSocket error", ex)
        listener?.onServerError(ex)
    }

    override fun onStart() {
        Log.d(TAG, "WebSocket server started on port ${port}")
        startHeartbeat()
    }

    fun startServer(port: Int = this.port) {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return
        }
        this.port = port
        start()
    }

    fun stopServer() {
        stopHeartbeat()
        stop()
        synchronized(clients) {
            clients.clear()
        }
        recentFinalResults.clear()
        Log.d(TAG, "Server stopped")
    }

    fun isRunning(): Boolean {
        return try {
            // Check if server is bound and accepting connections
            !this.isClosed
        } catch (e: Exception) {
            false
        }
    }

    // ASR result broadcasting
    fun broadcastPartialResult(text: String) {
        val message = TranscriptMessage(
            type = "partial",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        broadcast(gson.toJson(message))
    }

    fun broadcastFinalResult(text: String) {
        val message = TranscriptMessage(
            type = "final",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        val json = gson.toJson(message)
        broadcast(json)

        // Store in recent results (keep only last MAX_HISTORY_SIZE)
        recentFinalResults.addLast(message)
        while (recentFinalResults.size > MAX_HISTORY_SIZE) {
            recentFinalResults.removeFirst()
        }
    }

    // VAD state broadcasting
    fun broadcastVadState(state: VadState) {
        val stateStr = when (state) {
            VadState.VOICE_START -> "voice_start"
            VadState.VOICE_END -> "voice_end"
            VadState.SILENCE -> "silence"
        }
        val message = VadMessage(
            type = "vad",
            state = stateStr,
            timestamp = System.currentTimeMillis()
        )
        broadcast(gson.toJson(message))
    }

    enum class VadState {
        VOICE_START,
        VOICE_END,
        SILENCE
    }

    // Client management
    fun getClientCount(): Int {
        synchronized(clients) {
            return clients.size
        }
    }

    fun setListener(listener: ServerListener?) {
        this.listener = listener
    }

    // Internal methods
    private fun sendToClient(conn: WebSocket, message: Any) {
        try {
            val json = gson.toJson(message)
            conn.send(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message to client", e)
        }
    }

    private fun sendRecentResultsToClient(conn: WebSocket) {
        try {
            // Send last 3 final results for context recovery
            val recentResults = recentFinalResults.toList().takeLast(3)
            if (recentResults.isNotEmpty()) {
                val historyMessage = mapOf(
                    "type" to "history",
                    "results" to recentResults
                )
                sendToClient(conn, historyMessage)
                Log.d(TAG, "Sent ${recentResults.size} recent results to new client")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending recent results to client", e)
        }
    }

    private fun broadcastConnectionCount() {
        val message = mapOf(
            "type" to "status",
            "clientCount" to clients.size,
            "timestamp" to System.currentTimeMillis()
        )
        broadcast(gson.toJson(message))
    }

    // Heartbeat mechanism
    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate({
            try {
                val heartbeat = HeartbeatMessage("heartbeat")
                broadcast(gson.toJson(heartbeat))
                Log.d(TAG, "Heartbeat sent to ${clients.size} clients")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending heartbeat", e)
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun stopHeartbeat() {
        heartbeatFuture?.cancel(false)
        heartbeatFuture = null
    }

    override fun stop() {
        stopHeartbeat()
        super.stop()
    }

    override fun destroy() {
        stopHeartbeat()
        heartbeatExecutor.shutdown()
        super.destroy()
    }
}
