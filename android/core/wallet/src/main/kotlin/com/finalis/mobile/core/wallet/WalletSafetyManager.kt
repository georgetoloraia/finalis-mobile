package com.finalis.mobile.core.wallet

class WalletSafetyManager(
    private val walletSessionManager: WalletSessionManager,
    private val submittedTransactionManager: SubmittedTransactionManager,
) {
    fun resetWallet() {
        walletSessionManager.clearWallet()
        submittedTransactionManager.clearAll()
    }
}
