package com.finalis.mobile.core.model

data class BroadcastResult(
    val accepted: Boolean,
    val txid: String?,
    val status: TxStatus,
    val errorCode: BroadcastErrorCode? = null,
    val error: String?,
)
