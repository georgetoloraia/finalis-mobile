package com.finalis.mobile.core.model

data class WalletUtxo(
    val txid: String,
    val vout: Int,
    val valueUnits: Long,
    val height: Long,
    val scriptPubKeyHex: String,
)
