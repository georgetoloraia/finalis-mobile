package com.finalis.mobile.core.storage

import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.WalletOutPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubmittedTransactionRecordsTest {
    @Test
    fun `sanitize removes corrupt txids and deduplicates by normalized txid`() {
        val newer = record(txid = "AA".repeat(32), createdAtEpochMillis = 200L)
        val olderDuplicate = record(txid = "aa".repeat(32), createdAtEpochMillis = 100L)
        val corrupt = record(txid = "xyz", createdAtEpochMillis = 300L)

        val sanitized = SubmittedTransactionRecords.sanitize(listOf(olderDuplicate, newer, corrupt))

        assertEquals(listOf(newer.copy(txid = "aa".repeat(32))), sanitized)
    }

    @Test
    fun `normalize for storage lowercases valid txid`() {
        val normalized = SubmittedTransactionRecords.normalizeForStorage(
            record(txid = "AB".repeat(32))
        )

        assertEquals("ab".repeat(32), normalized.txid)
    }

    @Test
    fun `normalize for storage rejects invalid txid`() {
        assertFailsWith<IllegalArgumentException> {
            SubmittedTransactionRecords.normalizeForStorage(record(txid = "bad-txid"))
        }
    }

    @Test
    fun `normalize for storage lowercases and deduplicates consumed inputs`() {
        val normalized = SubmittedTransactionRecords.normalizeForStorage(
            record(
                txid = "AB".repeat(32),
                consumedInputs = listOf(
                    WalletOutPoint(txid = "CD".repeat(32), vout = 1),
                    WalletOutPoint(txid = "cd".repeat(32), vout = 1),
                    WalletOutPoint(txid = "bad", vout = 2),
                ),
            )
        )

        assertEquals(
            listOf(WalletOutPoint(txid = "cd".repeat(32), vout = 1)),
            normalized.consumedInputs,
        )
    }

    private fun record(
        txid: String,
        createdAtEpochMillis: Long = 1L,
        consumedInputs: List<WalletOutPoint> = emptyList(),
    ): SubmittedTransactionRecord = SubmittedTransactionRecord(
        txid = txid,
        recipientAddress = "tsc1recipient",
        amountUnits = 10L,
        requestedFeeUnits = 1L,
        appliedFeeUnits = 1L,
        changeUnits = 0L,
        createdAtEpochMillis = createdAtEpochMillis,
        consumedInputs = consumedInputs,
    )
}
