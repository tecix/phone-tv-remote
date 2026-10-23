package io.github.tecix.richtvremote.direct

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.EOFException

/**
 * Just enough of the protobuf wire format for the Android TV Remote v2 messages.
 * The few messages we need are encoded by hand, so the app needs no protobuf runtime.
 */
internal object Proto {

    fun varint(value: Long): ByteArray {
        // Negative int32 values are sent as 10-byte two's complement, which unsigned shifting gives us.
        var n = value
        val out = ByteArrayOutputStream()
        while (true) {
            val b = (n and 0x7F).toInt()
            n = n ushr 7
            if (n != 0L) {
                out.write(b or 0x80)
            } else {
                out.write(b)
                return out.toByteArray()
            }
        }
    }

    /** Encode (field number, value) pairs; numbers are varints, strings and bytes are length-delimited. */
    fun msg(vararg fields: Pair<Int, Any?>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((num, value) in fields) {
            when (value) {
                null -> Unit
                is Boolean -> {
                    out.write(varint((num shl 3).toLong()))
                    out.write(varint(if (value) 1 else 0))
                }
                is Int, is Long -> {
                    out.write(varint((num shl 3).toLong()))
                    out.write(varint((value as Number).toLong()))
                }
                is String, is ByteArray -> {
                    val data = if (value is String) value.toByteArray(Charsets.UTF_8) else value as ByteArray
                    out.write(varint((num shl 3 or 2).toLong()))
                    out.write(varint(data.size.toLong()))
                    out.write(data)
                }
                else -> error("Unsupported field type ${value::class}")
            }
        }
        return out.toByteArray()
    }

    fun frame(payload: ByteArray): ByteArray = varint(payload.size.toLong()) + payload

    fun readFrame(input: InputStream): ByteArray {
        var length = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Connection closed by the TV")
            length = length or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) throw RemoteException("Bad message length")
        }
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n < 0) throw EOFException("Connection closed by the TV")
            read += n
        }
        return buf
    }

    fun parse(data: ByteArray): Fields {
        val fields = HashMap<Int, MutableList<Any>>()
        var i = 0

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = data[i++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        while (i < data.size) {
            val key = readVarint()
            val num = (key ushr 3).toInt()
            val value: Any = when ((key and 7).toInt()) {
                0 -> readVarint()
                2 -> {
                    val n = readVarint().toInt()
                    data.copyOfRange(i, i + n).also { i += n }
                }
                1 -> data.copyOfRange(i, i + 8).also { i += 8 }
                5 -> data.copyOfRange(i, i + 4).also { i += 4 }
                else -> throw RemoteException("Unsupported protobuf wire type ${key and 7}")
            }
            fields.getOrPut(num) { mutableListOf() }.add(value)
        }
        return Fields(fields)
    }

    class Fields(private val map: Map<Int, List<Any>>) {
        operator fun contains(num: Int) = num in map

        fun long(num: Int, default: Long = 0): Long = map[num]?.firstOrNull() as? Long ?: default

        fun int(num: Int, default: Int = 0): Int = long(num, default.toLong()).toInt()

        fun sub(num: Int): Fields = (map[num]?.firstOrNull() as? ByteArray)?.let(::parse) ?: EMPTY

        fun text(num: Int): String =
            (map[num]?.firstOrNull() as? ByteArray)?.toString(Charsets.UTF_8) ?: ""

        fun isEmpty() = map.isEmpty()

        override fun toString() = map.keys.sorted().toString()

        companion object {
            val EMPTY = Fields(emptyMap())
        }
    }
}
