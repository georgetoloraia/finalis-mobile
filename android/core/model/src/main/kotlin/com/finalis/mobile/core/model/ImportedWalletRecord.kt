package com.finalis.mobile.core.model

data class ImportedWalletRecord(
    val privateKeyHex: String,
    val walletProfile: WalletProfile,
    val importedAtEpochMillis: Long,
)
