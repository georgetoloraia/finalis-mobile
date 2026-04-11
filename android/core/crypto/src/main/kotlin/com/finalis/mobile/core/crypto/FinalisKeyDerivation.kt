package com.finalis.mobile.core.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

data class DerivedKeyMaterial(
    val privateKeyHex: String,
    val publicKeyHex: String,
)

class InvalidPrivateKeyException(message: String) : IllegalArgumentException(message)

object FinalisKeyDerivation {
    fun normalizePrivateKeyHex(rawInput: String): String {
        val normalized = rawInput.trim().lowercase()
        if (normalized.length != 64) {
            throw InvalidPrivateKeyException("Private key must be exactly 32 bytes encoded as 64 hex characters")
        }
        if (!normalized.matches(Regex("^[0-9a-f]{64}$"))) {
            throw InvalidPrivateKeyException("Private key must contain only hexadecimal characters")
        }
        return normalized
    }

    fun deriveKeyMaterial(rawInput: String): DerivedKeyMaterial {
        val privateKeyHex = normalizePrivateKeyHex(rawInput)
        val seed = privateKeyHex.hexToBytes()
        val publicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        return DerivedKeyMaterial(
            privateKeyHex = privateKeyHex,
            publicKeyHex = publicKey.toHex(),
        )
    }

    fun signEd25519(message: ByteArray, rawInput: String): ByteArray {
        val privateKey = Ed25519PrivateKeyParameters(normalizePrivateKeyHex(rawInput).hexToBytes(), 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }
}

fun String.hexToBytes(): ByteArray {
    val out = ByteArray(length / 2)
    for (index in out.indices) {
        val offset = index * 2
        out[index] = substring(offset, offset + 2).toInt(16).toByte()
    }
    return out
}

fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
