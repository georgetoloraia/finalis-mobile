package com.finalis.mobile.core.model

data class AddressValidationResult(
    val valid: Boolean,
    val normalizedAddress: String? = null,
    val hrp: String? = null,
    val networkHint: String? = null,
    val serverNetworkHrp: String? = null,
    val serverNetworkMatch: Boolean? = null,
    val addressType: String? = null,
    val pubkeyHashHex: String? = null,
    val scriptPubKeyHex: String? = null,
    val scriptHashHex: String? = null,
    val error: String? = null,
)
