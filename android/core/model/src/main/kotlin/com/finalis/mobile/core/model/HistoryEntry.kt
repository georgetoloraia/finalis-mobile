package com.finalis.mobile.core.model

data class HistoryEntry(
    val txid: String,
    val height: Long,
    val status: TxStatus,
    val direction: TxDirection,
    val creditedValue: Long,
    val debitedValue: Long,
    val netValue: Long,
)
