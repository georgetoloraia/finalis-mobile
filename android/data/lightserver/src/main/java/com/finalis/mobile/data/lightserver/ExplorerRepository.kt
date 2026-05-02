package com.finalis.mobile.data.lightserver

import com.finalis.mobile.core.model.AddressValidationResult
import com.finalis.mobile.core.model.BalanceSnapshot
import com.finalis.mobile.core.model.BroadcastResult
import com.finalis.mobile.core.model.HistoryEntry
import com.finalis.mobile.core.model.HistoryPageResult
import com.finalis.mobile.core.model.NetworkIdentity
import com.finalis.mobile.core.model.TxDetail
import com.finalis.mobile.core.model.TxDirection
import com.finalis.mobile.core.model.TxStatus
import com.finalis.mobile.core.model.WalletAddress
import com.finalis.mobile.core.model.WalletUtxo
import com.finalis.mobile.core.wallet.FinalisMainnet
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Connects to a Finalis Explorer REST API for all read operations and to the
 * Finalis lightserver JSON-RPC endpoint (same host, port 19444) for transaction
 * broadcast. The explorer is the stable, finalized-only surface; the lightserver
 * RPC is only used for [broadcastTx].
 *
 * @param explorerBaseUrl  Explorer base URL, e.g. http://host:18080
 * @param lightserverRpcUrl  Lightserver JSON-RPC URL, e.g. http://host:19444/rpc
 */
class ExplorerRepository(
    private val explorerBaseUrl: String,
    private val lightserverRpcUrl: String,
) : LightserverRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val explorerTransport = ExplorerHttpTransport(explorerBaseUrl)
    private val rpcTransport = UrlConnectionLightserverTransport(lightserverRpcUrl)

    /** Cached status DTO from the last successful [loadStatus] call. Used by [loadBalance] to avoid a
     *  redundant /api/status round-trip when called through [withValidatedEndpoint], which already
     *  probed the endpoint via [loadStatus] on the same instance before invoking [loadBalance]. */
    @Volatile
    private var lastStatusDto: ExplorerStatusDto? = null

    // ── Status ───────────────────────────────────────────────────────────────

    override suspend fun loadStatus(): NetworkIdentity {
        val status = explorerGet<ExplorerStatusDto>("/api/status")
        lastStatusDto = status
        if (!status.finalizedOnly) {
            throw LightserverBackendIncompatibleException(
                "Explorer is not in finalized-only mode (finalized_only was false)",
            )
        }
        if (status.network != FinalisMainnet.EXPECTED_NETWORK_NAME) {
            throw LightserverBackendIncompatibleException(
                "Explorer reports unexpected network: ${status.network}",
            )
        }
        return NetworkIdentity(
            name = status.network,
            networkId = status.networkId,
            genesisHash = status.genesisHash,
            tipHeight = status.finalizedHeight,
            tipHash = status.finalizedTransitionHash,
            finalizedHeight = status.finalizedHeight,
            finalizedHash = status.finalizedTransitionHash,
            serverTruth = "finalized_only",
            proofsTipOnly = true,
            version = status.backendVersion,
            syncMode = "finalized_only",
        )
    }

    // ── Address validation (local — explorer has no validate_address) ─────────

    override suspend fun validateAddress(address: String): AddressValidationResult =
        validateAddressLocally(address)

    // ── Balance ──────────────────────────────────────────────────────────────

    override suspend fun loadBalance(address: String): BalanceSnapshot {
        val validation = requireValidAddress(address)
        // Re-use the status already fetched by probeRpcEndpoint (same ExplorerRepository instance);
        // fall back to a fresh /api/status call only when the cache is absent.
        val statusDto = lastStatusDto ?: explorerGet<ExplorerStatusDto>("/api/status")
        val addressData = explorerGet<ExplorerAddressDto>("/api/address/${validation.normalizedAddress}")
        return BalanceSnapshot(
            address = WalletAddress(validation.normalizedAddress!!),
            confirmedUnits = addressData.finalizedBalance,
            asset = "FINALIS",
            tipHeight = statusDto.finalizedHeight,
            tipHash = statusDto.finalizedTransitionHash,
        )
    }

    // ── UTXOs ────────────────────────────────────────────────────────────────

    override suspend fun loadUtxos(address: String): List<WalletUtxo> {
        val validation = requireValidAddress(address)
        val addressData = explorerGet<ExplorerAddressDto>("/api/address/${validation.normalizedAddress}")
        if (!addressData.found) return emptyList()
        val utxos = addressData.utxos ?: return emptyList()
        val expectedScript = validation.scriptPubKeyHex!!
        return utxos
            .asSequence()
            .filter { it.height > 0L }
            .map { utxo ->
                WalletUtxo(
                    txid = utxo.txid.lowercase(),
                    vout = utxo.vout,
                    valueUnits = utxo.value,
                    height = utxo.height,
                    scriptPubKeyHex = utxo.scriptPubKeyHex?.lowercase() ?: expectedScript,
                )
            }
            .sortedWith(
                compareByDescending<WalletUtxo> { it.valueUnits }
                    .thenBy { it.txid }
                    .thenBy { it.vout },
            )
            .toList()
    }

    // ── History ──────────────────────────────────────────────────────────────

    override suspend fun loadHistoryPage(
        address: String,
        cursor: String?,
        limit: Int,
        fromHeight: Long?,
    ): HistoryPageResult {
        val validation = requireValidAddress(address)
        val params = buildList {
            if (cursor != null) add("cursor=$cursor")
            if (limit > 0) add("limit=$limit")
            if (fromHeight != null) add("from_height=$fromHeight")
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        val path = "/api/address/${validation.normalizedAddress}$query"
        val addressData = explorerGet<ExplorerAddressDto>(path)

        if (!addressData.found) {
            return HistoryPageResult(items = emptyList(), nextCursor = null, hasMore = false)
        }
        val history = addressData.history
            ?: return HistoryPageResult(items = emptyList(), nextCursor = null, hasMore = false)

        val items = history.items.map { item -> item.toHistoryEntry() }
        return HistoryPageResult(
            items = items,
            nextCursor = history.nextCursor,
            hasMore = history.hasMore,
        )
    }

    override suspend fun loadHistory(
        address: String,
        cursor: String?,
        limit: Int,
        fromHeight: Long?,
    ): List<HistoryEntry> = loadHistoryPage(address, cursor, limit, fromHeight).items

    // ── TX detail ────────────────────────────────────────────────────────────

    override suspend fun loadTxDetail(txid: String): TxDetail {
        val tx = explorerGet<ExplorerTxDto>("/api/tx/${txid.lowercase()}")
        if (!tx.finalized || tx.finalizedHeight == null) {
            throw LightserverDataException("Transaction $txid is not finalized")
        }
        return tx.toTxDetail()
    }

    override suspend fun findFinalizedTxDetail(txid: String): TxDetail? = try {
        val tx = explorerGet<ExplorerTxDto>("/api/tx/${txid.lowercase()}")
        if (tx.finalized && tx.finalizedHeight != null) tx.toTxDetail() else null
    } catch (_: LightserverRpcException) {
        null
    } catch (_: LightserverDataException) {
        null
    }

    // ── Broadcast (lightserver JSON-RPC) ─────────────────────────────────────

    /**
     * Broadcasts a signed transaction via the lightserver JSON-RPC endpoint.
     * The explorer has no raw-broadcast endpoint; we call the lightserver
     * directly for this operation only.
     */
    override suspend fun broadcastTx(txHex: String): BroadcastResult =
        LiveLightserverRepository(transport = rpcTransport).broadcastTx(txHex)

    // ── Internal helpers ─────────────────────────────────────────────────────

    private suspend inline fun <reified T> explorerGet(path: String): T {
        val body = explorerTransport.get(path)
        return try {
            json.decodeFromString<T>(body)
        } catch (exc: SerializationException) {
            throw LightserverDataException("Explorer response parsing failed for $path", exc)
        }
    }

    private fun requireValidAddress(address: String): AddressValidationResult {
        val result = validateAddressLocally(address)
        if (!result.valid) {
            throw LightserverAddressException(result.error ?: "Address is invalid", wrongNetwork = false)
        }
        if (result.serverNetworkMatch != true) {
            throw LightserverAddressException("Address does not match the connected network", wrongNetwork = true)
        }
        return result
    }
}

// ── Explorer HTTP transport ───────────────────────────────────────────────────

class ExplorerHttpTransport(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun get(path: String): String {
        val url = baseUrl.trimEnd('/') + path
        val connection = java.net.URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            when {
                statusCode == 404 -> {
                    val message = runCatching {
                        errorJson.decodeFromString<ExplorerErrorEnvelopeDto>(responseBody)
                            .error?.message ?: "Not found"
                    }.getOrElse { "Not found" }
                    throw LightserverRpcException(-32001, message)
                }
                statusCode !in 200..299 -> {
                    val message = runCatching {
                        errorJson.decodeFromString<ExplorerErrorEnvelopeDto>(responseBody)
                            .error?.message ?: "HTTP $statusCode"
                    }.getOrElse { "HTTP $statusCode" }
                    throw LightserverDataException("Explorer: $message")
                }
            }
            responseBody
        } catch (exc: java.io.IOException) {
            throw LightserverDataException("Explorer unavailable", exc)
        } finally {
            connection.disconnect()
        }
    }
}

// ── URL helpers ───────────────────────────────────────────────────────────────

/**
 * Normalises a user-supplied URL as an explorer base URL.
 * Also migrates legacy lightserver RPC URLs (ending in /rpc on port 19444)
 * to the explorer format (port 18080).
 */
fun normalizeExplorerUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    require(trimmed.isNotEmpty()) { "Endpoint URL is required" }
    val parsed = try {
        URI(trimmed)
    } catch (error: Exception) {
        throw IllegalArgumentException("Endpoint URL is invalid", error)
    }
    val scheme = parsed.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") { "Endpoint URL must start with http:// or https://" }
    require(!parsed.host.isNullOrBlank()) { "Endpoint URL must include a host" }

    // Auto-migrate legacy lightserver RPC URL → explorer URL
    if (parsed.path.orEmpty().trimEnd('/').endsWith("/rpc")) {
        val explorerPort = if (parsed.port == 19444) 18080 else parsed.port
        return URI(scheme, parsed.userInfo, parsed.host, explorerPort, "", null, null).toString()
    }

    val path = parsed.path.orEmpty().trimEnd('/')
    return URI(scheme, parsed.userInfo, parsed.host, parsed.port, path, null, null).toString()
}

/**
 * Derives the lightserver JSON-RPC URL from an explorer base URL by convention:
 * port 18080 → port 19444, path "/rpc".
 */
fun rpcUrlFromExplorerUrl(explorerUrl: String): String {
    val uri = URI(explorerUrl)
    val rpcPort = if (uri.port == 18080) 19444 else if (uri.port > 0) uri.port else 19444
    return URI(uri.scheme, uri.userInfo, uri.host, rpcPort, "/rpc", null, null).toString()
}

// ── Local address validation (Finalis bech32 / P2PKH) ────────────────────────

internal fun validateAddressLocally(address: String): AddressValidationResult {
    val trimmed = address.trim().lowercase()
    if (trimmed.isBlank()) {
        return AddressValidationResult(valid = false, error = "Address is empty")
    }
    val hrp = when {
        trimmed.startsWith("sc1") -> "sc"
        trimmed.startsWith("tsc1") -> "tsc"
        else -> return AddressValidationResult(valid = false, error = "Address has unknown network prefix")
    }
    val encodedPart = trimmed.substring(hrp.length + 1) // skip hrp + separator '1'
    val decoded = decodeBase32(encodedPart)
        ?: return AddressValidationResult(valid = false, error = "Address encoding is invalid")
    if (decoded.size < 5) {
        return AddressValidationResult(valid = false, error = "Address too short")
    }
    val payload = decoded.copyOfRange(0, decoded.size - 4)
    val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
    val expectedChecksum = doubleSha256Local(
        hrp.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) + payload,
    ).copyOfRange(0, 4)
    if (!checksum.contentEquals(expectedChecksum)) {
        return AddressValidationResult(valid = false, error = "Address checksum is invalid")
    }
    if (payload.isEmpty() || payload[0] != 0x00.toByte()) {
        return AddressValidationResult(valid = false, error = "Unsupported address version")
    }
    if (payload.size != 21) {
        return AddressValidationResult(valid = false, error = "Address payload wrong length")
    }
    val pubkeyHash = payload.copyOfRange(1, 21)
    val pubkeyHashHex = pubkeyHash.toHex()
    // P2PKH: OP_DUP OP_HASH160 <20-byte-hash> OP_EQUALVERIFY OP_CHECKSIG
    val scriptPubKey = byteArrayOf(0x76, 0xa9.toByte(), 0x14) + pubkeyHash +
        byteArrayOf(0x88.toByte(), 0xac.toByte())
    val scriptPubKeyHex = scriptPubKey.toHex()
    val scriptHashHex = MessageDigest.getInstance("SHA-256").digest(scriptPubKey).toHex()
    val isMainnet = hrp == FinalisMainnet.EXPECTED_HRP
    return AddressValidationResult(
        valid = true,
        normalizedAddress = trimmed,
        hrp = hrp,
        networkHint = hrp,
        serverNetworkHrp = FinalisMainnet.EXPECTED_HRP,
        serverNetworkMatch = isMainnet,
        addressType = "p2pkh",
        pubkeyHashHex = pubkeyHashHex,
        scriptPubKeyHex = scriptPubKeyHex,
        scriptHashHex = scriptHashHex,
    )
}

private fun decodeBase32(encoded: String): ByteArray? {
    val alphabet = "abcdefghijklmnopqrstuvwxyz234567"
    var buffer = 0
    var bits = 0
    val output = mutableListOf<Byte>()
    for (char in encoded.lowercase()) {
        val value = alphabet.indexOf(char)
        if (value == -1) return null
        buffer = (buffer shl 5) or value
        bits += 5
        if (bits >= 8) {
            output.add(((buffer shr (bits - 8)) and 0xff).toByte())
            bits -= 8
        }
    }
    return output.toByteArray()
}

private fun doubleSha256Local(input: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(digest.digest(input))
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

// ── Explorer REST DTOs ────────────────────────────────────────────────────────

@Serializable
private data class ExplorerStatusDto(
    val network: String,
    @SerialName("finalized_height") val finalizedHeight: Long,
    @SerialName("finalized_transition_hash") val finalizedTransitionHash: String,
    @SerialName("backend_version") val backendVersion: String? = null,
    @SerialName("network_id") val networkId: String? = null,
    @SerialName("genesis_hash") val genesisHash: String? = null,
    @SerialName("ticket_pow") val ticketPow: ExplorerTicketPowDto? = null,
    @SerialName("finalized_only") val finalizedOnly: Boolean = true,
)

@Serializable
private data class ExplorerTicketPowDto(
    val difficulty: Int? = null,
    @SerialName("difficulty_min") val difficultyMin: Int? = null,
    @SerialName("difficulty_max") val difficultyMax: Int? = null,
    @SerialName("epoch_health") val epochHealth: String? = null,
    @SerialName("streak_up") val streakUp: Int? = null,
    @SerialName("streak_down") val streakDown: Int? = null,
    @SerialName("nonce_search_limit") val nonceSearchLimit: Int? = null,
    @SerialName("bonus_cap_bps") val bonusCapBps: Int? = null,
)

@Serializable
private data class ExplorerAddressDto(
    val address: String,
    val found: Boolean,
    @SerialName("finalized_balance") val finalizedBalance: Long = 0L,
    val summary: ExplorerAddressSummaryDto? = null,
    val history: ExplorerHistoryDto? = null,
    val utxos: List<ExplorerUtxoDto>? = null,
    @SerialName("finalized_only") val finalizedOnly: Boolean = true,
)

@Serializable
private data class ExplorerAddressSummaryDto(
    @SerialName("finalized_balance") val finalizedBalance: Long = 0L,
    val received: Long = 0L,
    val sent: Long = 0L,
    @SerialName("self_transfer") val selfTransfer: Long = 0L,
)

@Serializable
private data class ExplorerHistoryDto(
    val items: List<ExplorerHistoryItemDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("next_page_path") val nextPagePath: String? = null,
    @SerialName("loaded_pages") val loadedPages: Int = 1,
)

@Serializable
private data class ExplorerHistoryItemDto(
    val txid: String,
    val height: Long,
    val direction: String,         // "received" | "sent" | "self_transfer"
    @SerialName("net_amount") val netAmount: Long, // signed: + = received, - = sent
    val detail: String? = null,
)

@Serializable
private data class ExplorerUtxoDto(
    val txid: String,
    val vout: Int,
    val value: Long,
    val height: Long,
    @SerialName("script_pubkey_hex") val scriptPubKeyHex: String? = null,
)

@Serializable
private data class ExplorerTxDto(
    val txid: String,
    val finalized: Boolean,
    @SerialName("finalized_height") val finalizedHeight: Long? = null,
    @SerialName("credit_safe") val creditSafe: Boolean = false,
    @SerialName("transition_hash") val transitionHash: String? = null,
    @SerialName("finalized_out") val finalizedOut: Long? = null,
    val fee: Long? = null,
    val flow: ExplorerTxFlowDto? = null,
    @SerialName("primary_sender") val primarySender: String? = null,
    @SerialName("primary_recipient") val primaryRecipient: String? = null,
    @SerialName("recipient_count") val recipientCount: Int? = null,
    @SerialName("finalized_only") val finalizedOnly: Boolean = true,
)

@Serializable
private data class ExplorerTxFlowDto(
    val kind: String? = null,
    val summary: String? = null,
)

@Serializable
private data class ExplorerErrorEnvelopeDto(
    val error: ExplorerErrorDetailDto? = null,
)

@Serializable
private data class ExplorerErrorDetailDto(
    val code: String? = null,
    val message: String? = null,
)

// ── Mapping helpers ───────────────────────────────────────────────────────────

private fun ExplorerHistoryItemDto.toHistoryEntry(): HistoryEntry {
    val net = netAmount
    val (dir, credited, debited) = when (direction.lowercase()) {
        "received" -> Triple(TxDirection.RECEIVE, net.coerceAtLeast(0L), 0L)
        "sent" -> Triple(TxDirection.SEND, 0L, (-net).coerceAtLeast(0L))
        "self_transfer" -> Triple(TxDirection.SELF, net.coerceAtLeast(0L), (-net).coerceAtLeast(0L))
        else -> if (net >= 0) Triple(TxDirection.RECEIVE, net, 0L) else Triple(TxDirection.SEND, 0L, -net)
    }
    return HistoryEntry(
        txid = txid.lowercase(),
        height = height,
        status = TxStatus.FINALIZED,
        direction = dir,
        creditedValue = credited,
        debitedValue = debited,
        netValue = net,
    )
}

private fun ExplorerTxDto.toTxDetail(): TxDetail = TxDetail(
    txid = txid.lowercase(),
    height = finalizedHeight!!,
    status = TxStatus.FINALIZED,
    finalizedTransitionHash = transitionHash?.lowercase(),
    finalizedDepth = 1,
    creditSafe = creditSafe,
    inputs = emptyList(),
    outputs = emptyList(),
)
