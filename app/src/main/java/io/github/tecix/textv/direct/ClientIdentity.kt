package io.github.tecix.textv.direct

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Principal
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * The TLS client certificate that identifies this phone to the TV.
 *
 * The Remote v2 protocol authenticates the remote by its certificate: after pairing,
 * the TV remembers its public key. The certificate is self-signed, created on first use
 * and kept in app-private storage, so pairing survives restarts.
 */
class ClientIdentity private constructor(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {
    val publicKey: RSAPublicKey get() = certificate.publicKey as RSAPublicKey

    /** An SSLContext that always presents our certificate and accepts the TV's self-signed one. */
    fun sslContext(): SSLContext = SSLContext.getInstance("TLS").apply {
        init(arrayOf(FixedKeyManager()), arrayOf(TrustAllManager), SecureRandom())
    }

    // The default key manager filters by the issuers the server asks for, which
    // would never pick a self-signed certificate. Always offer ours.
    private inner class FixedKeyManager : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = null
        override fun getCertificateChain(alias: String?) = arrayOf(certificate)
        override fun getPrivateKey(alias: String?) = privateKey
    }

    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val ALIAS = "client"

        @Volatile
        private var cached: ClientIdentity? = null

        private fun dir(context: Context) = File(context.filesDir, "remote")

        fun get(context: Context): ClientIdentity = cached ?: synchronized(this) {
            cached ?: loadOrCreate(context).also { cached = it }
        }

        /** Throw away the certificate; every TV has to be paired again afterwards. */
        fun reset(context: Context) = synchronized(this) {
            dir(context).deleteRecursively()
            cached = null
        }

        private fun loadOrCreate(context: Context): ClientIdentity {
            val dir = dir(context).apply { mkdirs() }
            val keyFile = File(dir, "client.key")
            val certFile = File(dir, "client.der")
            if (keyFile.exists() && certFile.exists()) {
                runCatching {
                    val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                    return ClientIdentity(key, parseCertificate(certFile.readBytes()))
                }
            }

            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val certDer = buildCertificate(pair.public.encoded, pair.private, "atvremote/${Build.MODEL}")
            keyFile.writeBytes(pair.private.encoded)
            certFile.writeBytes(certDer)
            return ClientIdentity(pair.private, parseCertificate(certDer))
        }

        private fun parseCertificate(der: ByteArray) =
            CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate

        // ── Minimal DER encoding, just enough for a self-signed X.509 v3 certificate ──

        /** AlgorithmIdentifier { sha256WithRSAEncryption, NULL } */
        private val SHA256_WITH_RSA = hex("300d06092a864886f70d01010b0500")

        /** OID 2.5.4.3 (commonName) */
        private val COMMON_NAME_OID = hex("0603550403")

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        private fun der(tag: Int, content: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            val n = content.size
            if (n < 0x80) {
                out.write(n)
            } else {
                val raw = BigInteger.valueOf(n.toLong()).toByteArray().dropWhile { it == 0.toByte() }
                out.write(0x80 or raw.size)
                out.write(raw.toByteArray())
            }
            out.write(content)
            return out.toByteArray()
        }

        // BigInteger.toByteArray() already keeps the sign bit clear for positive numbers.
        private fun derInt(n: BigInteger) = der(0x02, n.toByteArray())

        private fun derName(commonName: String): ByteArray {
            val atv = der(0x30, COMMON_NAME_OID + der(0x0C, commonName.toByteArray()))
            return der(0x30, der(0x31, atv))
        }

        internal fun buildCertificate(spki: ByteArray, key: PrivateKey, commonName: String): ByteArray {
            val utc = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val notBefore = Date(System.currentTimeMillis() - 24L * 3600 * 1000)
            val validity = der(
                0x30,
                der(0x17, utc.format(notBefore).toByteArray()) + der(0x18, "20991231235959Z".toByteArray()),
            )
            val name = derName(commonName)
            val tbs = der(
                0x30,
                der(0xA0, derInt(BigInteger.valueOf(2))) + // [0] EXPLICIT version v3
                    derInt(BigInteger(63, SecureRandom())) +
                    SHA256_WITH_RSA + name + validity + name + spki,
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(key)
                update(tbs)
                sign()
            }
            return der(0x30, tbs + SHA256_WITH_RSA + der(0x03, byteArrayOf(0) + signature))
        }
    }
}
