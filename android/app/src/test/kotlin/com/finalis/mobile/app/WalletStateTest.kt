package com.finalis.mobile.app

import com.finalis.mobile.core.model.HistoryEntry
import com.finalis.mobile.core.model.HistoryPageResult
import com.finalis.mobile.core.model.NetworkIdentity
import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.WalletOutPoint
import com.finalis.mobile.core.model.WalletUtxo
import com.finalis.mobile.core.wallet.BuiltTransaction
import com.finalis.mobile.core.wallet.NetworkMismatch
import com.finalis.mobile.core.wallet.WalletTxOutput
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletStateTest {
    @Test
    fun `finalized history entries are presented newest first`() {
        val ordered = finalizedHistoryEntriesNewestFirst(
            listOf(
                HistoryEntry(
                    txid = "a",
                    height = 1,
                    status = com.finalis.mobile.core.model.TxStatus.FINALIZED,
                    direction = com.finalis.mobile.core.model.TxDirection.RECEIVE,
                    creditedValue = 10,
                    debitedValue = 0,
                    netValue = 10,
                ),
                HistoryEntry(
                    txid = "c",
                    height = 3,
                    status = com.finalis.mobile.core.model.TxStatus.FINALIZED,
                    direction = com.finalis.mobile.core.model.TxDirection.SEND,
                    creditedValue = 0,
                    debitedValue = 5,
                    netValue = -5,
                ),
                HistoryEntry(
                    txid = "b",
                    height = 2,
                    status = com.finalis.mobile.core.model.TxStatus.FINALIZED,
                    direction = com.finalis.mobile.core.model.TxDirection.RECEIVE,
                    creditedValue = 7,
                    debitedValue = 0,
                    netValue = 7,
                ),
            ),
        )

        assertEquals(listOf(3L, 2L, 1L), ordered.map { it.height })
    }

    @Test
    fun `build transaction lifecycle links reserved units to pending transactions`() {
        val submitted = SubmittedTransactionRecord(
            txid = "tx-1",
            recipientAddress = "sc1recipient",
            amountUnits = 40_000L,
            requestedFeeUnits = 1_000L,
            appliedFeeUnits = 1_000L,
            changeUnits = 9_000L,
            createdAtEpochMillis = 1234L,
            consumedInputs = listOf(
                WalletOutPoint(txid = "utxo-1", vout = 0),
                WalletOutPoint(txid = "utxo-2", vout = 1),
            ),
        )

        val lifecycle = buildTransactionLifecycleState(
            finalizedUtxos = listOf(
                WalletUtxo(txid = "utxo-1", vout = 0, valueUnits = 30_000L, height = 10, scriptPubKeyHex = "aa"),
                WalletUtxo(txid = "utxo-2", vout = 1, valueUnits = 20_000L, height = 11, scriptPubKeyHex = "bb"),
            ),
            submitted = listOf(submitted),
        )

        assertEquals(1, lifecycle.pendingTransactions.size)
        assertEquals(50_000L, lifecycle.pendingTransactions.single().reservedUnits)
        assertEquals(41_000L, lifecycle.pendingTransactions.single().totalDeductionUnits)
        assertEquals(50_000L, lifecycle.totalReservedUnits)
    }

    @Test
    fun `build send preview state reflects reservation impact on available balance`() {
        val previewState = buildSendPreviewState(
            preview = BuiltTransaction(
                txHex = "00",
                txid = "txid",
                inputs = listOf(
                    WalletUtxo(txid = "utxo-1", vout = 0, valueUnits = 70_000L, height = 10, scriptPubKeyHex = "aa"),
                ),
                outputs = listOf(
                    WalletTxOutput(valueUnits = 40_000L, scriptPubKeyHex = "bb"),
                    WalletTxOutput(valueUnits = 29_000L, scriptPubKeyHex = "cc"),
                ),
                requestedFeeUnits = 1_000L,
                appliedFeeUnits = 1_000L,
                changeUnits = 29_000L,
                amountUnits = 40_000L,
                recipientAddress = "sc1recipient",
            ),
            currentSpendableUnits = 120_000L,
            currentReservedUnits = 15_000L,
        )

        assertEquals(41_000L, previewState.totalDeductionUnits)
        assertEquals(70_000L, previewState.reservedByThisTransactionUnits)
        assertEquals(50_000L, previewState.availableAfterReservationUnits)
        assertEquals(85_000L, previewState.reservedAfterBroadcastUnits)
    }

    @Test
    fun `build diagnostics state marks valid synced endpoint healthy`() {
        val diagnostics = buildDiagnosticsState(
            activeEndpoint = RpcEndpoint("https://lightserver.finalis.org/rpc"),
            status = sampleNetworkIdentity(
                finalizedLag = 0L,
                syncSnapshotPresent = true,
                observedNetworkHeightKnown = true,
            ),
        )

        assertEquals(EndpointReachabilityState.REACHABLE, diagnostics.endpointHealth.reachability)
        assertEquals(SyncTrustState.HEALTHY, diagnostics.endpointHealth.syncTrust)
    }

    @Test
    fun `build diagnostics state distinguishes mismatch and unreachable`() {
        val mismatch = buildDiagnosticsState(
            activeEndpoint = RpcEndpoint("https://wrong.example/rpc"),
            status = sampleNetworkIdentity(),
            mismatch = NetworkMismatch(
                code = "invalid_network_id",
                message = "This endpoint is not Finalis mainnet.",
            ),
        )
        val unreachable = buildDiagnosticsState(
            activeEndpoint = RpcEndpoint("https://down.example/rpc"),
            status = null,
            error = EndpointFailure(
                kind = EndpointErrorKind.UNAVAILABLE,
                message = "The lightserver is unavailable",
            ),
        )
        val incompatible = buildDiagnosticsState(
            activeEndpoint = RpcEndpoint("https://stale.example/rpc"),
            status = null,
            error = EndpointFailure(
                kind = EndpointErrorKind.INCOMPATIBLE,
                message = "The endpoint does not satisfy the live Finalis lightserver contract.",
            ),
        )

        assertEquals(SyncTrustState.MISMATCHED, mismatch.endpointHealth.syncTrust)
        assertEquals(SyncTrustState.UNREACHABLE, unreachable.endpointHealth.syncTrust)
        assertEquals(SyncTrustState.DEGRADED, incompatible.endpointHealth.syncTrust)
        assertEquals(EndpointErrorKind.INCOMPATIBLE, incompatible.endpointHealth.errorKind)
    }

    @Test
    fun `append history page merges entries and carries cursor state`() {
        val initial = FinalizedHistoryState(
            entries = listOf(
                HistoryEntry(
                    txid = "a",
                    height = 1,
                    status = com.finalis.mobile.core.model.TxStatus.FINALIZED,
                    direction = com.finalis.mobile.core.model.TxDirection.RECEIVE,
                    creditedValue = 10,
                    debitedValue = 0,
                    netValue = 10,
                ),
            ),
            nextCursor = "cursor-1",
            hasMore = true,
            isLoadingMore = true,
        )

        val merged = appendHistoryPage(
            existing = initial,
            page = HistoryPageResult(
                items = listOf(
                    HistoryEntry(
                        txid = "b",
                        height = 2,
                        status = com.finalis.mobile.core.model.TxStatus.FINALIZED,
                        direction = com.finalis.mobile.core.model.TxDirection.SEND,
                        creditedValue = 0,
                        debitedValue = 5,
                        netValue = -5,
                    ),
                ),
                nextCursor = null,
                hasMore = false,
            ),
        )

        assertEquals(listOf("a", "b"), merged.entries.map { it.txid })
        assertEquals(null, merged.nextCursor)
        assertEquals(false, merged.hasMore)
        assertEquals(false, merged.isLoadingMore)
        assertEquals(null, merged.loadMoreError)
    }
}

private fun sampleNetworkIdentity(
    finalizedLag: Long? = 1L,
    syncSnapshotPresent: Boolean? = true,
    observedNetworkHeightKnown: Boolean? = true,
): NetworkIdentity =
    NetworkIdentity(
        name = "mainnet",
        networkId = "a57ab83946712672c507b1bd312c5fb2",
        protocolVersion = 1,
        featureFlags = 1L,
        genesisHash = "0".repeat(64),
        tipHeight = 123L,
        tipHash = "a".repeat(64),
        serverTruth = "finalized_only",
        proofsTipOnly = true,
        finalizedHeight = 123L,
        finalizedHash = "b".repeat(64),
        healthyPeerCount = 2,
        establishedPeerCount = 3,
        version = "finalis-core/0.7",
        binaryVersion = "finalis-lightserver/0.7",
        walletApiVersion = "FINALIS_WALLET_API_V1",
        syncMode = "finalized_only",
        syncSnapshotPresent = syncSnapshotPresent,
        observedNetworkHeightKnown = observedNetworkHeightKnown,
        finalizedLag = finalizedLag,
        bootstrapSyncIncomplete = false,
        peerHeightDisagreement = false,
        nextHeightCommitteeAvailable = true,
        nextHeightProposerAvailable = true,
    )
