package com.finalis.mobile.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.finalis.mobile.core.model.BroadcastErrorCode
import com.finalis.mobile.core.model.BroadcastResult
import com.finalis.mobile.core.model.ImportedWalletRecord
import com.finalis.mobile.core.model.TxStatus
import com.finalis.mobile.core.model.WalletOutPoint
import com.finalis.mobile.core.wallet.InsufficientFundsException
import com.finalis.mobile.core.wallet.BuiltTransaction
import com.finalis.mobile.core.wallet.SubmittedTransactionManager
import com.finalis.mobile.core.wallet.WalletSendService
import com.finalis.mobile.core.wallet.filterSpendableUtxos
import com.finalis.mobile.data.lightserver.LightserverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SendViewModel(
    private val sendService: WalletSendService,
) : ViewModel() {
    var recipientAddress by mutableStateOf("")
        private set
    var amountUnitsInput by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var preview by mutableStateOf<BuiltTransaction?>(null)
        private set
    var previewState by mutableStateOf<SendPreviewState?>(null)
        private set
    var broadcastResult by mutableStateOf<BroadcastResult?>(null)
        private set
    var isSending by mutableStateOf(false)
        private set

    private val sendMutex = Mutex()

    fun onRecipientAddressChange(value: String) {
        recipientAddress = value
        clearTransientState()
    }

    fun onAmountUnitsChange(value: String) {
        amountUnitsInput = value.replace(',', '.')
        clearTransientState()
    }

    fun normalizeAmountUnitsInput() {
        val raw = amountUnitsInput.trim()
        if (raw.isEmpty()) return
        val normalizedUnits = runCatching { parseFinalisUnits(raw) }.getOrNull() ?: return
        amountUnitsInput = formatFinalisUnits(normalizedUnits)
        clearTransientState()
    }

    fun clearAllState() {
        recipientAddress = ""
        amountUnitsInput = ""
        clearTransientState()
    }

    suspend fun prepareTransaction(
        wallet: ImportedWalletRecord,
        repository: LightserverRepository,
        submittedTransactionManager: SubmittedTransactionManager,
        currentSpendableUnits: Long?,
        currentReservedUnits: Long?,
    ) {
        if (isSending) return
        isSending = true
        errorMessage = null
        broadcastResult = null
        try {
            sendMutex.withLock {
                val amountUnits = parseAmountUnits(amountUnitsInput)
                amountUnitsInput = formatFinalisUnits(amountUnits)
                val addressValidation = withContext(Dispatchers.IO) {
                    repository.validateAddress(recipientAddress)
                }
                require(addressValidation.valid) { addressValidation.error ?: "Recipient address is invalid" }
                require(addressValidation.serverNetworkMatch != false) {
                    "Recipient address does not match the connected network."
                }
                val normalizedRecipientAddress = addressValidation.normalizedAddress ?: recipientAddress
                val utxos = withContext(Dispatchers.IO) {
                    repository.loadUtxos(wallet.walletProfile.address.value)
                }
                val reservedOutPoints = withContext(Dispatchers.IO) {
                    submittedTransactionManager.listReservedOutPoints()
                }
                val spendableUtxos = filterSpendableUtxos(utxos, reservedOutPoints)
                val requestedTotalUnits = amountUnits + WalletSendService.DEFAULT_FEE_UNITS
                val currentSpendable = spendableUtxos.sumOf { it.valueUnits }
                if (requestedTotalUnits > currentSpendable) {
                    throw InsufficientFundsException(
                        "Insufficient spendable balance. Available to send: ${formatFinalisAmountLabel(currentSpendable)}.",
                    )
                }
                preview = sendService.buildAndSignTransaction(
                    wallet = wallet,
                    spendableUtxos = utxos,
                    recipientAddress = normalizedRecipientAddress,
                    amountUnits = amountUnits,
                    reservedOutPoints = reservedOutPoints,
                )
                previewState = preview?.let { builtPreview ->
                    buildSendPreviewState(
                        preview = builtPreview,
                        currentSpendableUnits = currentSpendableUnits,
                        currentReservedUnits = currentReservedUnits,
                    )
                }
            }
        } catch (error: Exception) {
            errorMessage = error.message ?: "Failed to prepare transaction"
        } finally {
            isSending = false
        }
    }

    suspend fun broadcastPreparedTransaction(
        walletAddress: String,
        repository: LightserverRepository,
        submittedTransactionManager: SubmittedTransactionManager,
        onAccepted: suspend () -> Unit,
        onNeedsRefresh: suspend () -> Unit,
    ) {
        if (isSending) return
        isSending = true
        errorMessage = null
        var shouldRefresh = false
        try {
            sendMutex.withLock {
                val currentPreview = preview ?: return@withLock
                val liveOutPoints = withContext(Dispatchers.IO) {
                    repository.loadUtxos(walletAddress)
                        .mapTo(mutableSetOf()) { utxo ->
                            WalletOutPoint(
                                txid = utxo.txid.lowercase(),
                                vout = utxo.vout,
                            )
                        }
                }
                val previewInputsStillSpendable = currentPreview.inputs.all { utxo ->
                    liveOutPoints.contains(
                        WalletOutPoint(
                            txid = utxo.txid.lowercase(),
                            vout = utxo.vout,
                        ),
                    )
                }
                if (!previewInputsStillSpendable) {
                    val stalePreviewResult = BroadcastResult(
                        accepted = false,
                        txid = currentPreview.txid,
                        status = TxStatus.REJECTED,
                        errorCode = BroadcastErrorCode.TX_MISSING_OR_UNCONFIRMED_INPUT,
                        error = "Finalized balance changed before send. Prepare the transaction again.",
                    )
                    preview = null
                    previewState = null
                    broadcastResult = stalePreviewResult
                    errorMessage = formatBroadcastResultMessage(stalePreviewResult)
                    shouldRefresh = true
                    return@withLock
                }
                val result = withContext(Dispatchers.IO) {
                    repository.broadcastTx(currentPreview.txHex)
                }
                broadcastResult = result
                if (result.accepted) {
                    withContext(Dispatchers.IO) {
                        submittedTransactionManager.recordSubmitted(currentPreview)
                    }
                    preview = null
                    previewState = null
                    recipientAddress = ""
                    amountUnitsInput = ""
                    onAccepted()
                } else {
                    if (result.errorCode == BroadcastErrorCode.TX_MISSING_OR_UNCONFIRMED_INPUT) {
                        preview = null
                        previewState = null
                        shouldRefresh = true
                    }
                    errorMessage = formatBroadcastResultMessage(result)
                }
            }
            if (shouldRefresh) {
                onNeedsRefresh()
            }
        } catch (error: Exception) {
            errorMessage = formatLightserverError(error)
        } finally {
            isSending = false
        }
    }

    private fun clearTransientState() {
        errorMessage = null
        preview = null
        previewState = null
        broadcastResult = null
    }
}

fun parseAmountUnits(raw: String): Long {
    val normalized = raw.trim()
    require(normalized.isNotEmpty()) { "Amount is required" }
    return parseFinalisUnits(normalized)
}

fun formatBroadcastResultMessage(result: BroadcastResult): String =
    when (result.errorCode?.name) {
        "BAD_TX_HEX" -> "Broadcast rejected: transaction hex was invalid"
        "TX_PARSE_FAILED" -> "Broadcast rejected: transaction parsing failed"
        "VALIDATION_FAILED" -> result.error ?: "Broadcast rejected: transaction validation failed"
        "TX_DUPLICATE" -> "Broadcast rejected: this transaction was already submitted or finalized"
        "TX_MISSING_OR_UNCONFIRMED_INPUT" -> result.error ?: "Balance changed before send. Prepare the transaction again."
        "TX_FEE_BELOW_MIN_RELAY" -> "Broadcast rejected: fee is below the current relay minimum"
        "MEMPOOL_FULL_NOT_GOOD_ENOUGH" -> "Broadcast rejected: fee rate is too low for current network pressure"
        "RELAY_UNAVAILABLE" -> "Broadcast relay unavailable on current endpoint"
        "UNKNOWN_REJECTION" -> result.error ?: "Broadcast rejected by the lightserver"
        else -> if (result.accepted) {
            "Submission accepted. Wait for finalized visibility before treating the transfer as settled."
        } else {
            result.error ?: "Broadcast failed"
        }
    }

fun formatLightserverError(error: Throwable): String {
    return classifyEndpointFailure(error).message
}
