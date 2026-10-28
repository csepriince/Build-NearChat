package com.nearchat.app.core

import android.bluetooth.BluetoothSocket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import javax.crypto.spec.SecretKeySpec

/** Frame types of the NearChat wire protocol. */
object T {
    const val HELLO = 1
    const val ACCEPT = 2
    const val REJECT = 3
    const val TEXT = 4
    const val ACK = 5
    const val OFFER = 6
    const val FACCEPT = 7
    const val FREJECT = 8
    const val CHUNK = 9
    const val FEND = 10
    const val FCANCEL = 11
}

/** One encrypted link. Frame on the wire: [int length][12-byte IV + AES-GCM(type byte + body)]. */
class Conn(
    val socket: BluetoothSocket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    key: SecretKeySpec,
) {
    private val aes = key
    val code: String = Crypto.securityCode(key)

    @Synchronized
    fun send(type: Int, body: ByteArray = ByteArray(0)) {
        val enc = Crypto.encrypt(aes, byteArrayOf(type.toByte()) + body)
        output.writeInt(enc.size)
        output.write(enc)
        output.flush()
    }

    fun read(): Pair<Int, ByteArray> {
        val n = input.readInt()
        if (n < 29 || n > 1_000_000) throw IOException("Bad frame")
        val buf = ByteArray(n)
        input.readFully(buf)
        val p = Crypto.decrypt(aes, buf)
        return p[0].toInt() to p.copyOfRange(1, p.size)
    }

    fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /** Unauthenticated ECDH exchange; the 6-digit code lets users verify there is no man-in-the-middle. */
        fun handshake(socket: BluetoothSocket): Conn {
            val i = DataInputStream(BufferedInputStream(socket.inputStream))
            val o = DataOutputStream(BufferedOutputStream(socket.outputStream))
            val kp = Crypto.newKeyPair()
            val mine = kp.public.encoded
            o.writeInt(mine.size); o.write(mine); o.flush()
            val n = i.readInt()
            if (n < 50 || n > 200) throw IOException("Bad handshake")
            val peer = ByteArray(n)
            i.readFully(peer)
            return Conn(socket, i, o, Crypto.deriveKey(kp.private, peer))
        }
    }
}
