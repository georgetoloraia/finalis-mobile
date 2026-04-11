# ADR-0002: Single-Key Single-Address Wallet

Decision: V1 uses one private key and one derived address.

Reason:
- matches current Finalis wallet and SDK assumptions
- minimizes restore/discovery complexity
- keeps cross-platform behavior deterministic
