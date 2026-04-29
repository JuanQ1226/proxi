package com.jq.proxi

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object ProxyStats {
    val isRunning = AtomicBoolean(false)

    val activeConnections = AtomicLong(0)
    val totalConnections = AtomicLong(0)

    val bytesFromClient = AtomicLong(0)
    val bytesToClient = AtomicLong(0)

    fun onConnectionOpened() {
        activeConnections.incrementAndGet()
        totalConnections.incrementAndGet()
    }

    fun onConnectionClosed() {
        while (true) {
            val current = activeConnections.get()
            if (current == 0L) return
            if (activeConnections.compareAndSet(current, current - 1)) return
        }
    }

    fun addBytesFromClient(bytes: Int) {
        if (bytes > 0) {
            bytesFromClient.addAndGet(bytes.toLong())
        }
    }

    fun addBytesToClient(bytes: Int) {
        if (bytes > 0) {
            bytesToClient.addAndGet(bytes.toLong())
        }
    }

    fun reset() {
        activeConnections.set(0)
        totalConnections.set(0)
        bytesFromClient.set(0)
        bytesToClient.set(0)
    }

    fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0

        return when {
            bytes >= gb -> "%.2f GB".format(bytes / gb)
            bytes >= mb -> "%.2f MB".format(bytes / mb)
            bytes >= kb -> "%.2f KB".format(bytes / kb)
            else -> "$bytes B"
        }
    }
}
