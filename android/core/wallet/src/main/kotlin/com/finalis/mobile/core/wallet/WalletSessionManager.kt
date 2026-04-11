package com.finalis.mobile.core.wallet

import com.finalis.mobile.core.crypto.FinalisAddressCodec
import com.finalis.mobile.core.crypto.FinalisKeyDerivation
import com.finalis.mobile.core.model.ImportedWalletRecord
import com.finalis.mobile.core.model.WalletAddress
import com.finalis.mobile.core.model.WalletProfile
import com.finalis.mobile.core.storage.WalletStore

class WalletSessionManager(
    private val walletStore: WalletStore,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun loadCurrentWallet(): ImportedWalletRecord? = walletStore.load()

    fun importAndPersist(
        privateKeyHex: String,
        label: String = "Primary wallet",
    ): ImportedWalletRecord {
        val keyMaterial = FinalisKeyDerivation.deriveKeyMaterial(privateKeyHex)
        val address = FinalisAddressCodec.deriveAddressFromPublicKey(
            keyMaterial.publicKeyHex,
            FinalisMainnet.EXPECTED_HRP,
        )
        val record = ImportedWalletRecord(
            privateKeyHex = keyMaterial.privateKeyHex,
            walletProfile = WalletProfile(
                address = WalletAddress(address),
                publicKeyHex = keyMaterial.publicKeyHex,
                label = label,
            ),
            importedAtEpochMillis = nowEpochMillis(),
        )
        walletStore.save(record)
        return record
    }

    fun clearWallet() {
        walletStore.clear()
    }

    fun exportPrivateKeyHex(record: ImportedWalletRecord): String =
        FinalisKeyDerivation.normalizePrivateKeyHex(record.privateKeyHex)
}
