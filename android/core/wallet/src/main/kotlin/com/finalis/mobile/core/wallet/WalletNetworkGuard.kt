package com.finalis.mobile.core.wallet

import com.finalis.mobile.core.model.NetworkIdentity

data class NetworkMismatch(
    val code: String,
    val message: String,
)

object WalletNetworkGuard {
    fun detectMismatch(status: NetworkIdentity): NetworkMismatch? {
        if (status.name != FinalisMainnet.EXPECTED_NETWORK_NAME) {
            return NetworkMismatch(
                code = "invalid_network_name",
                message = "This app only supports Finalis mainnet. The endpoint reported ${status.name}.",
            )
        }

        if (status.networkId.lowercase() != FinalisMainnet.EXPECTED_NETWORK_ID) {
            return NetworkMismatch(
                code = "invalid_network_id",
                message = "This endpoint is not Finalis mainnet.",
            )
        }

        if (status.genesisHash.lowercase() != FinalisMainnet.EXPECTED_GENESIS_HASH) {
            return NetworkMismatch(
                code = "invalid_genesis_hash",
                message = "This endpoint is not Finalis mainnet.",
            )
        }

        return null
    }
}
