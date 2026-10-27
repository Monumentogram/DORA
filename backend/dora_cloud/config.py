"""Private operator configuration, deliberately unusable when incomplete."""
import json
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class LiveConfig(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    live_enabled: bool = False
    account_id: str = Field(pattern=r"^[0-9]{12}$")
    owner_id: str = Field(min_length=1, max_length=128)
    region: Literal["eu-central-1"] = "eu-central-1"
    input_bucket: str = Field(pattern=r"^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")
    output_bucket: str = Field(pattern=r"^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")
    input_kms_arn: str
    output_kms_arn: str
    ledger_path: str = Field(min_length=1)
    custody_key_path: str = Field(min_length=1)
    daily_reserve_usd: str = "0.25"
    per_minute_reserve_usd: str = "0.03"
    ancillary_per_attempt_usd: str = "0.01"

    @model_validator(mode="after")
    def isolated_resources(self):
        import re
        prefix = f"arn:aws:kms:eu-central-1:{self.account_id}:key/"
        for key in (self.input_kms_arn, self.output_kms_arn):
            if not key.startswith(prefix) or not re.fullmatch(r"[0-9a-f-]{36}", key[len(prefix):]):
                raise ValueError("exact regional CMK ARN required")
        if self.input_kms_arn == self.output_kms_arn or self.input_bucket == self.output_bucket:
            raise ValueError("separate input/output custody required")
        from .service import Policy
        Policy(self.daily_reserve_usd, self.per_minute_reserve_usd, self.ancillary_per_attempt_usd)
        return self

    @classmethod
    def load(cls, path):
        return cls.model_validate(json.loads(Path(path).read_text(encoding="utf-8")))

    def namespace(self):
        return {k: getattr(self, k) for k in ("account_id", "owner_id", "region", "input_bucket", "output_bucket", "input_kms_arn", "output_kms_arn")}
