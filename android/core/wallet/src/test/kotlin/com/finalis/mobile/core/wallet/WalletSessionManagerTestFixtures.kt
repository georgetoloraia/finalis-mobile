package com.finalis.mobile.core.wallet

import com.finalis.mobile.core.model.ImportedWalletRecord
import com.finalis.mobile.core.model.WalletAddress
import com.finalis.mobile.core.model.WalletProfile

object WalletSessionManagerTestFixtures {
    fun wallet(
        address: String = "sc1adrqzotqb6xl76opjawtlcn5ds6msa6sqaw7dj7p",
    ): ImportedWalletRecord = ImportedWalletRecord(
        privateKeyHex = "00".repeat(32),
        walletProfile = WalletProfile(
            address = WalletAddress(address),
            publicKeyHex = "11".repeat(32),
            label = "Finalis Wallet",
        ),
        importedAtEpochMillis = 1L,
    )
}
