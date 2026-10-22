package io.github.tecix.textv.direct

import android.util.Log
import io.github.tecix.textv.RemoteBackend
import io.github.tecix.textv.TvKey
import io.github.tecix.textv.direct.Proto.msg
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket

/*
 * Android TV Remote protocol v2, as spoken by the Google TV phone app.
 *
 * - Port 6467, pairing ("Polo"): the TV shows a 6-digit hex code that the user types
 *   into the phone. Afterwards the TV trusts our TLS client certificate.
 * - Port 6466, remote control: key presses, text input and app links go to the TV;
 *   volume, power and current-app updates come back.
 *
 * Every message is a varint length-prefixed protobuf.
 */

const val REMOTE_PORT = 6466
const val PAIRING_PORT = 6467

open class RemoteException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ConnectException(message: String, cause: Throwable? = null) : RemoteException(message, cause)
class NotPairedException(cause: Throwable? = null) : RemoteException("The TV doesn't know this phone yet. Pair it first.", cause)
class PairingException(message: String, cause: Throwable? = null) : RemoteException(message, cause)

data class DeviceInfo(val manufacturer: String, val model: String)

data class VolumeInfo(val level: Int, val maximum: Int, val muted: Boolean)

// RemoteDirection
private const val DIRECTION_START_LONG = 1
private const val DIRECTION_END_LONG = 2
private const val DIRECTION_SHORT = 3

// Feature bits we announce: PING | KEY | IME | POWER | VOLUME | APP_LINK
private const val FEATURES = 1 or 2 or 4 or 32 or 64 or 512

// Polo (pairing) constants
private const val POLO_PROTOCOL_VERSION = 2
private const val POLO_STATUS_OK = 200
private const val ENCODING_HEXADECIMAL = 3
private const val ROLE_INPUT = 1
private const val CODE_LENGTH = 6

// The TV pings every few seconds; this much silence means the link is dead.
private const val READ_TIMEOUT_MS = 30_000

private const val TAG = "RemoteV2"

// Pause between keys of a macro, so the TV's menus can catch up.
private const val MACRO_GAP_MS = 300L

private fun openTls(identity: ClientIdentity, host: String, port: Int, timeoutMs: Int): SSLSocket {
    val raw = Socket()
    try {
        raw.connect(InetSocketAddress(host, port), timeoutMs)
        raw.soTimeout = timeoutMs
        raw.tcpNoDelay = true
    } catch (e: IOException) {
        raw.close()
        throw ConnectException("Can't reach $host:$port", e)
    }
    val sock = identity.sslContext().socketFactory.createSocket(raw, host, port, true) as SSLSocket
    try {
        sock.startHandshake()
    } catch (e: IOException) {
        sock.close()
        // A TV that doesn't know our certificate aborts the handshake.
        throw NotPairedException(e)
    }
    return sock
}

/** Big-endian bytes without the sign byte BigInteger adds. */
private fun unsigned(n: BigInteger): ByteArray {
    val bytes = n.toByteArray()
    return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}

// ─────────────────────────────────────────────────────────────────────────────
// Pairing (port 6467)
// ─────────────────────────────────────────────────────────────────────────────

/** SHA-256 over both public keys and the last 4 hex digits of the code; its first byte must match the code's first 2. */
internal fun pairingDigest(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray =
    MessageDigest.getInstance("SHA-256").run {
        update(unsigned(client.modulus))
        update(unsigned(client.publicExponent))
        update(unsigned(server.modulus))
        update(unsigned(server.publicExponent))
        update(ByteArray(2) { code.substring(2 + it * 2, 4 + it * 2).toInt(16).toByte() })
        digest()
    }

/** One pairing attempt: [start] makes the TV show a code, [finish] completes it. Both block. */
class PairingSession(
    private val identity: ClientIdentity,
    val host: String,
    private val clientName: String,
    private val timeoutMs: Int = 10_000,
) {
    private var sock: SSLSocket? = null
    private var input: InputStream? = null
    private var serverKey: RSAPublicKey? = null

    /** False once the session failed or finished; a new one is needed to try again. */
    val isOpen get() = sock != null

    /** Begin pairing; the TV displays a code afterwards. Returns the TV's name. */
    fun start(): String {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(host, PAIRING_PORT), timeoutMs)
            raw.soTimeout = timeoutMs
        } catch (e: IOException) {
            raw.close()
            throw ConnectException("Can't reach $host:$PAIRING_PORT", e)
        }
        try {
            val s = identity.sslContext().socketFactory.createSocket(raw, host, PAIRING_PORT, true) as SSLSocket
            sock = s
            s.startHandshake()
            input = BufferedInputStream(s.inputStream)
            serverKey = s.session.peerCertificates[0].publicKey as RSAPublicKey

            val encoding = msg(1 to ENCODING_HEXADECIMAL, 2 to CODE_LENGTH)
            send(10, msg(1 to "atvremote", 2 to clientName)) // PairingRequest
            val ack = expect(11)
            send(20, msg(1 to encoding, 3 to ROLE_INPUT)) // Options
            expect(20)
            send(30, msg(1 to encoding, 2 to ROLE_INPUT)) // Configuration
            expect(31)
            return ack.text(1).ifEmpty { host }
        } catch (e: PairingException) {
            close()
            throw e
        } catch (e: Exception) {
            close()
            throw PairingException("Pairing failed: ${e.message}", e)
        }
    }

    /** Send the code shown on the TV. Throws [PairingException] if it is wrong. */
    fun finish(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (code.length != CODE_LENGTH || code.any { it !in "0123456789ABCDEF" }) {
            throw PairingException("Enter the 6 characters shown on the TV.")
        }
        val server = serverKey ?: throw PairingException("Pairing was not started.")
        val client = identity.publicKey

        val digest = pairingDigest(client, server, code)
        // The first byte of the code is a checksum, so typos are caught before the TV sees them.
        if ((digest[0].toInt() and 0xFF) != code.substring(0, 2).toInt(16)) {
            throw PairingException("Wrong code. Check the TV and try again.")
        }
        try {
            send(40, msg(1 to digest)) // Secret
            expect(41)
        } catch (e: PairingException) {
            throw e
        } catch (e: Exception) {
            throw PairingException("Pairing failed: ${e.message}", e)
        } finally {
            close()
        }
    }

    fun close() {
        runCatching { sock?.close() }
        sock = null
    }

    private fun send(field: Int, payload: ByteArray) {
        val s = sock ?: throw PairingException("Pairing was not started.")
        s.outputStream.apply {
            write(Proto.frame(msg(1 to POLO_PROTOCOL_VERSION, 2 to POLO_STATUS_OK, field to payload)))
            flush()
        }
    }

    private fun expect(field: Int): Proto.Fields {
        val m = Proto.parse(Proto.readFrame(input ?: throw PairingException("Pairing was not started.")))
        val status = m.int(2)
        if (status != POLO_STATUS_OK) throw PairingException("The TV rejected pairing (status $status).")
        if (field !in m) throw PairingException("Unexpected reply from the TV.")
        return m.sub(field)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Remote control (port 6466)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Connection to the TV's remote service.
 *
 * [connect] blocks. After that, a reader thread answers pings and reports state through
 * [Listener], whose methods are called from that thread. Commands never block: they go out
 * in order on a writer thread.
 */
class AndroidTvRemote(
    private val identity: ClientIdentity,
    val host: String,
    private val clientName: String,
    private val timeoutMs: Int = 8_000,
) : RemoteBackend {

    interface Listener {
        fun onDisconnected(error: Throwable?)
        fun onVolume(volume: VolumeInfo)
        fun onPower(on: Boolean)
        fun onCurrentApp(packageName: String)
    }

    @Volatile private var sock: SSLSocket? = null
    @Volatile private var active = false
    @Volatile private var closing = false
    @Volatile var deviceInfo: DeviceInfo? = null
        private set
    private var reader: Thread? = null
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "remote-v2-writer").apply { isDaemon = true } }
    private val sendLock = Any()

    // Text field mirror: the protocol replaces the whole field on every edit.
    @Volatile private var imeCounter = 0L
    @Volatile private var imeFieldCounter = 0L
    @Volatile private var imeText = ""

    var listener: Listener? = null

    val connected get() = sock != null && active

    /** Connect and finish the handshake. Throws [NotPairedException] if pairing is needed. */
    fun connect(): DeviceInfo {
        val s = openTls(identity, host, REMOTE_PORT, timeoutMs)
        sock = s
        closing = false
        val input = BufferedInputStream(s.inputStream)
        try {
            // The TV opens with RemoteConfigure, then RemoteSetActive; we're ready once both are answered.
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!active) {
                if (System.currentTimeMillis() > deadline) throw ConnectException("The TV did not finish the handshake.")
                handle(Proto.parse(Proto.readFrame(input)))
            }
        } catch (e: RemoteException) {
            closeSocket()
            throw e
        } catch (e: IOException) {
            closeSocket()
            // TLS 1.3 reports a rejected client certificate only on the first read.
            if (deviceInfo == null) throw NotPairedException(e)
            throw ConnectException(e.message ?: "Connection failed", e)
        }

        s.soTimeout = READ_TIMEOUT_MS
        reader = Thread({ readLoop(s, input) }, "remote-v2-reader").apply {
            isDaemon = true
            start()
        }
        return deviceInfo!!
    }

    fun disconnect() {
        closing = true
        closeSocket()
        writer.shutdown()
    }

    // ── Commands ────────────────────────────────────────────────────────────

    override fun press(key: TvKey) {
        if (key == TvKey.DEL && imeText.isNotEmpty()) {
            setText(imeText.dropLast(1))
            return
        }
        if (key != TvKey.FORWARD_DEL) {
            // Any navigation or confirmation probably leaves the text field.
            imeText = ""
        }
        sendKey(key, DIRECTION_SHORT)
    }

    override fun longPress(key: TvKey) {
        writer.execute {
            runCatching {
                sendNow(keyMessage(key, DIRECTION_START_LONG))
                Thread.sleep(600)
                sendNow(keyMessage(key, DIRECTION_END_LONG))
            }
        }
    }

    override fun pressSequence(keys: List<TvKey>) {
        imeText = ""
        writer.execute {
            runCatching {
                for ((i, key) in keys.withIndex()) {
                    if (i > 0) Thread.sleep(MACRO_GAP_MS)
                    sendNow(keyMessage(key, DIRECTION_SHORT))
                }
            }
        }
    }

    fun keyDown(key: TvKey) = sendKey(key, DIRECTION_START_LONG)

    fun keyUp(key: TvKey) = sendKey(key, DIRECTION_END_LONG)

    override fun sendText(text: String) = setText(imeText + text)

    override fun resetText() {
        imeText = ""
    }

    override fun launchApp(packageName: String) = sendAppLink("market://launch?id=$packageName")

    /** Open a deep link or `market://launch?id=<package>` on the TV. */
    fun sendAppLink(link: String) = send(msg(90 to msg(1 to link)))

    // ── Internals ───────────────────────────────────────────────────────────

    private fun keyMessage(key: TvKey, direction: Int) = msg(10 to msg(1 to key.code, 2 to direction))

    private fun sendKey(key: TvKey, direction: Int) = send(keyMessage(key, direction))

    private fun setText(text: String) {
        imeText = text
        val cursor = text.length - 1
        val field = msg(1 to cursor, 2 to cursor, 3 to text)
        val edit = msg(1 to 1, 2 to field)
        send(msg(21 to msg(1 to imeCounter, 2 to imeFieldCounter, 3 to edit)))
    }

    private fun send(payload: ByteArray) {
        if (writer.isShutdown) return
        writer.execute {
            try {
                sendNow(payload)
            } catch (e: IOException) {
                Log.w(TAG, "Send failed: ${e.message}")
            }
        }
    }

    private fun sendNow(payload: ByteArray) {
        val s = sock ?: throw ConnectException("Not connected")
        synchronized(sendLock) {
            s.outputStream.apply {
                write(Proto.frame(payload))
                flush()
            }
        }
    }

    private fun handle(m: Proto.Fields) {
        when {
            1 in m -> { // RemoteConfigure
                val info = m.sub(1).sub(2)
                deviceInfo = DeviceInfo(
                    manufacturer = info.text(2).ifEmpty { "Unknown" },
                    model = info.text(1).ifEmpty { "Android TV" },
                )
                val device = msg(1 to clientName, 2 to "Android", 3 to 1, 4 to "1", 5 to "atvremote", 6 to "1.0.0")
                sendNow(msg(1 to msg(1 to FEATURES, 2 to device)))
            }
            2 in m -> { // RemoteSetActive
                sendNow(msg(2 to msg(1 to FEATURES)))
                active = true
            }
            8 in m -> sendNow(msg(9 to msg(1 to m.sub(8).long(1)))) // RemotePingRequest
            3 in m -> Log.w(TAG, "TV reported an error: ${m.sub(3)}") // RemoteError
            20 in m -> { // RemoteImeKeyInject: current app and text field
                val inject = m.sub(20)
                val pkg = inject.sub(1).text(12)
                updateTextField(inject.sub(2))
                if (pkg.isNotEmpty()) listener?.onCurrentApp(pkg)
            }
            21 in m -> { // RemoteImeBatchEdit
                val edit = m.sub(21)
                imeCounter = edit.long(1, imeCounter)
                imeFieldCounter = edit.long(2, imeFieldCounter)
            }
            22 in m -> updateTextField(m.sub(22).sub(2)) // RemoteImeShowRequest
            40 in m -> listener?.onPower(m.sub(40).long(1) != 0L) // RemoteStart
            50 in m -> { // RemoteSetVolumeLevel
                val vol = m.sub(50)
                listener?.onVolume(
                    VolumeInfo(level = vol.int(7), maximum = vol.int(6).takeIf { it > 0 } ?: 100, muted = vol.long(8) != 0L)
                )
            }
        }
    }

    private fun updateTextField(status: Proto.Fields) {
        if (status.isEmpty() || 1 !in status) return
        val counter = status.long(1)
        if (counter != imeFieldCounter) {
            imeFieldCounter = counter
            imeText = status.text(2)
        }
    }

    private fun readLoop(s: SSLSocket, input: InputStream) {
        var error: Throwable? = null
        try {
            while (sock === s) handle(Proto.parse(Proto.readFrame(input)))
        } catch (e: Exception) {
            if (!closing) {
                Log.i(TAG, "Remote connection to $host ended: ${e.message}")
                error = e
            }
        } finally {
            val wasClosing = closing
            if (sock === s) closeSocket()
            if (!wasClosing) listener?.onDisconnected(error)
        }
    }

    private fun closeSocket() {
        val s = sock
        sock = null
        active = false
        runCatching { s?.close() }
    }
}
