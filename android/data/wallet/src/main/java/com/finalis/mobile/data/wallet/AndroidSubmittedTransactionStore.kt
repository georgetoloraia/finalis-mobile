package com.finalis.mobile.data.wallet

import android.content.Context
import android.content.SharedPreferences
import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.WalletOutPoint
import com.finalis.mobile.core.storage.SubmittedTransactionRecords
import com.finalis.mobile.core.storage.SubmittedTransactionStore

class AndroidSubmittedTransactionStore internal constructor(
    private val preferences: SharedPreferences,
) : SubmittedTransactionStore {
    constructor(
        context: Context,
    ) : this(
        context.applicationContext.getSharedPreferences(PreferencesFileName, Context.MODE_PRIVATE),
    )

    override fun list(): List<SubmittedTransactionRecord> {
        val txids = preferences.getStringSet(KeySubmittedTxids, emptySet()).orEmpty()
        val loadedRecords = mutableListOf<SubmittedTransactionRecord>()
        val invalidTxids = mutableListOf<String>()
        txids.forEach { txid ->
            val normalizedTxid = txid.lowercase()
            val record = loadRecord(normalizedTxid)
            if (record == null) {
                invalidTxids += normalizedTxid
            } else {
                loadedRecords += record
            }
        }
        if (invalidTxids.isNotEmpty()) {
            invalidTxids.forEach(::remove)
        }
        return SubmittedTransactionRecords.sanitize(loadedRecords)
    }

    override fun save(record: SubmittedTransactionRecord) {
        val normalizedRecord = SubmittedTransactionRecords.normalizeForStorage(record)
        val txid = normalizedRecord.txid
        val ids = preferences.getStringSet(KeySubmittedTxids, emptySet()).orEmpty().toMutableSet()
        ids += txid
        val committed = preferences.edit()
            .putStringSet(KeySubmittedTxids, ids)
            .putString(key(txid, "recipient"), normalizedRecord.recipientAddress)
            .putLong(key(txid, "amount"), normalizedRecord.amountUnits)
            .putLong(key(txid, "requested_fee"), normalizedRecord.requestedFeeUnits)
            .putLong(key(txid, "applied_fee"), normalizedRecord.appliedFeeUnits)
            .putLong(key(txid, "change"), normalizedRecord.changeUnits)
            .putLong(key(txid, "created_at"), normalizedRecord.createdAtEpochMillis)
            .putStringSet(key(txid, "consumed_inputs"), encodeConsumedInputs(normalizedRecord.consumedInputs))
            .commit()
        check(committed) { "Failed to persist submitted transaction record" }
    }

    override fun remove(txid: String) {
        val normalizedTxid = txid.lowercase()
        val ids = preferences.getStringSet(KeySubmittedTxids, emptySet()).orEmpty().toMutableSet()
        ids -= normalizedTxid
        val committed = preferences.edit()
            .putStringSet(KeySubmittedTxids, ids)
            .remove(key(normalizedTxid, "recipient"))
            .remove(key(normalizedTxid, "amount"))
            .remove(key(normalizedTxid, "requested_fee"))
            .remove(key(normalizedTxid, "applied_fee"))
            .remove(key(normalizedTxid, "change"))
            .remove(key(normalizedTxid, "created_at"))
            .remove(key(normalizedTxid, "consumed_inputs"))
            .commit()
        check(committed) { "Failed to remove submitted transaction record" }
    }

    override fun clear() {
        val ids = preferences.getStringSet(KeySubmittedTxids, emptySet()).orEmpty()
        val editor = preferences.edit().remove(KeySubmittedTxids)
        ids.forEach { txid ->
            editor.remove(key(txid, "recipient"))
            editor.remove(key(txid, "amount"))
            editor.remove(key(txid, "requested_fee"))
            editor.remove(key(txid, "applied_fee"))
            editor.remove(key(txid, "change"))
            editor.remove(key(txid, "created_at"))
            editor.remove(key(txid, "consumed_inputs"))
        }
        val committed = editor.commit()
        check(committed) { "Failed to clear submitted transaction records" }
    }

    private fun loadRecord(txid: String): SubmittedTransactionRecord? {
        if (!Hex32Regex.matches(txid)) return null
        val recipientAddress = preferences.getString(key(txid, "recipient"), null) ?: return null
        val amountUnits = preferences.getLong(key(txid, "amount"), Long.MIN_VALUE)
        val requestedFeeUnits = preferences.getLong(key(txid, "requested_fee"), Long.MIN_VALUE)
        val appliedFeeUnits = preferences.getLong(key(txid, "applied_fee"), Long.MIN_VALUE)
        val changeUnits = preferences.getLong(key(txid, "change"), Long.MIN_VALUE)
        val createdAtEpochMillis = preferences.getLong(key(txid, "created_at"), Long.MIN_VALUE)
        val consumedInputs = decodeConsumedInputs(preferences.getStringSet(key(txid, "consumed_inputs"), null))
        if (amountUnits == Long.MIN_VALUE || requestedFeeUnits == Long.MIN_VALUE ||
            appliedFeeUnits == Long.MIN_VALUE || changeUnits == Long.MIN_VALUE || createdAtEpochMillis == Long.MIN_VALUE
        ) {
            return null
        }
        return SubmittedTransactionRecord(
            txid = txid,
            recipientAddress = recipientAddress,
            amountUnits = amountUnits,
            requestedFeeUnits = requestedFeeUnits,
            appliedFeeUnits = appliedFeeUnits,
            changeUnits = changeUnits,
            createdAtEpochMillis = createdAtEpochMillis,
            consumedInputs = consumedInputs,
        )
    }

    private fun key(txid: String, suffix: String): String = "submitted.$txid.$suffix"

    private companion object {
        const val PreferencesFileName = "finalis_local_submitted_txs"
        const val KeySubmittedTxids = "submitted_txids"
        val Hex32Regex = Regex("^[0-9a-f]{64}$")
    }
}

internal fun encodeConsumedInputs(consumedInputs: List<WalletOutPoint>): Set<String> =
    consumedInputs.map { "${it.txid.lowercase()}:${it.vout}" }.toSet()

internal fun decodeConsumedInputs(encoded: Set<String>?): List<WalletOutPoint> =
    encoded.orEmpty().mapNotNull { raw ->
        val separatorIndex = raw.lastIndexOf(':')
        if (separatorIndex <= 0 || separatorIndex >= raw.lastIndex) return@mapNotNull null
        val txid = raw.substring(0, separatorIndex).trim().lowercase()
        if (!Regex("^[0-9a-f]{64}$").matches(txid)) return@mapNotNull null
        val vout = raw.substring(separatorIndex + 1).toIntOrNull() ?: return@mapNotNull null
        if (vout < 0) return@mapNotNull null
        WalletOutPoint(
            txid = txid,
            vout = vout,
        )
    }.distinct()
