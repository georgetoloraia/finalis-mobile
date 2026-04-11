# State Model

Server-backed truth:
- network status
- finalized balance
- finalized history
- finalized tx details

Client-local state:
- imported wallet profile
- lightserver RPC endpoint configuration
- submitted transactions awaiting finalization
- last refresh snapshot

V1 rule:
- balances and history shown as authoritative must come from finalized server data only
- local submitted state may be shown separately until a tx appears finalized
- finalized history pagination should follow lightserver `get_history_page`
- address normalization and script identity should follow lightserver `validate_address`
- if essential finalized-state fields are missing, the client should classify the backend as incompatible
- endpoint rotation should only mask network unavailability, not reachable incompatibility or network mismatch
