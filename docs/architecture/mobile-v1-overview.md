# Mobile V1 Overview

Finalis Mobile V1 is a non-custodial wallet product with Android as the first implementation target.

Frozen V1 decisions:
- one asset: Finalis
- one wallet model: single-key single-address
- chain state: finalized only
- signing: local on device only
- server edge: direct lightserver JSON-RPC
- no mint, staking, governance, multisig, watch-only, hardware wallet, swaps, or notifications

System shape:
1. Android app imports or creates one private key.
2. Android app derives one primary address.
3. Android app queries the published lightserver over JSON-RPC at `/rpc`.
4. Lightserver returns finalized chain data directly from node DB state.
5. Android signs transactions locally and sends raw tx hex directly to lightserver for submission.
6. Finalization is tracked from finalized-state lookup, not from `broadcast_tx` admission.

Design goals:
- minimal and trustworthy UX
- deterministic contract behavior
- strict separation between client signing and server state lookup
- explicit failure when the backend is stale or incompatible with the live lightserver contract
