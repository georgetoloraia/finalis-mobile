# Local Setup

## Android
- install Java 17 and point Gradle at it via `JAVA_HOME` or Android Studio Gradle JDK settings
- verify your shell JDK with `java -version`
- verify the Gradle runtime and Java 17 toolchain with `cd android && ./gradlew -version` and `./gradlew verifyJava17Toolchain`
- install Android Studio / Android SDK
- run `./gradlew :app:assembleDebug` from `android/`
- run `./gradlew :data:lightserver:testDebugUnitTest :app:testDebugUnitTest` from `android/` in a Java 17 environment
- if Gradle reports no matching Java 17 toolchain, install JDK 17 and set `JAVA_HOME` to it before rerunning the commands
- for a live endpoint build, use `-Pfinalis.useMockLightserver=false -Pfinalis.lightserverRpcUrl=http://host:port/rpc`

## Lightserver
- use a reachable Finalis lightserver JSON-RPC endpoint at `/rpc`
- treat `finalis-core` lightserver as authoritative; explorer is optional and not required by the mobile client
- the Android wallet talks directly to lightserver; there is no separate gateway in the current mobile client
- `broadcast_tx` is submission only; finalized visibility comes from `get_tx_status`, `get_history_page`, and `get_tx`
- if essential finalized-state fields are missing or malformed, the mobile treats the backend as incompatible instead of guessing
- automatic endpoint failover should only happen for unavailable endpoints, not for reachable wrong-network or stale-contract backends
- use HTTPS for release builds

## Contract validation
- run `python shared/tooling/validate-fixtures/validate_fixtures.py`
