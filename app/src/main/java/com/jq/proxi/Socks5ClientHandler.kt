package com.jq.proxi

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

class Socks5ClientHandler(
    private val clientSocket: Socket
) : Runnable {

    override fun run() {
        ProxyStats.onConnectionOpened()

        try {
            clientSocket.use { client ->
                try {
                    Log.d(TAG, "Client connected from ${client.inetAddress}:${client.port}")

                    client.tcpNoDelay = true

                    val input = client.getInputStream()
                    val output = client.getOutputStream()

                    if (!handleGreeting(input, output)) {
                        Log.e(TAG, "Invalid SOCKS5 greeting")
                        return
                    }

                    val request = parseConnectRequest(input)
                    if (request == null) {
                        Log.e(TAG, "Invalid SOCKS5 CONNECT request")
                        sendFailure(output)
                        return
                    }

                    Log.d(TAG, "CONNECT ${request.host}:${request.port}")

                    val remoteSocket = Socket()

                    try {
                        remoteSocket.tcpNoDelay = true
                        remoteSocket.connect(
                            InetSocketAddress(request.host, request.port),
                            15_000
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to connect to remote ${request.host}:${request.port}", e)
                        sendFailure(output)
                        return
                    }

                    remoteSocket.use { remote ->
                        sendSuccess(output)
                        Log.d(TAG, "Tunnel established to ${request.host}:${request.port}")
                        relay(client, remote)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SOCKS5 client handler crashed", e)
                }
            }
        } finally {
            ProxyStats.onConnectionClosed()
        }
    }

    private fun handleGreeting(input: InputStream, output: OutputStream): Boolean {
        val version = input.read()
        if (version != 0x05) {
            Log.e(TAG, "Unsupported SOCKS version: $version")
            return false
        }

        val methodCount = input.read()
        if (methodCount <= 0) {
            Log.e(TAG, "Invalid method count: $methodCount")
            return false
        }

        val methods = ByteArray(methodCount)
        input.readFullyCompat(methods)

        Log.d(TAG, "SOCKS methods: ${methods.joinToString { it.toUByte().toString() }}")

        output.write(byteArrayOf(0x05, 0x00))
        output.flush()

        return true
    }

    private fun parseConnectRequest(input: InputStream): Socks5Request? {
        val version = input.read()
        val command = input.read()
        val reserved = input.read()
        val addressType = input.read()

        Log.d(
            TAG,
            "Request header version=$version command=$command reserved=$reserved addressType=$addressType"
        )

        if (version != 0x05) return null
        if (command != 0x01) return null

        val host = when (addressType) {
            0x01 -> {
                val addressBytes = ByteArray(4)
                input.readFullyCompat(addressBytes)
                InetAddress.getByAddress(addressBytes).hostAddress ?: return null
            }

            0x03 -> {
                val length = input.read()
                if (length <= 0) return null

                val domainBytes = ByteArray(length)
                input.readFullyCompat(domainBytes)
                String(domainBytes, StandardCharsets.UTF_8)
            }

            0x04 -> {
                val addressBytes = ByteArray(16)
                input.readFullyCompat(addressBytes)
                InetAddress.getByAddress(addressBytes).hostAddress ?: return null
            }

            else -> {
                Log.e(TAG, "Unsupported address type: $addressType")
                return null
            }
        }

        val portHigh = input.read()
        val portLow = input.read()

        if (portHigh < 0 || portLow < 0) return null

        val port = (portHigh shl 8) or portLow

        return Socks5Request(host, port)
    }

    private fun sendSuccess(output: OutputStream) {
        output.write(
            byteArrayOf(
                0x05,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00
            )
        )
        output.flush()
    }

    private fun sendFailure(output: OutputStream) {
        output.write(
            byteArrayOf(
                0x05,
                0x01,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00
            )
        )
        output.flush()
    }

    private fun relay(client: Socket, remote: Socket) {
        val latch = CountDownLatch(2)

        Thread {
            try {
                copyStream(
                    client.getInputStream(),
                    remote.getOutputStream(),
                    Direction.CLIENT_TO_REMOTE
                )
            } catch (e: Exception) {
                Log.d(TAG, "Client to remote relay ended: ${e.message}")
            } finally {
                try {
                    remote.shutdownOutput()
                } catch (_: Exception) {
                }

                latch.countDown()
            }
        }.start()

        Thread {
            try {
                copyStream(
                    remote.getInputStream(),
                    client.getOutputStream(),
                    Direction.REMOTE_TO_CLIENT
                )
            } catch (e: Exception) {
                Log.d(TAG, "Remote to client relay ended: ${e.message}")
            } finally {
                try {
                    client.shutdownOutput()
                } catch (_: Exception) {
                }

                latch.countDown()
            }
        }.start()

        latch.await()
    }

    private fun copyStream(
        input: InputStream,
        output: OutputStream,
        direction: Direction
    ) {
        val buffer = ByteArray(16 * 1024)

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break

            when (direction) {
                Direction.CLIENT_TO_REMOTE -> ProxyStats.addBytesFromClient(read)
                Direction.REMOTE_TO_CLIENT -> ProxyStats.addBytesToClient(read)
            }

            output.write(buffer, 0, read)
            output.flush()
        }
    }

    private fun InputStream.readFullyCompat(buffer: ByteArray) {
        var offset = 0

        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)

            if (read == -1) {
                throw IllegalStateException("Unexpected end of stream")
            }

            offset += read
        }
    }

    private enum class Direction {
        CLIENT_TO_REMOTE,
        REMOTE_TO_CLIENT
    }

    companion object {
        private const val TAG = "Socks5Handler"
    }
}

data class Socks5Request(
    val host: String,
    val port: Int
)