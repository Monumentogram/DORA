# 6.2D engineering sandbox (synthetic S0 only)

This package is a prospective, task-owned diagnostic harness. It does not admit a provider or authorize private speech. Run only from a verified, exact repository commit in a private CloudShell session in `eu-central-1`. Do not run against the historical 6.2D resources.

The authored fixture is `fixtures/engineering-synthetic-ru.wav` (SHA-256 `3197cdd90229d945aefa33f0429fbe0849387df2f80291d761e16406139457d2`, mono PCM16/16 kHz, 3.32 seconds). `engineering_sandbox.py` resolves it relative to its own file, so the working directory does not affect the input. It sends only this synthetic input to Transcribe. The S0 request uses service-managed output with no Output fields or DataAccessRole. It is not the later admission reproduction and cannot unlock real audio.

Run the following stages separately from the repository root, inspecting each private artifact before continuing:

```sh
python3 tools/cloud62d_aws/engineering_sandbox.py provision
python3 tools/cloud62d_aws/engineering_sandbox.py input
python3 tools/cloud62d_aws/engineering_sandbox.py start
python3 tools/cloud62d_aws/engineering_sandbox.py poll
python3 tools/cloud62d_aws/engineering_sandbox.py cleanup
```

`provision` requires the expected root provisioning session and creates one scoped role, private input bucket, and private Lambda function. The role trusts only Lambda. CloudShell invokes that task-owned function; its role performs the exact S3/Transcribe runtime operations. Root performs provisioning, read-only independent verification, and administrative Lambda/role retirement. The exact bucket is deleted by the scoped runtime after whole-resource empty proof. No Lambda function URL or CloudWatch log write permission is created.

Private evidence is written under `~/dora-62d-eng-20260929-72c300a1/`. A single diagnostic ledger for both engineering and later admission modes lives outside the repository at `~/dora-62d-20260929-shared-diagnostics/diagnostic-ledger/`. Every Start consumes one of at most 12 durable reservations. A lost Start response remains unknown and must be reconciled by exact job name; never redispatch it. The worst-case diagnostic reservation is $0.12 within the existing $2 ASR allowance, and the combined prospective ceiling including $0.90 ancillary reserve is $9.3069 against $10.

Local verification:

```sh
python3 -B -m unittest discover -s tools/cloud62d_aws -p 'test_engineering_*.py' -v
sha256sum tools/cloud62d_aws/fixtures/engineering-synthetic-ru.wav
```

The fixture, harness, runtime, ledger, and tests are public synthetic artifacts. Do not commit CloudShell evidence, account identifiers, credentials, private audio, returned transcript URLs, or resource snapshots.
