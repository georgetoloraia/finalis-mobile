package com.finalis.mobile.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.finalis.mobile.core.model.HistoryEntry
import com.finalis.mobile.core.model.HistoryPageResult
import com.finalis.mobile.core.model.ImportedWalletRecord
import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.wallet.SubmittedTransactionManager
import com.finalis.mobile.core.wallet.WalletNetworkGuard
import com.finalis.mobile.core.wallet.WalletSafetyManager
import com.finalis.mobile.core.wallet.WalletSessionManager
import com.finalis.mobile.data.lightserver.LightserverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WalletViewModel(
    private val repository: LightserverRepository,
    private val rpcSettingsRepository: RpcSettingsRepository,
    private val walletSessionManager: WalletSessionManager,
    private val submittedTransactionManager: SubmittedTransactionManager,
    private val walletSafetyManager: WalletSafetyManager,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    companion object {
        private const val DefaultHistoryPageSize = 20
        private const val ReconciliationHistoryPageCount = 3
        private const val ReconciliationHistoryTxidLimit = DefaultHistoryPageSize * ReconciliationHistoryPageCount
        private const val ResumeRefreshDebounceMillis = 1_500L
        private const val LatestHistoryBootstrapBlockWindow = 256L
        private const val LatestHistoryBootstrapPageLimit = 16
    }
    var importPrivateKeyHex by mutableStateOf("")
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var exportArmed by mutableStateOf(false)
        private set
    var exportedPrivateKeyHex by mutableStateOf<String?>(null)
        private set
    var resetArmed by mutableStateOf(false)
        private set

    var startupChecked by mutableStateOf(false)
        private set
    var walletRecord by mutableStateOf<ImportedWalletRecord?>(null)
        private set
    var readState by mutableStateOf<DashboardState>(DashboardState.Loading)
        private set
    var rpcSettingsState by mutableStateOf(rpcSettingsRepository.loadUiState())
        private set
    var diagnosticsState by mutableStateOf(
        buildDiagnosticsState(
            activeEndpoint = rpcSettingsState.activeEndpoint,
            status = null,
        ),
    )
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private val refreshMutex = Mutex()
    private var refreshGeneration = 0L
    private var pollingJob: Job? = null
    private var inFlightRefreshCount = 0
    private var lastResumeRefreshEpochMillis: Long? = null

    fun onImportPrivateKeyChange(value: String) {
        importPrivateKeyHex = value
        importError = null
    }

    fun onRpcEndpointInputChange(value: String) {
        rpcSettingsState = rpcSettingsState.copy(
            inputValue = value,
            message = null,
        )
    }

    fun syncRpcSettingsState(message: String? = rpcSettingsState.message) {
        rpcSettingsState = rpcSettingsRepository.loadUiState(
            inputValue = rpcSettingsState.inputValue,
            message = message,
        )
        diagnosticsState = diagnosticsState.copy(
            endpointHealth = diagnosticsState.endpointHealth.copy(
                activeEndpoint = rpcSettingsState.activeEndpoint,
            ),
        )
    }

    private fun invalidateRefreshes() {
        refreshGeneration += 1L
    }

    private fun beginRefresh() {
        inFlightRefreshCount += 1
        isRefreshing = true
    }

    private fun endRefresh() {
        inFlightRefreshCount = (inFlightRefreshCount - 1).coerceAtLeast(0)
        isRefreshing = inFlightRefreshCount > 0
    }

    fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    suspend fun loadStartup() {
        cancelPolling()
        invalidateRefreshes()
        syncRpcSettingsState()
        refreshEndpointHealth()
        val storedWallet = withContext(Dispatchers.IO) { walletSessionManager.loadCurrentWallet() }
        walletRecord = storedWallet
        startupChecked = true
        if (storedWallet == null) {
            readState = DashboardState.Empty
        } else {
            refreshDashboard(storedWallet)
        }
    }

    suspend fun importWallet(): ImportedWalletRecord? {
        isImporting = true
        importError = null
        return try {
            val importedWallet = withContext(Dispatchers.IO) {
                walletSessionManager.importAndPersist(
                    privateKeyHex = importPrivateKeyHex,
                )
            }
            cancelPolling()
            invalidateRefreshes()
            walletRecord = importedWallet
            importPrivateKeyHex = ""
            exportArmed = false
            exportedPrivateKeyHex = null
            resetArmed = false
            refreshDashboard(importedWallet)
            importedWallet
        } catch (error: IllegalArgumentException) {
            importError = error.message ?: "Wallet import failed"
            null
        } finally {
            isImporting = false
        }
    }

    fun onToggleExport() {
        val currentWallet = walletRecord ?: return
        if (!exportArmed) {
            exportArmed = true
        } else {
            exportedPrivateKeyHex = walletSessionManager.exportPrivateKeyHex(currentWallet)
            exportArmed = false
        }
    }

    fun hideExportedKey() {
        exportedPrivateKeyHex = null
    }

    suspend fun onToggleReset(): Boolean {
        if (!resetArmed) {
            resetArmed = true
            return false
        }
        cancelPolling()
        invalidateRefreshes()
        withContext(Dispatchers.IO) {
            walletSafetyManager.resetWallet()
        }
        walletRecord = null
        readState = DashboardState.Empty
        syncRpcSettingsState()
        importPrivateKeyHex = ""
        importError = null
        exportedPrivateKeyHex = null
        exportArmed = false
        resetArmed = false
        return true
    }

    suspend fun addRpcEndpoint(): Boolean {
        if (BuildConfig.USE_MOCK_LIGHTSERVER) {
            syncRpcSettingsState(message = "Runtime endpoint settings are disabled in mock mode.")
            return false
        }
        invalidateRefreshes()
        val endpoint = try {
            RpcEndpoint(normalizeRpcUrl(rpcSettingsState.inputValue))
        } catch (error: IllegalArgumentException) {
            syncRpcSettingsState(message = error.message ?: "Endpoint URL is invalid")
            return false
        }
        val probe = probeRpcEndpoint(endpoint)
        if (!probe.isValid) {
            syncRpcSettingsState(message = formatProbeMessage(probe))
            diagnosticsState = buildDiagnosticsState(
                activeEndpoint = endpoint,
                status = probe.status,
                mismatch = probe.mismatch,
                error = probe.error?.takeIf { probe.mismatch == null },
            )
            return false
        }
        withContext(Dispatchers.IO) {
            rpcSettingsRepository.addEndpoint(endpoint)
        }
        rpcSettingsState = rpcSettingsRepository.loadUiState(
            inputValue = "",
            message = "Active endpoint set to ${endpoint.url}",
        )
        diagnosticsState = buildDiagnosticsState(
            activeEndpoint = endpoint,
            status = probe.status,
        )
        return true
    }

    suspend fun selectRpcEndpoint(endpoint: RpcEndpoint): Boolean {
        if (BuildConfig.USE_MOCK_LIGHTSERVER) {
            syncRpcSettingsState(message = "Runtime endpoint settings are disabled in mock mode.")
            return false
        }
        invalidateRefreshes()
        val probe = probeRpcEndpoint(endpoint)
        if (!probe.isValid) {
            syncRpcSettingsState(message = formatProbeMessage(probe))
            diagnosticsState = buildDiagnosticsState(
                activeEndpoint = endpoint,
                status = probe.status,
                mismatch = probe.mismatch,
                error = probe.error?.takeIf { probe.mismatch == null },
            )
            return false
        }
        withContext(Dispatchers.IO) {
            rpcSettingsRepository.setActiveEndpoint(endpoint)
        }
        syncRpcSettingsState(message = "Active endpoint set to ${endpoint.url}")
        diagnosticsState = buildDiagnosticsState(
            activeEndpoint = endpoint,
            status = probe.status,
        )
        return true
    }

    fun removeRpcEndpoint(endpoint: RpcEndpoint) {
        invalidateRefreshes()
        rpcSettingsRepository.removeEndpoint(endpoint)
        syncRpcSettingsState(message = "Removed endpoint ${endpoint.url}")
        diagnosticsState = buildDiagnosticsState(
            activeEndpoint = rpcSettingsState.activeEndpoint,
            status = diagnosticsState.endpointHealth.status?.takeIf {
                rpcSettingsState.activeEndpoint?.url == diagnosticsState.endpointHealth.activeEndpoint?.url
            },
        )
    }

    suspend fun refreshDashboard(loadedWallet: ImportedWalletRecord? = walletRecord) {
        val targetWallet = loadedWallet ?: return
        val generation = ++refreshGeneration
        var validatedStatus: com.finalis.mobile.core.model.NetworkIdentity? = null
        readState = DashboardState.Loading
        beginRefresh()
        try {
            refreshMutex.withLock {
                val status = withContext(Dispatchers.IO) { repository.loadStatus() }
                validatedStatus = status
                syncRpcSettingsState()
                val currentWallet = walletRecord
                if (generation == refreshGeneration && currentWallet != null && currentWallet.privateKeyHex == targetWallet.privateKeyHex) {
                    walletRecord = targetWallet
                    val submitted = withContext(Dispatchers.IO) { submittedTransactionManager.listSubmitted() }
                    val mismatch = WalletNetworkGuard.detectMismatch(status)
                    diagnosticsState = buildDiagnosticsState(
                        activeEndpoint = rpcSettingsState.activeEndpoint,
                        status = status,
                        mismatch = mismatch,
                    )
                    readState = if (mismatch != null) {
                        DashboardState.NetworkMismatch(
                            status = status,
                            submitted = submitted,
                            mismatch = mismatch,
                            lifecycleState = buildTransactionLifecycleState(
                                finalizedUtxos = emptyList(),
                                submitted = submitted,
                            ),
                        )
                    } else {
                        val walletAddress = targetWallet.walletProfile.address.value
                        val balance = withContext(Dispatchers.IO) { repository.loadBalance(walletAddress) }
                        val utxos = withContext(Dispatchers.IO) { repository.loadUtxos(walletAddress) }
                        val historyPage = loadLatestHistoryBootstrapPage(
                            repository = repository,
                            address = walletAddress,
                            finalizedHeight = status.finalizedHeight ?: status.tipHeight,
                            limit = DefaultHistoryPageSize,
                        )
                        val finalizedHistoryWindow = collectBoundedFinalizedHistoryWindow(
                            repository = repository,
                            address = walletAddress,
                            firstPage = historyPage,
                            pageLimit = ReconciliationHistoryPageCount,
                            pageSize = DefaultHistoryPageSize,
                            txidLimit = ReconciliationHistoryTxidLimit,
                        )
                        val reconciliation = reconcileSubmittedTransactions(
                            repository = repository,
                            submittedTransactionManager = submittedTransactionManager,
                            finalizedHistoryWindow = finalizedHistoryWindow,
                        )
                        val historyState = FinalizedHistoryState(
                            entries = historyPage.items,
                            nextCursor = historyPage.nextCursor,
                            hasMore = historyPage.hasMore,
                        )
                        val txDetail = historyPage.items.firstOrNull()?.let { entry ->
                            runCatching {
                                withContext(Dispatchers.IO) { repository.loadTxDetail(entry.txid) }
                            }.getOrNull()
                        }
                        val funds = summarizeWalletFunds(
                            finalizedBalance = balance.confirmedUnits,
                            finalizedUtxos = utxos,
                            submitted = reconciliation.remainingSubmitted,
                        )
                        val lifecycleState = buildTransactionLifecycleState(
                            finalizedUtxos = utxos,
                            submitted = reconciliation.remainingSubmitted,
                            recentlyFinalizedTransactions = reconciliation.newlyFinalized,
                            finalizedHistoryEntries = historyPage.items,
                            finalizedHistoryTxids = finalizedHistoryWindow.txHeights.keys,
                        )
                        val txDebugRecords = buildTxDebugRecords(
                            submitted = reconciliation.remainingSubmitted,
                            recentlyFinalizedTransactions = reconciliation.newlyFinalized,
                            pendingTransactions = lifecycleState.pendingTransactions,
                            visibleHistoryEntries = historyPage.items,
                            finalizedWindow = finalizedHistoryWindow,
                            reconciliationEvidence = reconciliation.evidenceByTxid,
                        )
                        diagnosticsState = buildDiagnosticsState(
                            activeEndpoint = rpcSettingsState.activeEndpoint,
                            status = status,
                            txDebugRecords = txDebugRecords,
                        )
                        DashboardState.Ready(
                            status = status,
                            balance = balance,
                            reservedUnits = funds.reservedUnits,
                            spendableUnits = funds.spendableUnits,
                            historyState = historyState,
                            txDetail = txDetail,
                            submitted = reconciliation.remainingSubmitted,
                            lifecycleState = lifecycleState,
                        )
                    }
                }
            }
        } catch (error: Exception) {
            val friendlyError = formatLightserverError(error)
            val failure = classifyEndpointFailure(error)
            val keepValidatedEndpointState =
                validatedStatus != null &&
                    failure.kind == EndpointErrorKind.UNAVAILABLE &&
                    !isTransportFailure(error)
            diagnosticsState = if (keepValidatedEndpointState) {
                buildDiagnosticsState(
                    activeEndpoint = rpcSettingsState.activeEndpoint,
                    status = validatedStatus,
                )
            } else {
                buildDiagnosticsState(
                    activeEndpoint = rpcSettingsState.activeEndpoint,
                    status = null,
                    error = failure,
                )
            }
            if (generation == refreshGeneration) {
                readState = DashboardState.Error(friendlyError)
            }
        } finally {
            syncRpcSettingsState()
            endRefresh()
        }
    }

    suspend fun refreshDashboardOnResume(loadedWallet: ImportedWalletRecord? = walletRecord): Boolean {
        val targetWallet = loadedWallet ?: return false
        val now = nowEpochMillis()
        if (isRefreshing) return false
        val lastResumeRefresh = lastResumeRefreshEpochMillis
        if (lastResumeRefresh != null && now - lastResumeRefresh < ResumeRefreshDebounceMillis) return false
        lastResumeRefreshEpochMillis = now
        refreshDashboard(targetWallet)
        return true
    }

    suspend fun refreshEndpointHealth() {
        if (BuildConfig.USE_MOCK_LIGHTSERVER) {
            diagnosticsState = buildDiagnosticsState(
                activeEndpoint = null,
                status = null,
                error = null,
            )
            return
        }
        try {
            val status = withContext(Dispatchers.IO) { repository.loadStatus() }
            syncRpcSettingsState()
            val mismatch = WalletNetworkGuard.detectMismatch(status)
            diagnosticsState = buildDiagnosticsState(
                activeEndpoint = rpcSettingsState.activeEndpoint,
                status = status,
                mismatch = mismatch,
            )
        } catch (error: Exception) {
            syncRpcSettingsState()
            diagnosticsState = buildDiagnosticsState(
                activeEndpoint = rpcSettingsState.activeEndpoint,
                status = null,
                error = classifyEndpointFailure(error),
            )
        }
    }

    suspend fun loadMoreHistory() {
        val ready = readState as? DashboardState.Ready ?: return
        val wallet = walletRecord ?: return
        val nextCursor = ready.historyState.nextCursor ?: return
        if (ready.historyState.isLoadingMore) return
        readState = ready.copy(
            historyState = ready.historyState.copy(
                isLoadingMore = true,
                loadMoreError = null,
            ),
        )
        try {
            val nextPage = withContext(Dispatchers.IO) {
                repository.loadHistoryPage(
                    address = wallet.walletProfile.address.value,
                    cursor = nextCursor,
                    limit = DefaultHistoryPageSize,
                )
            }
            val currentReady = readState as? DashboardState.Ready ?: return
            val updatedReady = currentReady.copy(
                historyState = appendHistoryPage(currentReady.historyState, nextPage),
            )
            readState = updatedReady
            diagnosticsState = diagnosticsState.copy(
                txDebugRecords = buildTxDebugRecords(
                    submitted = updatedReady.submitted,
                    recentlyFinalizedTransactions = updatedReady.lifecycleState.recentlyFinalizedTransactions,
                    pendingTransactions = updatedReady.lifecycleState.pendingTransactions,
                    visibleHistoryEntries = updatedReady.historyState.entries,
                    finalizedWindow = finalizedHistoryWindowFromDebugRecords(
                        txDebugRecords = diagnosticsState.txDebugRecords,
                        visibleHistoryEntries = updatedReady.historyState.entries,
                    ),
                    reconciliationEvidence = reconciliationEvidenceFromDebugRecords(diagnosticsState.txDebugRecords),
                ),
            )
        } catch (error: Exception) {
            val currentReady = readState as? DashboardState.Ready ?: return
            readState = currentReady.copy(
                historyState = currentReady.historyState.copy(
                    isLoadingMore = false,
                    loadMoreError = formatLightserverError(error),
                ),
            )
        }
    }

    fun startSubmittedPolling(scope: CoroutineScope, loadedWallet: ImportedWalletRecord) {
        cancelPolling()
        val walletPrivateKeyHex = loadedWallet.privateKeyHex
        pollingJob = scope.launch {
            repeat(20) {
                delay(3_000)
                if (walletRecord?.privateKeyHex != walletPrivateKeyHex) return@launch
                val pending = withContext(Dispatchers.IO) { submittedTransactionManager.listSubmitted() }
                if (pending.isEmpty()) return@launch
                refreshDashboard(loadedWallet)
            }
        }
    }
}

data class SubmittedReconciliationResult(
    val remainingSubmitted: List<SubmittedTransactionRecord>,
    val newlyFinalized: List<SubmittedTransactionRecord>,
    val evidenceByTxid: Map<String, SubmittedTxReconciliationEvidence>,
)

data class FinalizedHistoryWindow(
    val txHeights: Map<String, Long>,
    val visiblePageTxids: Set<String>,
)

data class SubmittedTxReconciliationEvidence(
    val txid: String,
    val finalizedStatusConfirmed: Boolean,
    val source: TxReconciliationSource,
    val lastObservedHeight: Long? = null,
)

suspend fun loadLatestHistoryBootstrapPage(
    repository: LightserverRepository,
    address: String,
    finalizedHeight: Long,
    limit: Int,
): HistoryPageResult {
    require(limit >= 1) { "limit must be at least 1" }
    val bootstrapBlockWindow = 256L
    val bootstrapPageLimit = 16

    var fromHeight = (finalizedHeight - bootstrapBlockWindow + 1L).coerceAtLeast(0L)
    var bestPage = withContext(Dispatchers.IO) {
        repository.loadHistoryPage(
            address = address,
            limit = limit,
            fromHeight = fromHeight,
        )
    }
    var pagesWalked = 1

    while (bestPage.items.isEmpty() && fromHeight > 0L) {
        fromHeight = (fromHeight - bootstrapBlockWindow).coerceAtLeast(0L)
        bestPage = withContext(Dispatchers.IO) {
            repository.loadHistoryPage(
                address = address,
                limit = limit,
                fromHeight = fromHeight,
            )
        }
        pagesWalked = 1
    }

    var cursor = bestPage.nextCursor
    while (bestPage.hasMore && cursor != null && pagesWalked < bootstrapPageLimit) {
        bestPage = withContext(Dispatchers.IO) {
            repository.loadHistoryPage(
                address = address,
                cursor = cursor,
                limit = limit,
            )
        }
        cursor = bestPage.nextCursor
        pagesWalked += 1
    }

    return bestPage
}

fun finalizedHistoryWindowFromDebugRecords(
    txDebugRecords: List<TxDebugRecord>,
    visibleHistoryEntries: List<HistoryEntry>,
): FinalizedHistoryWindow =
    FinalizedHistoryWindow(
        txHeights = txDebugRecords
            .filter { it.finalizedHistoryPresent }
            .mapNotNull { record -> record.lastObservedHeight?.let { height -> record.txid.lowercase() to height } }
            .toMap(linkedMapOf()),
        visiblePageTxids = visibleHistoryEntries.map { it.txid.lowercase() }.toSet(),
    )

fun reconciliationEvidenceFromDebugRecords(
    txDebugRecords: List<TxDebugRecord>,
): Map<String, SubmittedTxReconciliationEvidence> =
    txDebugRecords.associate { record ->
        record.txid.lowercase() to SubmittedTxReconciliationEvidence(
            txid = record.txid.lowercase(),
            finalizedStatusConfirmed = record.finalizedStatusConfirmed,
            source = record.reconciliationSource,
            lastObservedHeight = record.lastObservedHeight,
        )
    }

suspend fun collectBoundedFinalizedHistoryWindow(
    repository: LightserverRepository,
    address: String,
    firstPage: com.finalis.mobile.core.model.HistoryPageResult,
    pageLimit: Int,
    pageSize: Int,
    txidLimit: Int,
): FinalizedHistoryWindow {
    require(pageLimit >= 1) { "pageLimit must be at least 1" }
    require(pageSize >= 1) { "pageSize must be at least 1" }
    require(txidLimit >= 1) { "txidLimit must be at least 1" }

    val txHeights = linkedMapOf<String, Long>()
    var page = firstPage
    var pagesFetched = 1
    val visiblePageTxids = firstPage.items.map { it.txid.lowercase() }.toSet()
    page.items.forEach { entry ->
        if (txHeights.size < txidLimit) txHeights.putIfAbsent(entry.txid.lowercase(), entry.height)
    }

    while (page.hasMore && page.nextCursor != null && pagesFetched < pageLimit && txHeights.size < txidLimit) {
        page = withContext(Dispatchers.IO) {
            repository.loadHistoryPage(
                address = address,
                cursor = page.nextCursor,
                limit = pageSize,
            )
        }
        page.items.forEach { entry ->
            if (txHeights.size < txidLimit) txHeights.putIfAbsent(entry.txid.lowercase(), entry.height)
        }
        pagesFetched += 1
    }

    return FinalizedHistoryWindow(
        txHeights = txHeights,
        visiblePageTxids = visiblePageTxids,
    )
}

suspend fun reconcileSubmittedTransactions(
    repository: LightserverRepository,
    submittedTransactionManager: SubmittedTransactionManager,
    finalizedHistoryWindow: FinalizedHistoryWindow = FinalizedHistoryWindow(emptyMap(), emptySet()),
): SubmittedReconciliationResult {
    val records: List<SubmittedTransactionRecord> = withContext(Dispatchers.IO) { submittedTransactionManager.listSubmitted() }
    val normalizedFinalizedHistoryTxids = finalizedHistoryWindow.txHeights.keys
    val finalizedRecords = mutableListOf<SubmittedTransactionRecord>()
    val evidenceByTxid = linkedMapOf<String, SubmittedTxReconciliationEvidence>()
    records.forEach { record ->
        val normalizedTxid = record.txid.lowercase()
        val source = if (finalizedHistoryWindow.visiblePageTxids.contains(normalizedTxid)) {
            SubmittedTxReconciliationEvidence(
                txid = normalizedTxid,
                finalizedStatusConfirmed = false,
                source = TxReconciliationSource.HISTORY_PAGE,
                lastObservedHeight = finalizedHistoryWindow.txHeights[normalizedTxid],
            )
        } else if (normalizedFinalizedHistoryTxids.contains(normalizedTxid)) {
            SubmittedTxReconciliationEvidence(
                txid = normalizedTxid,
                finalizedStatusConfirmed = false,
                source = TxReconciliationSource.BOUNDED_HISTORY_WINDOW,
                lastObservedHeight = finalizedHistoryWindow.txHeights[normalizedTxid],
            )
        } else {
            val finalizedDetail = withContext(Dispatchers.IO) { repository.findFinalizedTxDetail(record.txid) }
            if (finalizedDetail != null) {
                SubmittedTxReconciliationEvidence(
                    txid = normalizedTxid,
                    finalizedStatusConfirmed = true,
                    source = TxReconciliationSource.TX_STATUS,
                    lastObservedHeight = finalizedDetail.height,
                )
            } else {
                SubmittedTxReconciliationEvidence(
                    txid = normalizedTxid,
                    finalizedStatusConfirmed = false,
                    source = TxReconciliationSource.LOCAL_ONLY,
                )
            }
        }
        evidenceByTxid[normalizedTxid] = source
        if (source.source != TxReconciliationSource.LOCAL_ONLY) {
            withContext(Dispatchers.IO) { submittedTransactionManager.markFinalized(record.txid) }
            finalizedRecords += record
        }
    }
    val remainingSubmitted = withContext(Dispatchers.IO) { submittedTransactionManager.listSubmitted() }
    return SubmittedReconciliationResult(
        remainingSubmitted = remainingSubmitted,
        newlyFinalized = finalizedRecords,
        evidenceByTxid = evidenceByTxid,
    )
}

private fun isTransportFailure(error: Throwable): Boolean {
    val message = error.message?.lowercase().orEmpty()
    return "unavailable" in message ||
        "timed out" in message ||
        "timeout" in message ||
        "http " in message ||
        generateSequence(error) { it.cause }.any { it is java.io.IOException }
}

private fun formatProbeMessage(probe: EndpointProbeResult): String =
    when {
        probe.mismatch != null -> probe.mismatch.message
        probe.error != null -> probe.error.message
        else -> "Endpoint validation failed"
    }
