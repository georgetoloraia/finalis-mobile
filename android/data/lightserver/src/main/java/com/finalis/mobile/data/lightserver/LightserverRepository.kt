package com.finalis.mobile.data.lightserver

import com.finalis.mobile.core.model.AddressValidationResult
import com.finalis.mobile.core.model.BalanceSnapshot
import com.finalis.mobile.core.model.BroadcastResult
import com.finalis.mobile.core.model.HistoryEntry
import com.finalis.mobile.core.model.HistoryPageResult
import com.finalis.mobile.core.model.NetworkIdentity
import com.finalis.mobile.core.model.TxDetail
import com.finalis.mobile.core.model.WalletUtxo

interface LightserverRepository {
    suspend fun loadStatus(): NetworkIdentity
    suspend fun validateAddress(address: String): AddressValidationResult
    suspend fun loadBalance(address: String): BalanceSnapshot
    suspend fun loadUtxos(address: String): List<WalletUtxo>
    suspend fun loadHistoryPage(address: String, cursor: String? = null, limit: Int = 20, fromHeight: Long? = null): HistoryPageResult
    suspend fun loadHistory(address: String, cursor: String? = null, limit: Int = 20, fromHeight: Long? = null): List<HistoryEntry>
    suspend fun loadTxDetail(txid: String): TxDetail
    suspend fun findFinalizedTxDetail(txid: String): TxDetail?
    suspend fun broadcastTx(txHex: String): BroadcastResult
}
