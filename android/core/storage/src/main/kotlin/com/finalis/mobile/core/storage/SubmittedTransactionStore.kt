package com.finalis.mobile.core.storage

import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.WalletOutPoint

interface SubmittedTransactionStore {
    fun list(): List<SubmittedTransactionRecord>
    fun save(record: SubmittedTransactionRecord)
    fun remove(txid: String)
    fun clear()
}

object SubmittedTransactionRecords {
    private val Hex32Regex = Regex("^[0-9a-f]{64}$")

    fun sanitize(records: List<SubmittedTransactionRecord>): List<SubmittedTransactionRecord> =
        records.mapNotNull(::normalizeOrNull)
            .sortedByDescending { it.createdAtEpochMillis }
            .distinctBy { it.txid }

    fun normalizeForStorage(record: SubmittedTransactionRecord): SubmittedTransactionRecord =
        normalizeOrNull(record) ?: throw IllegalArgumentException("Submitted txid must be hex32")

    private fun normalizeOrNull(record: SubmittedTransactionRecord): SubmittedTransactionRecord? {
        val normalizedTxid = record.txid.trim().lowercase()
        if (!Hex32Regex.matches(normalizedTxid)) return null
        return record.copy(
            txid = normalizedTxid,
            consumedInputs = record.consumedInputs.mapNotNull(::normalizeOutPointOrNull).distinct(),
        )
    }

    private fun normalizeOutPointOrNull(outPoint: WalletOutPoint): WalletOutPoint? {
        val normalizedTxid = outPoint.txid.trim().lowercase()
        if (!Hex32Regex.matches(normalizedTxid)) return null
        if (outPoint.vout < 0) return null
        return WalletOutPoint(
            txid = normalizedTxid,
            vout = outPoint.vout,
        )
    }
}
