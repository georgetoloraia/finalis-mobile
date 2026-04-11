# finalis-mobile

`finalis-mobile` is the Android-first native mobile client repository for the Finalis wallet product.

V1 scope is intentionally narrow:
- Finalis only
- single-key single-address wallet
- finalized-only server truth
- local signing only
- direct lightserver JSON-RPC access
- Android only

Live backend contract:
- `finalis-core` lightserver is the authoritative integration surface
- explorer is optional and thin on top of lightserver
- wallet settlement visibility comes from finalized-state lookup, not relay admission
- `broadcast_tx` is submission only and must not be treated as finalization
- address lookup and validation must follow live `validate_address`, `get_utxos`, `get_history_page`, `get_tx_status`, and `get_tx`
- finalized history browsing follows the lightserver paging contract and should surface explicit end-of-history and paging errors
- if essential finalized-state fields are missing, the mobile treats the backend as incompatible
- endpoint failover is only for unavailable backends; reachable wrong-network or incompatible backends are surfaced explicitly

Repository areas:
- `docs/`: architecture, ADRs, delivery notes, and local setup
- `shared/`: fixtures and validation tooling
- `android/`: Android multi-module app with direct lightserver integration

Android build requirement:
- use a Java 17 toolchain for Gradle and Android tasks
- do not rely on a repo-pinned local JDK path; contributors should provide Java 17 via `JAVA_HOME` or Android Studio Gradle JDK settings
- verify the active toolchain with `java -version` and `cd android && ./gradlew -version`
- verify the required Java toolchain with `cd android && ./gradlew verifyJava17Toolchain`
- run unit tests from `android/` with `./gradlew :data:lightserver:testDebugUnitTest :app:testDebugUnitTest`

The Android app imports a raw V1 private key, derives the canonical address locally, signs locally, validates addresses against the connected lightserver, and talks directly to published Finalis lightservers over JSON-RPC at `/rpc`.
