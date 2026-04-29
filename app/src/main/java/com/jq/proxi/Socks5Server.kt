package com.jq.proxi

import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class Socks5Server(
    private val host: String,
    private val port: Int
) {
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    private var serverSocket: ServerSocket? = null

    fun start() {
        if (running.getAndSet(true)) {
            Log.d(TAG, "Server already running")
            return
        }

        executor.execute {
            try {
                val bindAddress = InetAddress.getByName(host)
                serverSocket = ServerSocket(port, 50, bindAddress)

                Log.d(TAG, "SOCKS5 server listening on $host:$port")

                while (running.get()) {
                    val clientSocket = serverSocket?.accept() ?: break

                    Log.d(
                        TAG,
                        "Accepted client from ${clientSocket.inetAddress}:${clientSocket.port}"
                    )

                    executor.execute {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.e(TAG, "Server crashed", e)
                } else {
                    Log.d(TAG, "Server stopped")
                }
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping SOCKS5 server")

        running.set(false)

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close server socket", e)
        }

        executor.shutdownNow()
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            Socks5ClientHandler(clientSocket).run()
        } catch (e: Exception) {
            Log.e(TAG, "Client handler failed", e)
        }
    }

    companion object {
        private const val TAG = "Socks5Server"
    }
}