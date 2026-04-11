package com.finalis.mobile.app

import com.finalis.mobile.core.model.AddressValidationResult
import com.finalis.mobile.core.model.BalanceSnapshot
import com.finalis.mobile.core.model.BroadcastResult
import com.finalis.mobile.core.model.HistoryEntry
import com.finalis.mobile.core.model.HistoryPageResult
import com.finalis.mobile.core.model.NetworkIdentity
import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.TxDetail
import com.finalis.mobile.core.model.TxDirection
import com.finalis.mobile.core.model.TxInput
import com.finalis.mobile.core.model.TxOutput
import com.finalis.mobile.core.model.TxStatus
import com.finalis.mobile.core.model.WalletAddress
import com.finalis.mobile.core.model.WalletOutPoint
import com.finalis.mobile.core.model.WalletUtxo
import com.finalis.mobile.core.storage.SubmittedTransactionStore
import com.finalis.mobile.core.wallet.SubmittedTransactionManager
import com.finalis.mobile.data.lightserver.LightserverRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletReconciliationTest {
    @Test
    fun `submitted spend becomes finalized and stops contributing reserved balance`() = runBlocking {
        val txid = "a".repeat(64)
        val manager = SubmittedTransactionManager(TestSubmittedTransactionStore(listOf(submittedRecord(txid))))
        val reconciliation = reconcileSubmittedTransactions(
            repository = FakeReconciliationRepository(finalizedTxids = setOf(txid)),
            submittedTransactionManager = manager,
        )

        val funds = summarizeWalletFunds(
            finalizedBalance = 100_000L,
            finalizedUtxos = listOf(finalizedUtxo()),
            submitted = reconciliation.remainingSubmitted,
        )

        assertEquals(emptyList(), reconciliation.remainingSubmitted)
        assertEquals(listOf(txid), reconciliation.newlyFinalized.map { it.txid })
        assertEquals(0L, funds.reservedUnits)
        assertEquals(100_000L, funds.spendableUnits)
    }

    @Test
    fun `finalized history replaces local recently finalized activity for same txid`() = runBlocking {
        val txid = "a".repeat(64)
        val manager = SubmittedTransactionManager(TestSubmittedTransactionStore(listOf(submittedRecord(txid))))
        val reconciliation = reconcileSubmittedTransactions(
            repository = FakeReconciliationRepository(),
            submittedTransactionManager = manager,
            finalizedHistoryWindow = FinalizedHistoryWindow(
                txHeights = mapOf(txid to 100L),
                visiblePageTxids = setOf(txid),
            ),
        )
        val lifecycle = buildTransactionLifecycleState(
            finalizedUtxos = listOf(finalizedUtxo()),
            submitted = reconciliation.remainingSubmitted,
            recentlyFinalizedTransactions = reconciliation.newlyFinalized,
            finalizedHistoryEntries = emptyList(),
            finalizedHistoryTxids = setOf(txid),
        )

        assertEquals(emptyList(), reconciliation.remainingSubmitted)
        assertEquals(emptyList(), lifecycle.pendingTransactions)
        assertEquals(emptyList(), lifecycle.recentlyFinalizedTransactions)
    }

    @Test
    fun `older finalized tx not on current page is reconciled from bounded history window`() = runBlocking {
        val txid = "a".repeat(64)
        val manager = SubmittedTransactionManager(TestSubmittedTransactionStore(listOf(submittedRecord(txid))))
        val repository = FakeReconciliationRepository(
            historyPages = mapOf(
                "" to historyPage(
                    txid = "c".repeat(64),
                    nextCursor = "cursor-2",
                    hasMore = true,
                ),
                "cursor-2" to historyPage(
                    txid = txid,
                    nextCursor = null,
                    hasMore = false,
                ),
            ),
        )
        val currentPage = repository.loadHistoryPage("sc1wallet", limit = 20)
        val finalizedHistoryWindow = collectBoundedFinalizedHistoryWindow(
            repository = repository,
            address = "sc1wallet",
            firstPage = currentPage,
            pageLimit = 3,
            pageSize = 20,
            txidLimit = 60,
        )
        val reconciliation = reconcileSubmittedTransactions(
            repository = repository,
            submittedTransactionManager = manager,
            finalizedHistoryWindow = finalizedHistoryWindow,
        )
        val lifecycle = buildTransactionLifecycleState(
            finalizedUtxos = listOf(finalizedUtxo()),
            submitted = reconciliation.remainingSubmitted,
            recentlyFinalizedTransactions = reconciliation.newlyFinalized,
            finalizedHistoryEntries = currentPage.items,
            finalizedHistoryTxids = finalizedHistoryWindow.txHeights.keys,
        )

        assertEquals(setOf("c".repeat(64), txid), finalizedHistoryWindow.txHeights.keys)
        assertEquals(emptyList(), reconciliation.remainingSubmitted)
        assertEquals(emptyList(), lifecycle.recentlyFinalizedTransactions)
        assertEquals(TxReconciliationSource.BOUNDED_HISTORY_WINDOW, reconciliation.evidenceByTxid[txid]?.source)
    }

    @Test
    fun `bounded history window respects page cap and does not fetch unbounded history`() = runBlocking {
        val repository = FakeReconciliationRepository(
            historyPages = mapOf(
                "" to historyPage(txid = "1".repeat(64), nextCursor = "cursor-2", hasMore = true),
                "cursor-2" to historyPage(txid = "2".repeat(64), nextCursor = "cursor-3", hasMore = true),
                "cursor-3" to historyPage(txid = "3".repeat(64), nextCursor = "cursor-4", hasMore = true),
                "cursor-4" to historyPage(txid = "4".repeat(64), nextCursor = null, hasMore = false),
            ),
        )
        val currentPage = repository.loadHistoryPage("sc1wallet", limit = 20)

        val finalizedHistoryWindow = collectBoundedFinalizedHistoryWindow(
            repository = repository,
            address = "sc1wallet",
            firstPage = currentPage,
            pageLimit = 3,
            pageSize = 20,
            txidLimit = 60,
        )

        assertEquals(3, repository.historyPageCalls)
        assertEquals(setOf("1".repeat(64), "2".repeat(64), "3".repeat(64)), finalizedHistoryWindow.txHeights.keys)
    }

    @Test
    fun `still submitted spend remains reserved until finalized`() = runBlocking {
        val txid = "a".repeat(64)
        val manager = SubmittedTransactionManager(TestSubmittedTransactionStore(listOf(submittedRecord(txid))))
        val reconciliation = reconcileSubmittedTransactions(
            repository = FakeReconciliationRepository(),
            submittedTransactionManager = manager,
        )
        val lifecycle = buildTransactionLifecycleState(
            finalizedUtxos = listOf(finalizedUtxo()),
            submitted = reconciliation.remainingSubmitted,
        )

        assertEquals(listOf(txid), reconciliation.remainingSubmitted.map { it.txid })
        assertEquals(emptyList(), reconciliation.newlyFinalized)
        assertEquals(1, lifecycle.pendingTransactions.size)
        assertEquals(70_000L, lifecycle.totalReservedUnits)
        assertEquals(TxReconciliationSource.LOCAL_ONLY, reconciliation.evidenceByTxid[txid]?.source)
    }

    @Test
    fun `reconciliation is idempotent after submitted tx is finalized`() = runBlocking {
        val txid = "a".repeat(64)
        val manager = SubmittedTransactionManager(TestSubmittedTransactionStore(listOf(submittedRecord(txid))))
        val repository = FakeReconciliationRepository(finalizedTxids = setOf(txid))

        val first = reconcileSubmittedTransactions(
            repository = repository,
            submittedTransactionManager = manager,
        )
        val second = reconcileSubmittedTransactions(
            repository = repository,
            submittedTransactionManager = manager,
        )

        assertEquals(emptyList(), first.remainingSubmitted)
        assertEquals(listOf(txid), first.newlyFinalized.map { it.txid })
        assertEquals(emptyList(), second.remainingSubmitted)
        assertEquals(emptyList(), second.newlyFinalized)
    }

    @Test
    fun `submitted only tx debug record explains local reservation state`() {
        val txid = "a".repeat(64)
        val submitted = listOf(submittedRecord(txid))
        val pending = buildTransactionLifecycleState(
            finalizedUtxos = listOf(finalizedUtxo()),
            submitted = submitted,
        ).pendingTransactions

        val debug = buildTxDebugRecords(
            submitted = submitted,
            recentlyFinalizedTransactions = emptyList(),
            pendingTransactions = pending,
            visibleHistoryEntries = emptyList(),
            finalizedWindow = FinalizedHistoryWindow(emptyMap(), emptySet()),
            reconciliationEvidence = mapOf(
                txid to SubmittedTxReconciliationEvidence(
                    txid = txid,
                    finalizedStatusConfirmed = false,
                    source = TxReconciliationSource.LOCAL_ONLY,
                ),
            ),
        ).single()

        assertEquals(TxVisibleActivityCategory.SUBMITTED, debug.visibleActivityCategory)
        assertEquals(true, debug.localSubmittedPresent)
        assertEquals(true, debug.reservedBalanceContributor)
        assertEquals(false, debug.finalizedStatusConfirmed)
        assertEquals("still_in_local_submitted_bucket_and_no_finalized_evidence_loaded", debug.whyStillSubmitted)
    }

    @Test
    fun `tx finalized by direct status exposes tx status reconciliation source`() {
        val txid = "a".repeat(64)
        val debug = buildTxDebugRecords(
            submitted = emptyList(),
            recentlyFinalizedTransactions = listOf(submittedRecord(txid)),
            pendingTransactions = emptyList(),
            visibleHistoryEntries = emptyList(),
            finalizedWindow = FinalizedHistoryWindow(emptyMap(), emptySet()),
            reconciliationEvidence = mapOf(
                txid to SubmittedTxReconciliationEvidence(
                    txid = txid,
                    finalizedStatusConfirmed = true,
                    source = TxReconciliationSource.TX_STATUS,
                    lastObservedHeight = 100L,
                ),
            ),
        ).single()

        assertEquals(TxVisibleActivityCategory.RECENTLY_FINALIZED, debug.visibleActivityCategory)
        assertEquals(TxReconciliationSource.TX_STATUS, debug.reconciliationSource)
        assertEquals(true, debug.finalizedStatusConfirmed)
        assertEquals(false, debug.reservedBalanceContributor)
        assertEquals("submitted_state_cleared_local_recently_finalized_bridge_active", debug.whyStillSubmitted)
    }

    @Test
    fun `duplicate suppression debug reason is populated when bounded history replaces local bridge`() {
        val txid = "a".repeat(64)
        val debug = buildTxDebugRecords(
            submitted = emptyList(),
            recentlyFinalizedTransactions = listOf(submittedRecord(txid)),
            pendingTransactions = emptyList(),
            visibleHistoryEntries = emptyList(),
            finalizedWindow = FinalizedHistoryWindow(
                txHeights = mapOf(txid to 100L),
                visiblePageTxids = emptySet(),
            ),
            reconciliationEvidence = mapOf(
                txid to SubmittedTxReconciliationEvidence(
                    txid = txid,
                    finalizedStatusConfirmed = false,
                    source = TxReconciliationSource.BOUNDED_HISTORY_WINDOW,
                    lastObservedHeight = 100L,
                ),
            ),
        ).single()

        assertEquals(true, debug.suppressedAsDuplicate)
        assertEquals("bounded_finalized_history_window_contains_same_txid", debug.suppressionReason)
        assertEquals(TxVisibleActivityCategory.NONE, debug.visibleActivityCategory)
        assertEquals("bounded_finalized_history_window_contains_same_txid", debug.whyStillSubmitted)
    }

    @Test
    fun `debug record explains stale submitted state when tx status is already finalized`() {
        val txid = "46caef73d6282ed2418d4a9c6e84e112ac1c6458ebc7f125a5a4d08ec68096bd"
        val submitted = listOf(submittedRecord(txid))
        val pending = buildTransactionLifecycleState(
            finalizedUtxos = listOf(finalizedUtxo()),
            submitted = submitted,
        ).pendingTransactions

        val debug = buildTxDebugRecords(
            submitted = submitted,
            recentlyFinalizedTransactions = emptyList(),
            pendingTransactions = pending,
            visibleHistoryEntries = emptyList(),
            finalizedWindow = FinalizedHistoryWindow(emptyMap(), emptySet()),
            reconciliationEvidence = mapOf(
                txid to SubmittedTxReconciliationEvidence(
                    txid = txid,
                    finalizedStatusConfirmed = true,
                    source = TxReconciliationSource.TX_STATUS,
                    lastObservedHeight = 80L,
                ),
            ),
        ).single()

        assertEquals(TxVisibleActivityCategory.SUBMITTED, debug.visibleActivityCategory)
        assertTrue(debug.localSubmittedPresent)
        assertTrue(debug.finalizedStatusConfirmed)
        assertTrue(debug.reservedBalanceContributor)
        assertEquals(TxReconciliationSource.TX_STATUS, debug.reconciliationSource)
        assertEquals("finalized_status_known_but_local_state_not_reconciled", debug.whyStillSubmitted)
    }

    @Test
    fun `inspect tx debug record reports absence from current wallet state`() {
        val txid = "46caef73d6282ed2418d4a9c6e84e112ac1c6458ebc7f125a5a4d08ec68096bd"

        val record = inspectTxDebugRecord(txid, emptyList())

        assertEquals(txid, record.txid)
        assertEquals(TxVisibleActivityCategory.NONE, record.visibleActivityCategory)
        assertEquals(TxReconciliationSource.LOCAL_ONLY, record.reconciliationSource)
        assertEquals("tx_not_present_in_current_wallet_state", record.whyStillSubmitted)
        assertFalse(record.localSubmittedPresent)
        assertFalse(record.finalizedHistoryPresent)
    }
}

private class TestSubmittedTransactionStore(
    initial: List<SubmittedTransactionRecord>,
) : SubmittedTransactionStore {
    private val records = linkedMapOf<String, SubmittedTransactionRecord>()

    init {
        initial.forEach { save(it) }
    }

    override fun list(): List<SubmittedTransactionRecord> = records.values.toList()

    override fun save(record: SubmittedTransactionRecord) {
        records[record.txid.lowercase()] = record.copy(txid = record.txid.lowercase())
    }

    override fun remove(txid: String) {
        records.remove(txid.lowercase())
    }

    override fun clear() {
        records.clear()
    }
}

private class FakeReconciliationRepository(
    private val finalizedTxids: Set<String> = emptySet(),
    val historyPages: Map<String, HistoryPageResult> = mapOf("" to HistoryPageResult(items = emptyList(), hasMore = false)),
) : LightserverRepository {
    var historyPageCalls: Int = 0
        private set

    override suspend fun loadStatus(): NetworkIdentity =
        NetworkIdentity(
            name = "mainnet",
            networkId = "a57ab83946712672c507b1bd312c5fb2",
            protocolVersion = 1,
            featureFlags = 1L,
            genesisHash = "5".repeat(64),
            tipHeight = 100L,
            tipHash = "f".repeat(64),
            serverTruth = "finalized_only",
            proofsTipOnly = true,
            finalizedHeight = 100L,
            finalizedHash = "f".repeat(64),
            syncMode = "finalized_only",
            syncSnapshotPresent = true,
            observedNetworkHeightKnown = true,
            finalizedLag = 0L,
        )

    override suspend fun validateAddress(address: String): AddressValidationResult =
        AddressValidationResult(valid = true, normalizedAddress = address)

    override suspend fun loadBalance(address: String): BalanceSnapshot =
        BalanceSnapshot(
            address = WalletAddress(address),
            confirmedUnits = 100_000L,
            asset = "FINALIS",
            tipHeight = 100L,
            tipHash = "f".repeat(64),
        )

    override suspend fun loadUtxos(address: String): List<WalletUtxo> = listOf(finalizedUtxo())

    override suspend fun loadHistoryPage(address: String, cursor: String?, limit: Int, fromHeight: Long?): HistoryPageResult {
        historyPageCalls += 1
        return historyPages[cursor.orEmpty()] ?: HistoryPageResult(items = emptyList(), hasMore = false)
    }

    override suspend fun loadHistory(address: String, cursor: String?, limit: Int, fromHeight: Long?): List<HistoryEntry> = emptyList()

    override suspend fun loadTxDetail(txid: String): TxDetail =
        TxDetail(
            txid = txid,
            height = 100L,
            status = TxStatus.FINALIZED,
            finalizedTransitionHash = "f".repeat(64),
            finalizedDepth = 1L,
            inputs = listOf(TxInput(prevTxid = "b".repeat(64), prevVout = 0)),
            outputs = listOf(TxOutput(index = 0, value = 50_000L, address = "sc1recipient")),
        )

    override suspend fun findFinalizedTxDetail(txid: String): TxDetail? =
        if (finalizedTxids.contains(txid.lowercase())) loadTxDetail(txid) else null

    override suspend fun broadcastTx(txHex: String): BroadcastResult =
        BroadcastResult(
            accepted = true,
            txid = "c".repeat(64),
            status = TxStatus.SUBMITTED,
            errorCode = null,
            error = null,
        )
}

private fun submittedRecord(txid: String): SubmittedTransactionRecord =
    SubmittedTransactionRecord(
        txid = txid,
        recipientAddress = "sc1recipient",
        amountUnits = 50_000L,
        requestedFeeUnits = 1_000L,
        appliedFeeUnits = 1_000L,
        changeUnits = 19_000L,
        createdAtEpochMillis = 1_000L,
        consumedInputs = listOf(WalletOutPoint(txid = "b".repeat(64), vout = 0)),
    )

private fun finalizedUtxo(): WalletUtxo =
    WalletUtxo(
        txid = "b".repeat(64),
        vout = 0,
        valueUnits = 70_000L,
        height = 99L,
        scriptPubKeyHex = "76a914" + "11".repeat(20) + "88ac",
    )

private fun historyPage(
    txid: String,
    nextCursor: String?,
    hasMore: Boolean,
): HistoryPageResult =
    HistoryPageResult(
        items = listOf(
            HistoryEntry(
                txid = txid,
                height = 100L,
                status = TxStatus.FINALIZED,
                direction = TxDirection.SEND,
                creditedValue = 0L,
                debitedValue = 51_000L,
                netValue = -51_000L,
            ),
        ),
        nextCursor = nextCursor,
        hasMore = hasMore,
    )
