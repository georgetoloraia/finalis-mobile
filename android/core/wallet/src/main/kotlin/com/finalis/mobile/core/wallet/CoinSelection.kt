package com.finalis.mobile.core.wallet

import com.finalis.mobile.core.model.WalletUtxo

object CoinSelection {
    fun deterministicLargestFirst(utxos: List<WalletUtxo>): List<WalletUtxo> =
        utxos.sortedWith(
            compareByDescending<WalletUtxo> { it.valueUnits }
                .thenBy { it.txid }
                .thenBy { it.vout }
        )

    fun selectCoins(utxos: List<WalletUtxo>, targetUnits: Long): List<WalletUtxo> {
        require(targetUnits > 0) { "Target amount must be positive" }
        val selected = mutableListOf<WalletUtxo>()
        var sum = 0L
        for (utxo in deterministicLargestFirst(utxos)) {
            selected += utxo
            sum += utxo.valueUnits
            if (sum >= targetUnits) {
                return selected
            }
        }
        throw InsufficientFundsException("Insufficient finalized funds")
    }
}

class InsufficientFundsException(message: String) : IllegalArgumentException(message)
