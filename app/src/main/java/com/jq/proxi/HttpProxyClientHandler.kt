package com.jq.proxi

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CountDownLatch

class HttpProxyClientHandler(
    private val clientSocket: Socket
) : Runnable {

    override fun run() {
        ProxyStats.onConnectionOpened()

        try {
            clientSocket.use { client ->
                try {
                    client.tcpNoDelay = true

                    val input = client.getInputStream()
                    val output = client.getOutputStream()

                    val requestLine = readRequestLine(input)

                    if (requestLine == null) {
                        Log.e(TAG, "Empty HTTP request")
                        return
                    }

                    Log.d(TAG, "HTTP request line: $requestLine")

                    val parts = requestLine.split(" ")
                    if (parts.size < 3) {
                        sendBadRequest(output)
                        return
                    }

                    val method = parts[0].uppercase(Locale.US)
                    val target = parts[1]

                    readHeaders(input)

                    when (method) {
                        "CONNECT" -> handleConnect(target, client, output)
                        else -> handlePlainHttp(method, target, output)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "HTTP proxy handler crashed", e)
                }
            }
        } finally {
            ProxyStats.onConnectionClosed()
        }
    }

    private fun handleConnect(
        target: String,
        client: Socket,
        clientOutput: OutputStream
    ) {
        val hostPort = parseHostPort(target, defaultPort = 443)

        if (hostPort == null) {
            sendBadRequest(clientOutput)
            return
        }

        val remoteSocket = Socket()

        try {
            remoteSocket.tcpNoDelay = true
            remoteSocket.connect(
                InetSocketAddress(hostPort.host, hostPort.port),
                15_000
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed CONNECT to ${hostPort.host}:${hostPort.port}", e)
            sendBadGateway(clientOutput)
            return
        }

        remoteSocket.use { remote ->
            clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientOutput.flush()

            Log.d(TAG, "CONNECT tunnel established to ${hostPort.host}:${hostPort.port}")

            relay(client, remote)
        }
    }

    private fun handlePlainHttp(
        method: String,
        target: String,
        clientOutput: OutputStream
    ) {
        val parsed = parseHttpUrl(target)

        if (parsed == null) {
            sendBadRequest(clientOutput)
            return
        }

        val remoteSocket = Socket()

        try {
            remoteSocket.tcpNoDelay = true
            remoteSocket.connect(
                InetSocketAddress(parsed.host, parsed.port),
                15_000
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed HTTP request to ${parsed.host}:${parsed.port}", e)
            sendBadGateway(clientOutput)
            return
        }

        remoteSocket.use { remote ->
            val remoteOutput = remote.getOutputStream()

            val rebuiltRequest = buildString {
                append("$method ${parsed.path} HTTP/1.1\r\n")
                append("Host: ${parsed.host}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }

            remoteOutput.write(rebuiltRequest.toByteArray())
            remoteOutput.flush()

            relayOneWay(
                remote.getInputStream(),
                clientOutput,
                Direction.REMOTE_TO_CLIENT
            )
        }
    }

    private fun readRequestLine(input: InputStream): String? {
        val buffer = StringBuilder()

        while (true) {
            val b = input.read()
            if (b == -1) return null

            if (b == '\r'.code) {
                val next = input.read()
                if (next == '\n'.code) break
            } else {
                buffer.append(b.toChar())
            }

            if (buffer.length > 8192) {
                throw IllegalStateException("HTTP request line too long")
            }
        }

        return buffer.toString()
    }

    private fun readHeaders(input: InputStream) {
        var matched = 0

        while (true) {
            val current = input.read()
            if (current == -1) break

            matched = when {
                matched == 0 && current == '\r'.code -> 1
                matched == 1 && current == '\n'.code -> 2
                matched == 2 && current == '\r'.code -> 3
                matched == 3 && current == '\n'.code -> 4
                else -> 0
            }

            if (matched == 4) break
        }
    }

    private fun parseHostPort(target: String, defaultPort: Int): HostPort? {
        val cleanTarget = target.trim()

        if (cleanTarget.isBlank()) return null

        return if (cleanTarget.startsWith("[")) {
            val end = cleanTarget.indexOf("]")
            if (end == -1) return null

            val host = cleanTarget.substring(1, end)

            val port = if (cleanTarget.length > end + 2 && cleanTarget[end + 1] == ':') {
                cleanTarget.substring(end + 2).toIntOrNull() ?: defaultPort
            } else {
                defaultPort
            }

            HostPort(host, port)
        } else {
            val lastColon = cleanTarget.lastIndexOf(":")

            if (lastColon == -1) {
                HostPort(cleanTarget, defaultPort)
            } else {
                val host = cleanTarget.substring(0, lastColon)
                val port = cleanTarget.substring(lastColon + 1).toIntOrNull() ?: defaultPort

                HostPort(host, port)
            }
        }
    }

    private fun parseHttpUrl(url: String): ParsedHttpUrl? {
        if (!url.startsWith("http://")) return null

        val withoutScheme = url.removePrefix("http://")
        val slashIndex = withoutScheme.indexOf("/")

        val hostPortPart = if (slashIndex == -1) {
            withoutScheme
        } else {
            withoutScheme.substring(0, slashIndex)
        }

        val path = if (slashIndex == -1) {
            "/"
        } else {
            withoutScheme.substring(slashIndex)
        }

        val hostPort = parseHostPort(hostPortPart, defaultPort = 80) ?: return null

        return ParsedHttpUrl(
            host = hostPort.host,
            port = hostPort.port,
            path = path
        )
    }

    private fun sendBadRequest(output: OutputStream) {
        output.write(
            "HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray()
        )
        output.flush()
    }

    private fun sendBadGateway(output: OutputStream) {
        output.write(
            "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray()
        )
        output.flush()
    }

    private fun relay(client: Socket, remote: Socket) {
        val latch = CountDownLatch(2)

        Thread {
            try {
                relayOneWay(
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
                relayOneWay(
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

    private fun relayOneWay(
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

    private enum class Direction {
        CLIENT_TO_REMOTE,
        REMOTE_TO_CLIENT
    }

    data class HostPort(
        val host: String,
        val port: Int
    )

    data class ParsedHttpUrl(
        val host: String,
        val port: Int,
        val path: String
    )

    companion object {
        private const val TAG = "HttpProxyHandler"
    }
}