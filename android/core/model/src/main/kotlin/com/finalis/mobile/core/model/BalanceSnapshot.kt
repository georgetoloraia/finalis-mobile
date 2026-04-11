package com.finalis.mobile.core.model

data class BalanceSnapshot(
    val address: WalletAddress,
    val confirmedUnits: Long,
    val asset: String,
    val tipHeight: Long,
    val tipHash: String,
)
