package com.finalis.mobile.core.wallet

import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.WalletOutPoint
import com.finalis.mobile.core.storage.SubmittedTransactionStore

class SubmittedTransactionManager(
    private val submittedTransactionStore: SubmittedTransactionStore,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun listSubmitted(): List<SubmittedTransactionRecord> = submittedTransactionStore.list()

    fun listReservedOutPoints(): Set<WalletOutPoint> =
        submittedTransactionStore.list()
            .flatMap { it.consumedInputs }
            .toSet()

    fun recordSubmitted(tx: BuiltTransaction): SubmittedTransactionRecord {
        val record = SubmittedTransactionRecord(
            txid = tx.txid,
            recipientAddress = tx.recipientAddress,
            amountUnits = tx.amountUnits,
            requestedFeeUnits = tx.requestedFeeUnits,
            appliedFeeUnits = tx.appliedFeeUnits,
            changeUnits = tx.changeUnits,
            createdAtEpochMillis = nowEpochMillis(),
            consumedInputs = tx.inputs.map { input ->
                WalletOutPoint(
                    txid = input.txid,
                    vout = input.vout,
                )
            },
        )
        submittedTransactionStore.save(record)
        return record
    }

    fun markFinalized(txid: String) {
        submittedTransactionStore.remove(txid)
    }

    fun clearAll() {
        submittedTransactionStore.clear()
    }
}
