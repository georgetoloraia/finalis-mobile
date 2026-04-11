package com.finalis.mobile.core.crypto

import java.security.MessageDigest
import org.bouncycastle.crypto.digests.RIPEMD160Digest

object FinalisAddressCodec {
    private const val Alphabet = "abcdefghijklmnopqrstuvwxyz234567"

    data class DecodedAddress(
        val hrp: String,
        val addrType: Int,
        val pubkeyHash: ByteArray,
    )

    fun deriveAddressFromPrivateKey(rawPrivateKeyHex: String, hrp: String = "sc"): String {
        val keyMaterial = FinalisKeyDerivation.deriveKeyMaterial(rawPrivateKeyHex)
        return deriveAddressFromPublicKey(keyMaterial.publicKeyHex, hrp)
    }

    fun deriveAddressFromPublicKey(publicKeyHex: String, hrp: String = "sc"): String {
        require(hrp == "sc" || hrp == "tsc") { "Unsupported address HRP" }
        val publicKey = publicKeyHex.hexToBytes()
        require(publicKey.size == 32) { "Public key must be 32 bytes" }
        val payload = byteArrayOf(0x00) + h160(publicKey)
        val checksum = doubleSha256(hrp.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) + payload).copyOfRange(0, 4)
        return buildString {
            append(hrp)
            append('1')
            append(base32Encode(payload + checksum))
        }
    }

    fun decodeAddress(address: String): DecodedAddress {
        val separatorIndex = address.indexOf('1')
        require(separatorIndex > 0) { "Missing address separator" }
        val hrp = address.substring(0, separatorIndex)
        require(hrp == "sc" || hrp == "tsc") { "Unsupported address HRP" }
        val data = base32Decode(address.substring(separatorIndex + 1))
        require(data.size == 25) { "Decoded address size mismatch" }
        val payload = data.copyOfRange(0, 21)
        val checksum = data.copyOfRange(21, 25)
        val expectedChecksum = doubleSha256(hrp.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) + payload).copyOfRange(0, 4)
        require(checksum.contentEquals(expectedChecksum)) { "Address checksum mismatch" }
        require(payload[0] == 0.toByte()) { "Unsupported address type" }
        return DecodedAddress(
            hrp = hrp,
            addrType = 0,
            pubkeyHash = payload.copyOfRange(1, payload.size),
        )
    }

    fun scriptPubKeyHexFromAddress(address: String): String {
        val decoded = decodeAddress(address)
        return (byteArrayOf(0x76, 0xa9.toByte(), 0x14) + decoded.pubkeyHash + byteArrayOf(0x88.toByte(), 0xac.toByte())).toHex()
    }

    fun scriptHashHexFromAddress(address: String): String {
        val scriptPubKey = scriptPubKeyHexFromAddress(address).hexToBytes()
        return MessageDigest.getInstance("SHA-256").digest(scriptPubKey).toHex()
    }

    fun addressFromP2pkhScriptHex(scriptPubKeyHex: String, hrp: String): String? =
        addressFromP2pkhScript(scriptPubKeyHex.hexToBytes(), hrp)

    fun addressFromP2pkhScript(scriptPubKey: ByteArray, hrp: String): String? {
        if (scriptPubKey.size != 25) return null
        if (scriptPubKey[0] != 0x76.toByte() || scriptPubKey[1] != 0xa9.toByte() || scriptPubKey[2] != 0x14.toByte()) {
            return null
        }
        if (scriptPubKey[23] != 0x88.toByte() || scriptPubKey[24] != 0xac.toByte()) return null
        val payload = byteArrayOf(0x00) + scriptPubKey.copyOfRange(3, 23)
        val checksum = doubleSha256(hrp.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) + payload).copyOfRange(0, 4)
        return buildString {
            append(hrp)
            append('1')
            append(base32Encode(payload + checksum))
        }
    }

    private fun h160(input: ByteArray): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(input)
        val ripemd = RIPEMD160Digest()
        ripemd.update(sha256, 0, sha256.size)
        val out = ByteArray(20)
        ripemd.doFinal(out, 0)
        return out
    }

    private fun doubleSha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(input))
    }

    private fun base32Encode(data: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                output.append(Alphabet[(buffer shr (bits - 5)) and 0x1f])
                bits -= 5
            }
        }
        if (bits > 0) {
            output.append(Alphabet[(buffer shl (5 - bits)) and 0x1f])
        }
        return output.toString()
    }

    private fun base32Decode(text: String): ByteArray {
        require(text.isNotEmpty()) { "Empty base32 payload" }
        val out = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (character in text) {
            val value = Alphabet.indexOf(character)
            require(value >= 0) { "Invalid base32 character" }
            buffer = (buffer shl 5) or value
            bits += 5
            while (bits >= 8) {
                out += ((buffer shr (bits - 8)) and 0xff).toByte()
                bits -= 8
            }
        }
        require(bits == 0 || (buffer and ((1 shl bits) - 1)) == 0) { "Non-canonical base32 tail" }
        return out.toByteArray()
    }
}
