package com.finalis.mobile.core.model

data class SubmittedTransactionRecord(
    val txid: String,
    val recipientAddress: String,
    val amountUnits: Long,
    val requestedFeeUnits: Long,
    val appliedFeeUnits: Long,
    val changeUnits: Long,
    val createdAtEpochMillis: Long,
    val consumedInputs: List<WalletOutPoint> = emptyList(),
)
