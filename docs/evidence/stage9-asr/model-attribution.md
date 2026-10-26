# Bounded Alpha model attribution

The optional model originates from OpenAI Whisper SMALL (multilingual), distributed
under the [OpenAI MIT notice](OpenAI-MIT.txt). The GGML conversion and q8_0 artifact
are from `ggerganov/whisper.cpp`, revision
`5359861c739e955e79d9a303bcbc70fb988958b1`:

[Pinned artifact source](https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q8_0.bin).

SHA256: `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
Size: 264464607 bytes. This repository contains no model weights.

The runtime uses whisper.cpp v1.9.4, commit
`927cfce34f31707e17f2bff35c349632fb9e2c3a`, with the complete
[ggml authors MIT notice](whisper-MIT.txt). The private native build retains the
NDK's complete NOTICE and NOTICE.toolchain alongside its controlled output.

Upstream conversion attribution and applicable notices must accompany separately
authorized controlled copies. This attribution does not grant project production
admission, independently extend a retention deadline, or claim that the historical
producer's converter/quantizer build was reproduced bit for bit.

The original 2026-10-25 retention authorization remains historical. The owner's
explicit 2026-10-26 renewal through 2026-11-02 inclusive (Europe/Moscow), limited
to custody and local testing of the same artifact, is recorded separately in the
[native admission record](../../stage1/DORA_STAGE9_NATIVE_ADMISSION.md).
