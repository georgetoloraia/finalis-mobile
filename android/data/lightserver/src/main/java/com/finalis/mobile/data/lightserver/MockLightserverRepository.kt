package com.finalis.mobile.data.lightserver

import com.finalis.mobile.core.model.AddressValidationResult
import com.finalis.mobile.core.model.BalanceSnapshot
import com.finalis.mobile.core.model.BroadcastResult
import com.finalis.mobile.core.model.HistoryEntry
import com.finalis.mobile.core.model.HistoryPageResult
import com.finalis.mobile.core.model.NetworkIdentity
import com.finalis.mobile.core.model.TxDetail
import com.finalis.mobile.core.model.WalletAddress
import com.finalis.mobile.core.model.WalletUtxo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class MockLightserverRepository : LightserverRepository {
    private val transport = MockRpcTransport()
    private val delegate = LiveLightserverRepository(transport)

    override suspend fun loadStatus(): NetworkIdentity = delegate.loadStatus()

    override suspend fun validateAddress(address: String): AddressValidationResult =
        delegate.validateAddress(address)

    override suspend fun loadBalance(address: String): BalanceSnapshot =
        delegate.loadBalance(MockLightserverJson.KnownWalletAddress)
            .copy(address = WalletAddress(address))

    override suspend fun loadUtxos(address: String): List<WalletUtxo> =
        delegate.loadUtxos(MockLightserverJson.KnownWalletAddress)

    override suspend fun loadHistoryPage(address: String, cursor: String?, limit: Int, fromHeight: Long?): HistoryPageResult =
        delegate.loadHistoryPage(MockLightserverJson.KnownWalletAddress, cursor, limit, fromHeight)

    override suspend fun loadHistory(address: String, cursor: String?, limit: Int, fromHeight: Long?): List<HistoryEntry> =
        delegate.loadHistory(MockLightserverJson.KnownWalletAddress, cursor, limit, fromHeight)

    override suspend fun loadTxDetail(txid: String): TxDetail = delegate.loadTxDetail("c".repeat(64))

    override suspend fun findFinalizedTxDetail(txid: String): TxDetail? = delegate.findFinalizedTxDetail("c".repeat(64))

    override suspend fun broadcastTx(txHex: String): BroadcastResult = delegate.broadcastTx(txHex)
}

private class MockRpcTransport : LightserverTransport {
    override suspend fun post(body: String): String {
        val method = Json.parseToJsonElement(body).jsonObject["method"]?.toString()?.trim('"')
        return when (method) {
            "get_status" -> MockLightserverJson.status
            "validate_address" -> MockLightserverJson.validateAddress
            "get_utxos" -> MockLightserverJson.utxos
            "get_history_page" -> MockLightserverJson.historyPage
            "get_tx" -> MockLightserverJson.tx
            "get_tx_status" -> MockLightserverJson.txStatus
            "broadcast_tx" -> MockLightserverJson.broadcastResult
            else -> error("Unsupported mock lightserver method: $method")
        }
    }
}
