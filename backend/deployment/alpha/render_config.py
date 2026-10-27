"""Offline stack Outputs -> private disabled LiveConfig. No AWS calls."""
import argparse
import json
import os
from pathlib import Path

from dora_cloud.config import LiveConfig


def build_config(outputs, owner):
    values = {row["OutputKey"]: row["OutputValue"] for row in outputs}
    return LiveConfig(
        live_enabled=False, account_id=values["AccountId"], owner_id=owner,
        region=values["Region"], input_bucket=values["InputBucket"],
        output_bucket=values["OutputBucket"], input_kms_arn=values["InputKeyArn"],
        output_kms_arn=values["OutputKeyArn"], ledger_path="/var/lib/dora/state.sqlite",
        custody_key_path="/var/lib/dora/custody.key",
    ).model_dump()


def write_private(path, values):
    path = Path(path).resolve()
    if path.is_relative_to(Path(__file__).resolve().parents[3]):
        raise ValueError("private configuration must be outside repository")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as target:
        json.dump(values, target, indent=2)
        target.write("\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--outputs", type=Path, required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    try:
        write_private(args.destination, build_config(json.loads(args.outputs.read_text(encoding="utf-8")), args.owner))
    except (OSError, ValueError, KeyError, TypeError):
        parser.exit(2, "PRIVATE_CONFIGURATION_NOT_WRITTEN\n")
    print("DISABLED_CONFIGURATION_WRITTEN")


if __name__ == "__main__":
    main()
