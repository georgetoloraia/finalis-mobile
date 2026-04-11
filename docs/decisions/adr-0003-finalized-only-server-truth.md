# ADR-0003: Finalized-Only Server Truth

Decision: server-provided wallet state is finalized only.

Reason:
- aligns with Finalis lightserver semantics
- avoids ambiguous pending-balance logic
- keeps wallet UX conservative and trustworthy
