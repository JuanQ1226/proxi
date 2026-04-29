package com.jq.proxi

import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class HttpProxyServer(
    private val host: String,
    private val port: Int
) {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (running.getAndSet(true)) {
            Log.d(TAG, "HTTP proxy already running")
            return
        }

        executor.execute {
            try {
                val bindAddress = InetAddress.getByName(host)
                serverSocket = ServerSocket(port, 50, bindAddress)

                Log.d(TAG, "HTTP proxy listening on $host:$port")

                while (running.get()) {
                    val clientSocket = serverSocket?.accept() ?: break

                    Log.d(
                        TAG,
                        "Accepted HTTP client from ${clientSocket.inetAddress}:${clientSocket.port}"
                    )

                    executor.execute {
                        HttpProxyClientHandler(clientSocket).run()
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.e(TAG, "HTTP proxy crashed", e)
                } else {
                    Log.d(TAG, "HTTP proxy stopped")
                }
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping HTTP proxy")

        running.set(false)

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close HTTP server socket", e)
        }

        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "HttpProxyServer"
    }
}