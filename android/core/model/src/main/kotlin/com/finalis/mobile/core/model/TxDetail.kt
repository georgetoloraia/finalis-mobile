package com.finalis.mobile.core.model

data class TxDetail(
    val txid: String,
    val height: Long,
    val status: TxStatus,
    val finalizedTransitionHash: String?,
    val finalizedDepth: Long? = null,
    val creditSafe: Boolean = false,
    val inputs: List<TxInput>,
    val outputs: List<TxOutput>,
)

data class TxInput(
    val prevTxid: String,
    val prevVout: Int,
)

data class TxOutput(
    val index: Int,
    val value: Long,
    val address: String,
)
