# ADR-0004: Gateway In Front Of Lightserver

Decision: Android calls published Finalis lightservers directly over JSON-RPC at `/rpc`.

Reason:
- stable mobile-facing contract
- request validation and error normalization
- future rate limiting, observability, and multi-endpoint handling
