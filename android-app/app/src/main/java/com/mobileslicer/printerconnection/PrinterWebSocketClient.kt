package com.mobileslicer.printerconnection

import android.util.Base64
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

internal class PrinterWebSocketClient(
    private val runNetwork: (() -> NetworkResult) -> NetworkResult
) {
    fun sendText(host: String, port: Int, path: String, payload: String): NetworkResult =
        withSession(host = host, port = port, path = path, userAgent = "MobileSlicer websocket-client") { session ->
            session.sendText(payload)
            NetworkResult.Success
        }

    fun withSession(
        host: String,
        port: Int,
        path: String,
        userAgent: String,
        block: (PrinterWebSocketSession) -> NetworkResult
    ): NetworkResult {
        return runNetwork {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5_000)
                socket.soTimeout = 10_000
                val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val key = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
                val output = socket.getOutputStream()
                output.write(
                    buildString {
                        append("GET $path HTTP/1.1\r\n")
                        append("Host: $host:$port\r\n")
                        append("Upgrade: websocket\r\n")
                        append("Connection: Upgrade\r\n")
                        append("Sec-WebSocket-Key: $key\r\n")
                        append("Sec-WebSocket-Version: 13\r\n")
                        append("User-Agent: $userAgent\r\n")
                        append("\r\n")
                    }.toByteArray(StandardCharsets.UTF_8)
                )
                output.flush()

                val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                val statusLine = reader.readLine().orEmpty()
                if (!statusLine.contains(" 101 ")) {
                    return@runNetwork NetworkResult.Failure("Printer WebSocket handshake failed: ${statusLine.ifBlank { "no response" }}")
                }
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val result = block(PrinterWebSocketSession(socket))
                runCatching {
                    output.write(maskedWebSocketCloseFrame())
                    output.flush()
                }
                result
            }
        }
    }
}

internal class PrinterWebSocketSession(private val socket: Socket) {
    fun sendText(text: String) {
        val output = socket.getOutputStream()
        output.write(maskedWebSocketTextFrame(text.toByteArray(StandardCharsets.UTF_8)))
        output.flush()
    }

    fun readText(timeoutMs: Int): String? {
        val previousTimeout = socket.soTimeout
        socket.soTimeout = timeoutMs
        return try {
            readWebSocketTextFrame(socket.getInputStream())
        } catch (_: SocketTimeoutException) {
            null
        } finally {
            socket.soTimeout = previousTimeout
        }
    }
}

private fun maskedWebSocketTextFrame(payload: ByteArray): ByteArray =
    maskedWebSocketFrame(opcode = 0x1, payload = payload)

private fun maskedWebSocketCloseFrame(): ByteArray =
    maskedWebSocketFrame(opcode = 0x8, payload = ByteArray(0))

private fun maskedWebSocketFrame(opcode: Int, payload: ByteArray): ByteArray {
    val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
    val header = mutableListOf<Byte>()
    header += (0x80 or (opcode and 0x0f)).toByte()
    when {
        payload.size <= 125 -> header += (0x80 or payload.size).toByte()
        payload.size <= 65_535 -> {
            header += (0x80 or 126).toByte()
            header += ((payload.size ushr 8) and 0xff).toByte()
            header += (payload.size and 0xff).toByte()
        }
        else -> {
            header += (0x80 or 127).toByte()
            val length = payload.size.toLong()
            for (shift in 56 downTo 0 step 8) {
                header += ((length ushr shift) and 0xff).toByte()
            }
        }
    }
    header.addAll(mask.asList())
    val maskedPayload = ByteArray(payload.size) { index ->
        (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
    }
    return header.toByteArray() + maskedPayload
}

private fun readWebSocketTextFrame(input: java.io.InputStream): String? {
    while (true) {
        val first = input.read()
        if (first < 0) return null
        val opcode = first and 0x0f
        val second = input.read()
        if (second < 0) return null
        val masked = second and 0x80 != 0
        var length = (second and 0x7f).toLong()
        if (length == 126L) {
            length = ((input.read() and 0xff) shl 8 or (input.read() and 0xff)).toLong()
        } else if (length == 127L) {
            length = 0L
            repeat(8) {
                length = (length shl 8) or (input.read().toLong() and 0xffL)
            }
        }
        val mask = if (masked) ByteArray(4) { input.read().toByte() } else null
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < payload.size) {
            val count = input.read(payload, read, payload.size - read)
            if (count < 0) return null
            read += count
        }
        if (mask != null) {
            for (index in payload.indices) {
                payload[index] = (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
            }
        }
        if (opcode == 0x1) return payload.toString(StandardCharsets.UTF_8)
        if (opcode == 0x8) return null
    }
}
