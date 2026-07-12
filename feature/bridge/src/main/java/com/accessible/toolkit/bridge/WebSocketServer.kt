package com.accessible.toolkit.bridge

import android.util.Log
import com.google.gson.Gson
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class WebSocketServer(
    port: Int = 8765
) : WebSocketServer(InetSocketAddress(port)) {

    companion object {
        private const val TAG = "WebSocketServer"
    }

    private val clients = mutableSetOf<WebSocket>()
    private val gson = Gson()
    private var listener: ServerListener? = null

    interface ServerListener {
        fun onClientConnected(clientCount: Int)
        fun onClientDisconnected(clientCount: Int)
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        clients.add(conn)
        Log.d(TAG, "Client connected: ${conn.remoteSocketAddress}")
        listener?.onClientConnected(clients.size)
        broadcastConnectionCount()
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        conn?.let { clients.remove(it) }
        Log.d(TAG, "Client disconnected: $reason")
        listener?.onClientDisconnected(clients.size)
        broadcastConnectionCount()
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.d(TAG, "Message received: $message")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "WebSocket error", ex)
    }

    override fun onStart() {
        Log.d(TAG, "WebSocket server started on port ${port}")
    }

    fun broadcastTranscript(text: String, isFinal: Boolean) {
        val message = mapOf(
            "type" to "transcript",
            "text" to text,
            "isFinal" to isFinal,
            "timestamp" to System.currentTimeMillis()
        )
        val json = gson.toJson(message)
        broadcast(json)
    }

    fun broadcastSubtitle(text: String) {
        val message = mapOf(
            "type" to "subtitle",
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )
        val json = gson.toJson(message)
        broadcast(json)
    }

    private fun broadcastConnectionCount() {
        val message = mapOf(
            "type" to "status",
            "clientCount" to clients.size,
            "timestamp" to System.currentTimeMillis()
        )
        val json = gson.toJson(message)
        broadcast(json)
    }

    fun setListener(listener: ServerListener?) {
        this.listener = listener
    }

    fun getClientCount(): Int = clients.size
}