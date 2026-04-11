package com.finalis.mobile.core.wallet

import com.finalis.mobile.core.model.ImportedWalletRecord
import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.WalletAddress
import com.finalis.mobile.core.model.WalletOutPoint
import com.finalis.mobile.core.model.WalletProfile
import com.finalis.mobile.core.model.WalletUtxo
import com.finalis.mobile.core.storage.SubmittedTransactionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletSendServiceTest {
    private val wallet = ImportedWalletRecord(
        privateKeyHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        walletProfile = WalletProfile(
            address = WalletAddress("tsc1adrqzotqb6xl76opjawtlcn5ds6msa6sqdtecmte"),
            publicKeyHex = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8",
        ),
        importedAtEpochMillis = 0L,
    )

    @Test
    fun `builds and signs tx matching core vector`() {
        val service = WalletSendService()
        val tx = service.buildAndSignTransaction(
            wallet = wallet,
            spendableUtxos = listOf(
                WalletUtxo(
                    txid = "1111111111111111111111111111111111111111111111111111111111111111",
                    vout = 1,
                    valueUnits = 1_000_000L,
                    height = 120L,
                    scriptPubKeyHex = "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac",
                )
            ),
            recipientAddress = "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg",
            amountUnits = 250_000L,
            requestedFeeUnits = 1_000L,
        )

        assertEquals("a8e2567f937a31429e50311bfb0c1b539c45a985f473b1bf8aedbfb7b635e9b6", tx.txid)
        assertEquals(
            "01000000011111111111111111111111111111111111111111111111111111111111111111010000006240aa34462252e03624e736ed838d89ff31bc2fe48dbfe0f357d1b887e1f025f13549816dc2c4228e79595ecdb81e9d5ee0a904d1dc93b95ca9586f7d1a3f86ea0d2003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8ffffffff0290d00300000000001976a914c6483861d53cd667df6d21320d58e3593180e1b388acc86d0b00000000001976a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac00000000",
            tx.txHex,
        )
        assertEquals(749_000L, tx.changeUnits)
        assertEquals(1_000L, tx.appliedFeeUnits)
    }

    @Test
    fun `throws for insufficient funds`() {
        val service = WalletSendService()

        assertFailsWith<InsufficientFundsException> {
            service.buildAndSignTransaction(
                wallet = wallet,
                spendableUtxos = listOf(
                    WalletUtxo(
                        txid = "1".repeat(64),
                        vout = 0,
                        valueUnits = 5_000L,
                        height = 100L,
                        scriptPubKeyHex = "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac",
                    )
                ),
                recipientAddress = "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg",
                amountUnits = 10_000L,
                requestedFeeUnits = 1_000L,
            )
        }
    }

    @Test
    fun `folds dust change into applied fee`() {
        val service = WalletSendService()
        val tx = service.buildAndSignTransaction(
            wallet = wallet,
            spendableUtxos = listOf(
                WalletUtxo(
                    txid = "2".repeat(64),
                    vout = 0,
                    valueUnits = 10_500L,
                    height = 100L,
                    scriptPubKeyHex = "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac",
                )
            ),
            recipientAddress = "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg",
            amountUnits = 9_000L,
            requestedFeeUnits = 1_000L,
        )

        assertEquals(0L, tx.changeUnits)
        assertEquals(1, tx.outputs.size)
        assertEquals(1_500L, tx.appliedFeeUnits)
    }

    @Test
    fun `selection and serialization are deterministic`() {
        val service = WalletSendService()
        val utxos = listOf(
            WalletUtxo("3".repeat(64), 1, 200_000L, 100L, "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac"),
            WalletUtxo("1".repeat(64), 0, 500_000L, 100L, "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac"),
            WalletUtxo("2".repeat(64), 0, 500_000L, 100L, "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac"),
        )

        val txA = service.buildAndSignTransaction(wallet, utxos, "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg", 250_000L)
        val txB = service.buildAndSignTransaction(wallet, utxos.reversed(), "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg", 250_000L)

        assertEquals(txA.txHex, txB.txHex)
        assertEquals(txA.txid, txB.txid)
    }

    @Test
    fun `filters reserved outpoints from spendable utxos`() {
        val utxos = listOf(
            WalletUtxo("1".repeat(64), 0, 500_000L, 100L, "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac"),
            WalletUtxo("2".repeat(64), 1, 250_000L, 100L, "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac"),
        )

        val spendable = filterSpendableUtxos(
            utxos = utxos,
            reservedOutPoints = setOf(WalletOutPoint("1".repeat(64), 0)),
        )

        assertEquals(listOf(utxos[1]), spendable)
    }

    @Test
    fun `second send cannot reuse reserved input from submitted transaction`() {
        val service = WalletSendService()
        val singleUtxo = WalletUtxo(
            txid = "1".repeat(64),
            vout = 0,
            valueUnits = 500_000L,
            height = 100L,
            scriptPubKeyHex = "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac",
        )

        val firstTx = service.buildAndSignTransaction(
            wallet = wallet,
            spendableUtxos = listOf(singleUtxo),
            recipientAddress = "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg",
            amountUnits = 250_000L,
        )

        assertFailsWith<InsufficientFundsException> {
            service.buildAndSignTransaction(
                wallet = wallet,
                spendableUtxos = listOf(singleUtxo),
                recipientAddress = "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg",
                amountUnits = 250_000L,
                reservedOutPoints = firstTx.inputs.map { WalletOutPoint(it.txid, it.vout) }.toSet(),
            )
        }
    }

    @Test
    fun `submitted manager persists and removes submitted records`() {
        val store = InMemorySubmittedTransactionStore()
        val manager = SubmittedTransactionManager(store, nowEpochMillis = { 500L })
        val tx = BuiltTransaction(
            txHex = "ab",
            txid = "f".repeat(64),
            inputs = listOf(
                WalletUtxo(
                    txid = "1".repeat(64),
                    vout = 2,
                    valueUnits = 500_000L,
                    height = 100L,
                    scriptPubKeyHex = "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac",
                )
            ),
            outputs = emptyList(),
            requestedFeeUnits = 1_000L,
            appliedFeeUnits = 1_000L,
            changeUnits = 0L,
            amountUnits = 250_000L,
            recipientAddress = "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg",
        )

        val record = manager.recordSubmitted(tx)

        assertEquals(1, manager.listSubmitted().size)
        assertEquals(500L, record.createdAtEpochMillis)
        assertEquals(setOf(WalletOutPoint("1".repeat(64), 2)), manager.listReservedOutPoints())
        manager.markFinalized(tx.txid)
        assertEquals(emptyList(), manager.listSubmitted())
        assertEquals(emptySet(), manager.listReservedOutPoints())
    }

    @Test
    fun `clearAll releases reserved outpoints`() {
        val store = InMemorySubmittedTransactionStore()
        val manager = SubmittedTransactionManager(store, nowEpochMillis = { 500L })

        manager.recordSubmitted(
            BuiltTransaction(
                txHex = "ab",
                txid = "f".repeat(64),
                inputs = listOf(
                    WalletUtxo(
                        txid = "1".repeat(64),
                        vout = 0,
                        valueUnits = 500_000L,
                        height = 100L,
                        scriptPubKeyHex = "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac",
                    )
                ),
                outputs = emptyList(),
                requestedFeeUnits = 1_000L,
                appliedFeeUnits = 1_000L,
                changeUnits = 0L,
                amountUnits = 250_000L,
                recipientAddress = "tsc1addeqodb2u6nmz67nuqtedky4nmtdahbwprg5dsg",
            )
        )

        manager.clearAll()

        assertEquals(emptySet(), manager.listReservedOutPoints())
    }
}

private class InMemorySubmittedTransactionStore : SubmittedTransactionStore {
    private val records = linkedMapOf<String, SubmittedTransactionRecord>()

    override fun list(): List<SubmittedTransactionRecord> = records.values.toList()

    override fun save(record: SubmittedTransactionRecord) {
        records[record.txid] = record
    }

    override fun remove(txid: String) {
        records.remove(txid)
    }

    override fun clear() {
        records.clear()
    }
}
