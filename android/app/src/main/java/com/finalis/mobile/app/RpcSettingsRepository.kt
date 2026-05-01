package com.finalis.mobile.app

import android.content.Context
import android.content.SharedPreferences
import com.finalis.mobile.core.model.AddressValidationResult
import com.finalis.mobile.core.model.BalanceSnapshot
import com.finalis.mobile.core.model.BroadcastResult
import com.finalis.mobile.core.model.HistoryEntry
import com.finalis.mobile.core.model.HistoryPageResult
import com.finalis.mobile.core.model.NetworkIdentity
import com.finalis.mobile.core.model.TxDetail
import com.finalis.mobile.core.model.WalletUtxo
import com.finalis.mobile.core.wallet.WalletNetworkGuard
import com.finalis.mobile.data.lightserver.ExplorerRepository
import com.finalis.mobile.data.lightserver.LightserverAddressException
import com.finalis.mobile.data.lightserver.LightserverBackendIncompatibleException
import com.finalis.mobile.data.lightserver.LightserverDataException
import com.finalis.mobile.data.lightserver.LightserverRepository
import com.finalis.mobile.data.lightserver.LightserverRpcException
import com.finalis.mobile.data.lightserver.MockLightserverRepository
import com.finalis.mobile.data.lightserver.normalizeExplorerUrl
import com.finalis.mobile.data.lightserver.rpcUrlFromExplorerUrl

data class PersistedRpcSettings(
    val savedEndpoints: List<RpcEndpoint>,
    val activeEndpoint: RpcEndpoint?,
) {
    fun orderedEndpoints(): List<RpcEndpoint> {
        val active = activeEndpoint
        return if (active == null) {
            savedEndpoints
        } else {
            listOf(active) + savedEndpoints.filterNot { it.url == active.url }
        }
    }
}

interface RpcEndpointSettingsStore {
    fun loadSettings(): PersistedRpcSettings
    fun setActiveEndpoint(endpoint: RpcEndpoint)
}

class RpcSettingsRepository(
    private val preferences: SharedPreferences,
) : RpcEndpointSettingsStore {
    constructor(
        context: Context,
    ) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )
    override fun loadSettings(): PersistedRpcSettings {
        if (BuildConfig.USE_MOCK_LIGHTSERVER) {
            return PersistedRpcSettings(
                savedEndpoints = emptyList(),
                activeEndpoint = null,
            )
        }
        val defaultEndpoints = defaultRpcEndpoints()
        val savedEndpoints = (parseEndpoints(preferences.getString(KEY_ENDPOINTS, null)) + defaultEndpoints)
            .distinctBy { it.url }
        val activeUrl = preferences.getString(KEY_ACTIVE_ENDPOINT, null)
        val activeEndpoint = savedEndpoints.firstOrNull { it.url == activeUrl } ?: savedEndpoints.firstOrNull()
        val settings = PersistedRpcSettings(
            savedEndpoints = savedEndpoints,
            activeEndpoint = activeEndpoint,
        )
        return settings
    }

    fun loadUiState(
        inputValue: String = "",
        message: String? = null,
    ): RpcSettingsState {
        val settings = loadSettings()
        return RpcSettingsState(
            savedEndpoints = settings.savedEndpoints,
            activeEndpoint = settings.activeEndpoint,
            inputValue = inputValue,
            message = message,
        )
    }

    fun addEndpoint(endpoint: RpcEndpoint) {
        val settings = loadSettings()
        val savedEndpoints = (settings.savedEndpoints + endpoint).distinctBy { it.url }
        saveSettings(
            savedEndpoints = savedEndpoints,
            activeEndpoint = endpoint,
        )
    }

    fun removeEndpoint(endpoint: RpcEndpoint) {
        val settings = loadSettings()
        val savedEndpoints = settings.savedEndpoints.filterNot { it.url == endpoint.url }
        val activeEndpoint = when {
            settings.activeEndpoint?.url == endpoint.url -> savedEndpoints.firstOrNull()
            savedEndpoints.any { it.url == settings.activeEndpoint?.url } -> settings.activeEndpoint
            else -> savedEndpoints.firstOrNull()
        }
        saveSettings(
            savedEndpoints = savedEndpoints,
            activeEndpoint = activeEndpoint,
        )
    }

    override fun setActiveEndpoint(endpoint: RpcEndpoint) {
        val settings = loadSettings()
        val savedEndpoints = listOf(endpoint) + settings.savedEndpoints.filterNot { it.url == endpoint.url }
        saveSettings(
            savedEndpoints = savedEndpoints,
            activeEndpoint = endpoint,
        )
    }

    private fun saveSettings(
        savedEndpoints: List<RpcEndpoint>,
        activeEndpoint: RpcEndpoint?,
    ) {
        val committed = preferences.edit()
            .putString(KEY_ENDPOINTS, savedEndpoints.joinToString(separator = "\n") { it.url })
            .putString(KEY_ACTIVE_ENDPOINT, activeEndpoint?.url)
            .commit()
        check(committed) { "Failed to persist RPC settings" }
    }

    private fun parseEndpoints(raw: String?): List<RpcEndpoint> =
        raw.orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { candidate ->
                runCatching { RpcEndpoint(normalizeExplorerUrl(candidate)) }.getOrNull()
            }
            .distinctBy { it.url }
            .toList()

    companion object {
        private const val PREFERENCES_NAME = "rpc_settings"
        private const val KEY_ENDPOINTS = "saved_endpoints"
        private const val KEY_ACTIVE_ENDPOINT = "active_endpoint"
        internal val BUILTIN_FALLBACK_ENDPOINTS = listOf(
            "http://85.217.171.168:18080",
            "http://64.23.244.126:18080",
        )
    }
}

private fun defaultRpcEndpoints(): List<RpcEndpoint> =
    (listOf(BuildConfig.EXPLORER_BASE_URL) + RpcSettingsRepository.BUILTIN_FALLBACK_ENDPOINTS)
        .map { normalizeExplorerUrl(it) }
        .distinct()
        .map(::RpcEndpoint)

class RuntimeLightserverRepository(
    private val endpointSettingsStore: RpcEndpointSettingsStore,
    private val repositoryFactory: (RpcEndpoint) -> LightserverRepository = ::explorerRepositoryFor,
) : LightserverRepository {
    override suspend fun loadStatus(): NetworkIdentity {
        val endpoints = endpointSettingsStore.loadSettings().orderedEndpoints()
        require(endpoints.isNotEmpty()) { "No lightserver endpoints configured" }
        var lastFailureMessage: String? = null
        for (endpoint in endpoints) {
            val repository = repositoryFactory(endpoint)
            val probe = probeRpcEndpoint(endpoint, repositoryFactory = { _ -> repository })
            when {
                probe.status != null -> {
                    endpointSettingsStore.setActiveEndpoint(endpoint)
                    return probe.status
                }
                probe.error?.kind == EndpointErrorKind.UNAVAILABLE -> {
                    lastFailureMessage = probeDisplayMessage(probe)
                }
                probe.error != null -> {
                    throw LightserverDataException(probeDisplayMessage(probe))
                }
                else -> {
                    lastFailureMessage = probeDisplayMessage(probe)
                }
            }
        }
        throw LightserverDataException(lastFailureMessage ?: "No reachable Finalis lightserver endpoint")
    }

    override suspend fun validateAddress(address: String) =
        withValidatedEndpoint { repository, _, _ -> repository.validateAddress(address) }

    override suspend fun loadBalance(address: String): BalanceSnapshot =
        withValidatedEndpoint { repository, _, _ -> repository.loadBalance(address) }

    override suspend fun loadUtxos(address: String): List<WalletUtxo> =
        withValidatedEndpoint { repository, _, _ -> repository.loadUtxos(address) }

    override suspend fun loadHistoryPage(address: String, cursor: String?, limit: Int, fromHeight: Long?): HistoryPageResult =
        withValidatedEndpoint { repository, _, _ -> repository.loadHistoryPage(address, cursor, limit, fromHeight) }

    override suspend fun loadHistory(address: String, cursor: String?, limit: Int, fromHeight: Long?): List<HistoryEntry> =
        withValidatedEndpoint { repository, _, _ -> repository.loadHistory(address, cursor, limit, fromHeight) }

    override suspend fun loadTxDetail(txid: String): TxDetail =
        withValidatedEndpoint { repository, _, _ -> repository.loadTxDetail(txid) }

    override suspend fun findFinalizedTxDetail(txid: String): TxDetail? =
        withValidatedEndpoint { repository, _, _ -> repository.findFinalizedTxDetail(txid) }

    override suspend fun broadcastTx(txHex: String): BroadcastResult {
        val endpoints = endpointSettingsStore.loadSettings().orderedEndpoints()
        require(endpoints.isNotEmpty()) { "No lightserver endpoints configured" }
        var lastResult: BroadcastResult? = null
        var lastFailureMessage: String? = null
        for (endpoint in endpoints) {
            val repository = repositoryFactory(endpoint)
            val probe = probeRpcEndpoint(endpoint, repositoryFactory = { _ -> repository })
            if (!probe.isValid) {
                if (probe.error?.kind == EndpointErrorKind.UNAVAILABLE) {
                    lastFailureMessage = probeDisplayMessage(probe)
                    continue
                }
                throw LightserverDataException(probeDisplayMessage(probe))
            }
            try {
                val result = repository.broadcastTx(txHex)
                if (result.accepted) {
                    endpointSettingsStore.setActiveEndpoint(endpoint)
                    return result
                }
                lastResult = result
                if (result.errorCode != com.finalis.mobile.core.model.BroadcastErrorCode.RELAY_UNAVAILABLE) {
                    endpointSettingsStore.setActiveEndpoint(endpoint)
                    return result
                }
                lastFailureMessage = result.error ?: "Relay unavailable"
            } catch (error: Exception) {
                val failure = classifyEndpointFailure(error)
                if (failure.kind == EndpointErrorKind.UNAVAILABLE) {
                    lastFailureMessage = "${endpoint.url}: ${failure.message}"
                    continue
                }
                throw error
            }
        }
        return lastResult ?: throw LightserverDataException(lastFailureMessage ?: "No reachable Finalis lightserver endpoint")
    }

    private suspend fun <T> withValidatedEndpoint(
        block: suspend (LightserverRepository, NetworkIdentity, RpcEndpoint) -> T,
    ): T {
        val endpoints = endpointSettingsStore.loadSettings().orderedEndpoints()
        require(endpoints.isNotEmpty()) { "No lightserver endpoints configured" }
        var lastFailureMessage: String? = null
        for (endpoint in endpoints) {
            val repository = repositoryFactory(endpoint)
            val probe = probeRpcEndpoint(endpoint, repositoryFactory = { _ -> repository })
            if (!probe.isValid) {
                if (probe.error?.kind == EndpointErrorKind.UNAVAILABLE) {
                    lastFailureMessage = probeDisplayMessage(probe)
                    continue
                }
                throw LightserverDataException(probeDisplayMessage(probe))
            }
            try {
                val result = block(repository, probe.status!!, endpoint)
                endpointSettingsStore.setActiveEndpoint(endpoint)
                return result
            } catch (error: Exception) {
                val failure = classifyEndpointFailure(error)
                if (failure.kind == EndpointErrorKind.UNAVAILABLE) {
                    lastFailureMessage = "${endpoint.url}: ${failure.message}"
                    continue
                }
                throw error
            }
        }
        throw LightserverDataException(lastFailureMessage ?: "No reachable Finalis lightserver endpoint")
    }
}

suspend fun probeRpcEndpoint(
    endpoint: RpcEndpoint,
    repositoryFactory: (RpcEndpoint) -> LightserverRepository = ::explorerRepositoryFor,
): EndpointProbeResult {
    return try {
        val repository = repositoryFactory(endpoint)
        val status = repository.loadStatus()
        val mismatch = WalletNetworkGuard.detectMismatch(status)
        EndpointProbeResult(
            endpoint = endpoint,
            status = status,
            mismatch = mismatch,
        )
    } catch (error: Exception) {
        EndpointProbeResult(
            endpoint = endpoint,
            error = classifyEndpointFailure(error),
        )
    }
}

fun createLightserverRepository(
    rpcSettingsRepository: RpcSettingsRepository,
): LightserverRepository =
    if (BuildConfig.USE_MOCK_LIGHTSERVER) {
        MockLightserverRepository()
    } else {
        RuntimeLightserverRepository(
            endpointSettingsStore = rpcSettingsRepository,
        )
    }

// normalizeRpcUrl kept for callers that still pass lightserver RPC URLs;
// new code should use normalizeExplorerUrl from ExplorerRepository.kt.
fun normalizeRpcUrl(rawUrl: String): String = normalizeExplorerUrl(rawUrl)

private fun explorerRepositoryFor(endpoint: RpcEndpoint): LightserverRepository =
    ExplorerRepository(
        explorerBaseUrl = endpoint.url,
        lightserverRpcUrl = rpcUrlFromExplorerUrl(endpoint.url),
    )

private fun probeDisplayMessage(probe: EndpointProbeResult): String =
    when {
        probe.mismatch != null -> "${probe.endpoint.url}: ${probe.mismatch.message}"
        probe.error != null -> "${probe.endpoint.url}: ${probe.error.message}"
        else -> "${probe.endpoint.url}: endpoint probe failed"
    }

fun classifyEndpointFailure(error: Throwable): EndpointFailure {
    val message = error.message ?: return EndpointFailure(
        kind = EndpointErrorKind.UNKNOWN,
        message = "Lightserver request failed",
    )
    return when (error) {
        is LightserverAddressException -> EndpointFailure(
            kind = if (error.wrongNetwork) EndpointErrorKind.ADDRESS_WRONG_NETWORK else EndpointErrorKind.ADDRESS_INVALID,
            message = message,
        )
        is LightserverBackendIncompatibleException -> EndpointFailure(
            kind = EndpointErrorKind.INCOMPATIBLE,
            message = "The endpoint does not satisfy the live Finalis lightserver contract. $message",
        )
        is LightserverRpcException -> EndpointFailure(
            kind = EndpointErrorKind.RPC_ERROR,
            message = "The lightserver returned an RPC error. $message",
        )
        is LightserverDataException -> when {
            "unavailable" in message.lowercase() || "timeout" in message.lowercase() || "http " in message.lowercase() ->
                EndpointFailure(EndpointErrorKind.UNAVAILABLE, formatEndpointErrorMessage(message))
            else ->
                EndpointFailure(EndpointErrorKind.INCOMPATIBLE, formatEndpointErrorMessage(message))
        }
        else -> when {
            generateSequence(error) { it.cause }.any { it is java.io.IOException } ->
                EndpointFailure(EndpointErrorKind.UNAVAILABLE, formatEndpointErrorMessage(message))
            else ->
                EndpointFailure(EndpointErrorKind.UNKNOWN, message)
        }
    }
}

private fun formatEndpointErrorMessage(message: String): String =
    when {
        "upstream unavailable" in message.lowercase() || "unavailable" in message.lowercase() -> "The lightserver is unavailable"
        "timeout" in message.lowercase() -> "The lightserver request timed out"
        "parsing failed" in message.lowercase() -> "The lightserver returned malformed data"
        "missing result" in message.lowercase() -> "The lightserver response was incomplete"
        "http " in message.lowercase() -> "The lightserver returned an HTTP error"
        else -> message
    }
