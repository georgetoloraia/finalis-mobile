# Android Architecture

Android uses a multi-module structure.

Pure Kotlin modules:
- `core:model`
- `core:common`
- `core:crypto`
- `core:wallet`
- `core:rpc`
- `core:testing`

Android-dependent modules:
- `app`
- `data:lightserver`
- `data:wallet`
- `feature:*`
- `sync`
- `benchmark`

Rules:
- wallet rules live outside UI modules
- feature modules do not depend on raw lightserver JSON-RPC directly
- submitted transaction state is local and separate from finalized server truth
