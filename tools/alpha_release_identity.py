"""Shared product identity; historical release records remain immutable."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APPLICATION_ID = "com.monumentogram.dora"
CERTIFICATE_SHA256 = "e77c9af533e603c0fa7c5c2000eb74b9514d001d98c3e5845cc2bae905be76bf"
DISTRIBUTION = "OWNER_ONLY_CLOSED_INTERNAL_ALPHA"


def identity(root: Path = ROOT) -> tuple[int, str]:
    values = {}
    for line in (root / "android/alpha-release.properties").read_text(encoding="ascii").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        if key in values:
            raise ValueError("Duplicate product identity field")
        values[key] = value
    if set(values) != {"versionCode", "versionName"}:
        raise ValueError("Invalid product identity fields")
    code, name = int(values["versionCode"]), values["versionName"]
    if code < 4 or not re.fullmatch(r"0\.1\.0-alpha\.[2-9][0-9]*", name):
        raise ValueError("Historical product identity cannot be reused")
    return code, name
