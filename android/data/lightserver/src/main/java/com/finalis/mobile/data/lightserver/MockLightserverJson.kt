package com.finalis.mobile.data.lightserver

internal object MockLightserverJson {
    private const val WalletAddress = "sc1adrqzotqb6xl76opjawtlcn5ds6msa6sqaw7dj7p"
    private const val WalletScriptPubKey = "76a914e30cba700faebff9cf482d3589bd1cbcc903d28088ac"
    private const val WalletScriptHash = "5af54089c37ef238852e9288de75a7a4ccdd53a42dcd36ee155d3467c6d1f682"
    private const val TransitionHash = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    private const val ReceiveTxHex =
        "0100000001" +
            "0000000000000000000000000000000000000000000000000000000000000000" +
            "00000000" +
            "00" +
            "ffffffff" +
            "01" +
            "0065cd1d00000000" +
            "19" +
            WalletScriptPubKey +
            "00000000"

    val status = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "network_name": "mainnet",
            "protocol_version": 1,
            "feature_flags": 1,
            "network_id": "a57ab83946712672c507b1bd312c5fb2",
            "magic": 1396919630,
            "genesis_hash": "5bc5efaa355d082b1951843816de1fa56db3051fc950db9844c9f269b802f6ce",
            "genesis_source": "embedded",
            "chain_id_ok": true,
            "tip": {
              "height": 12345,
              "transition_hash": "$TransitionHash"
            },
            "finalized_tip": {
              "height": 12345,
              "transition_hash": "$TransitionHash"
            },
            "finalized_height": 12345,
            "finalized_transition_hash": "$TransitionHash",
            "healthy_peer_count": 4,
            "established_peer_count": 5,
            "version": "finalis-core/0.7",
            "binary": "finalis-lightserver",
            "binary_version": "finalis-lightserver/0.7",
            "wallet_api_version": "FINALIS_WALLET_API_V1",
            "availability": {
              "checkpoint_derivation_mode": "normal",
              "checkpoint_fallback_reason": "none",
              "fallback_sticky": false,
              "adaptive_regime": {
                "qualified_depth": 31,
                "adaptive_target_committee_size": 24,
                "adaptive_min_eligible": 27,
                "adaptive_min_bond": 100000000,
                "slack": 4,
                "target_expand_streak": 4,
                "target_contract_streak": 0,
                "fallback_rate_bps": 0,
                "sticky_fallback_rate_bps": 0,
                "fallback_rate_window_epochs": 16,
                "near_threshold_operation": false,
                "prolonged_expand_buildup": false,
                "prolonged_contract_buildup": false,
                "repeated_sticky_fallback": false,
                "depth_collapse_after_bond_increase": false
              }
            },
            "sync": {
              "mode": "finalized_only",
              "snapshot_present": true,
              "local_finalized_height": 12345,
              "observed_network_height_known": true,
              "observed_network_finalized_height": 12345,
              "finalized_lag": 0,
              "bootstrap_sync_incomplete": false,
              "peer_height_disagreement": false,
              "next_height_committee_available": true,
              "next_height_proposer_available": true
            },
            "adaptive_telemetry_summary": {
              "window_epochs": 16,
              "sample_count": 16,
              "fallback_epochs": 0,
              "sticky_fallback_epochs": 0
            }
          }
        }
    """.trimIndent()

    val validateAddress = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "valid": true,
            "normalized_address": "$WalletAddress",
            "hrp": "sc",
            "network_hint": "mainnet",
            "server_network_hrp": "sc",
            "server_network_match": true,
            "addr_type": "p2pkh",
            "pubkey_hash_hex": "e30cba700faebff9cf482d3589bd1cbcc903d280",
            "script_pubkey_hex": "$WalletScriptPubKey",
            "scripthash_hex": "$WalletScriptHash",
            "error": null
          }
        }
    """.trimIndent()

    val utxos = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": [
            {
              "txid": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
              "vout": 0,
              "value": 500000000,
              "height": 12340,
              "script_pubkey_hex": "$WalletScriptPubKey"
            }
          ]
        }
    """.trimIndent()

    val historyPage = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "items": [
              {
                "txid": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "height": 12340
              }
            ],
            "has_more": false,
            "ordering": "height_asc_txid_asc",
            "next_start_after": null
          }
        }
    """.trimIndent()

    val txStatus = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "txid": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            "status": "finalized",
            "finalized": true,
            "height": 12340,
            "finalized_depth": 6,
            "credit_safe": true,
            "transition_hash": "$TransitionHash"
          }
        }
    """.trimIndent()

    val tx = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "height": 12340,
            "tx_hex": "$ReceiveTxHex"
          }
        }
    """.trimIndent()

    val broadcastResult = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "accepted": true,
            "status": "accepted_for_relay",
            "finalized": false,
            "txid": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
          }
        }
    """.trimIndent()

    const val KnownWalletAddress: String = WalletAddress
    const val KnownTransitionHash: String = TransitionHash
}
