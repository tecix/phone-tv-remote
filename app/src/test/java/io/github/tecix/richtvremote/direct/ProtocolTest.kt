package io.github.tecix.richtvremote.direct

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

class ProtocolTest {

    @Test
    fun appLinkUsesExistingPacketAndFraming() {
        val uri = "youtube://search?query=Tom%20%26%20Jerry&method=play"
        val payload = appLinkMessage(uri)
        val framed = Proto.readFrame(Proto.frame(payload).inputStream())!!
        assertEquals(uri, Proto.parse(framed).sub(90).text(1))
    }

    @Test
    fun protoRoundTrip() {
        val inner = Proto.msg(1 to "atvremote", 2 to 300)
        val data = Proto.msg(1 to 2, 2 to 200, 10 to inner, 4 to -1, 5 to true)
        val parsed = Proto.parse(data)
        assertEquals(2, parsed.int(1))
        assertEquals(200, parsed.int(2))
        assertEquals("atvremote", parsed.sub(10).text(1))
        assertEquals(300, parsed.sub(10).int(2))
        // Negative int32 travels as a 10-byte varint and reads back as -1.
        assertEquals(-1L, parsed.long(4))
        assertEquals(1, parsed.int(5))
    }

    @Test
    fun frameRoundTrip() {
        val payload = ByteArray(300) { it.toByte() }
        val read = Proto.readFrame(Proto.frame(payload).inputStream())
        assertArrayEquals(payload, read)
    }

    @Test
    fun selfSignedCertificateParses() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val der = ClientIdentity.buildCertificate(pair.public.encoded, pair.private, "atvremote/test")
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
        assertEquals(pair.public, cert.publicKey)
        assertEquals(3, cert.version)
        assertTrue(cert.subjectX500Principal.name.contains("atvremote/test"))
        cert.checkValidity()
        cert.verify(pair.public) // signature over the TBS is valid
    }

    @Test
    fun pairingDigestMatchesPythonImplementation() {
        // Too small for KeyFactory, so implement the interface directly; only n and e matter here.
        fun key(n: String, e: Long) = object : RSAPublicKey {
            override fun getModulus() = BigInteger(n, 16)
            override fun getPublicExponent(): BigInteger = BigInteger.valueOf(e)
            override fun getAlgorithm() = "RSA"
            override fun getFormat() = null
            override fun getEncoded() = null
        }
        val client = key("C0FFEE1234567890ABCDEF1122334455667788990011223344556677889900AA", 65537)
        val server = key("80F00DFACE00112233445566778899AABBCCDDEEFF", 3)
        // Expected values computed with remote_v2.py's algorithm.
        assertEquals(
            "9c55f7197cb96481e99f4858b5002b8f56c4974eb04e5f410f547788d868d856",
            pairingDigest(client, server, "9C1A2B").toHex(),
        )
        assertEquals(
            "0da17adc95e837333aafd40a7d4773ed0fc7cb17395ca220aa8c8b9d3701c432",
            pairingDigest(client, server, "0D00FF").toHex(),
        )
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
