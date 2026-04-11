package com.finalis.mobile.core.wallet

import com.finalis.mobile.core.model.NetworkIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WalletNetworkGuardTest {
    @Test
    fun `returns null for finalis mainnet status`() {
        val mismatch = WalletNetworkGuard.detectMismatch(mainnetStatus())

        assertNull(mismatch)
    }

    @Test
    fun `blocks non-mainnet network name`() {
        val mismatch = WalletNetworkGuard.detectMismatch(mainnetStatus(name = "testnet"))

        assertEquals("invalid_network_name", mismatch?.code)
    }

    @Test
    fun `blocks non-mainnet network id`() {
        val mismatch = WalletNetworkGuard.detectMismatch(mainnetStatus(networkId = "ff".repeat(16)))

        assertEquals("invalid_network_id", mismatch?.code)
    }

    @Test
    fun `blocks non-mainnet genesis hash`() {
        val mismatch = WalletNetworkGuard.detectMismatch(mainnetStatus(genesisHash = "aa".repeat(32)))

        assertEquals("invalid_genesis_hash", mismatch?.code)
    }

    private fun mainnetStatus(
        name: String = FinalisMainnet.EXPECTED_NETWORK_NAME,
        networkId: String = FinalisMainnet.EXPECTED_NETWORK_ID,
        genesisHash: String = FinalisMainnet.EXPECTED_GENESIS_HASH,
    ) = NetworkIdentity(
        name = name,
        networkId = networkId,
        protocolVersion = 1,
        featureFlags = 1L,
        genesisHash = genesisHash,
        tipHeight = 1L,
        tipHash = "22".repeat(32),
        serverTruth = "finalized_only",
        proofsTipOnly = true,
    )
}
