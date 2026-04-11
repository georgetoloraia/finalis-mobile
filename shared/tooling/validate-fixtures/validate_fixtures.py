from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
INDEX = ROOT / "shared" / "test-vectors" / "fixtures-index.json"
EXAMPLES = ROOT / "docs" / "contracts" / "examples"
REQUIRED_EXAMPLES = [
    "status.json",
    "balance.json",
    "history-page.json",
    "tx-detail.json",
    "broadcast-result.json",
]


def main() -> int:
    data = json.loads(INDEX.read_text())
    fixtures = data.get("fixtures", [])
    if not fixtures:
        print("fixtures-index.json must contain at least one fixture entry", file=sys.stderr)
        return 1
    for rel in fixtures:
        path = ROOT / "shared" / rel
        if not path.exists():
            print(f"missing fixture path: shared/{rel}", file=sys.stderr)
            return 1
    for name in REQUIRED_EXAMPLES:
        path = EXAMPLES / name
        if not path.exists():
            print(f"missing example contract file: {name}", file=sys.stderr)
            return 1
        json.loads(path.read_text())
    print("fixture validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
