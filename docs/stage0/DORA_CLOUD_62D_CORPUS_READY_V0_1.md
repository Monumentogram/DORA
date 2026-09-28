# DORA 6.2D: owned corpus ready v0.1

The real private eight-record corpus is collected and finalized: RU four records (two READ, two SPONTANEOUS), EN four basic READ records. All eight reference texts were explicitly confirmed by the speaker in the Android app. Those saved confirmations were imported; the assistant did not infer word accuracy from recording completion.

RU contains 179 normalized reference words and 150.96 seconds; EN contains 181 words and 129.56 seconds. These are actual future scoring denominators, not recognition results. WER remains NOT_RUN, with unchanged RU 20% and EN 18% limits. No pronunciation assessment or provider-quality claim is made. The natural recordings were retained without retakes to improve provider performance.

Source WAVs, verified references, correction history and package audit remain private. Four technical composites (300 and 599.999 seconds per language) were generated from these recordings; they do not add independent quality samples. Original phone data was not cleared or changed during collection.

This closes the corpus preparation blocker only. AWS configuration is still unverified; no full Phase A successor or AWS evaluation has been created/run. The complete bound Phase A must be published and refetched before the first AWS run. USD10, privacy and the prohibition on 6.3 remain. English spontaneous speech, noise robustness and timestamp accuracy remain NOT_EVALUATED; one speaker cannot establish population-wide quality.

The following is the exact public-safe machine record.

```json
{
  "schema_version": "1.0",
  "status": "OWNED_CORPUS_READY_AWS_CONFIGURATION_UNVERIFIED",
  "source_commit": "02568293c44b6e0cb7452cec333911efc19f8486",
  "protocol_id": "dora-owned-easy-en8-v3",
  "recordings": 8,
  "human_verified_references": 8,
  "composition": {
    "ru": {
      "READ": 2,
      "SPONTANEOUS": 2
    },
    "en": {
      "READ": 4,
      "SPONTANEOUS": 0
    }
  },
  "languages": {
    "ru": {
      "duration_us": 150960000,
      "records": 4,
      "reference_words": 179,
      "verified_references": 4,
      "wer_percent": null,
      "wer_status": "NOT_RUN",
      "wer_threshold_percent": 20
    },
    "en": {
      "duration_us": 129560000,
      "records": 4,
      "reference_words": 181,
      "verified_references": 4,
      "wer_percent": null,
      "wer_status": "NOT_RUN",
      "wer_threshold_percent": 18
    }
  },
  "reference_word_count_basis": "NORMALIZED_PERSONALLY_VERIFIED_ACTUAL_WORDS_NOT_PROVIDER_OUTPUT",
  "total_duration_us": 280520000,
  "whole_manifest_sha256": "27ea1593a46c9c17de0a568df6537dbe55df56c4def59559d758cb9f07ecbe65",
  "whole_original_inventory_sha256": "9382e335345135438ac1d8be4103f7631d5182b091bcfcd7bb3ce413006e7a4b",
  "wav_format": {
    "sample_rate_hz": 16000,
    "channels": 1,
    "pcm_bits": 16
  },
  "source_preservation": "ANDROID_SOURCE_AND_EVALUATION_WAV_BYTES_IDENTICAL_PRIVATE_ORIGINALS_RETAINED",
  "reference_authority": "EXPLICIT_HUMAN_CONFIRMATIONS_SAVED_IN_ANDROID_APP",
  "assistant_inferred_reference_confirmation": false,
  "technical_composites": [
    {
      "duration_us": 300000000,
      "independent_quality_sample": false,
      "language": "ru"
    },
    {
      "duration_us": 599999000,
      "independent_quality_sample": false,
      "language": "ru"
    },
    {
      "duration_us": 300000000,
      "independent_quality_sample": false,
      "language": "en"
    },
    {
      "duration_us": 599999000,
      "independent_quality_sample": false,
      "language": "en"
    }
  ],
  "technical_composite_scope": "DERIVED_REPEATED_AUDIO_NOT_ADDITIONAL_INDEPENDENT_QUALITY_CASES",
  "checks": {
    "current_seed_binding": "PASS",
    "phone_export_byte_identity": "PASS",
    "unchanged_phone_state_during_collection": "PASS",
    "private_import_idempotence": "PASS",
    "eight_wav_duration_and_format_checks": "PASS",
    "reference_and_source_hash_audit": "PASS",
    "all_eight_selection_without_noise_or_timing": "PASS",
    "full_manifest_and_composite_revalidation": "PASS"
  },
  "privacy": "AUDIO_REFERENCES_DEVICE_IDENTITY_AND_PER_RECORD_HASHES_REMAIN_PRIVATE_OUTSIDE_GIT",
  "english_spontaneous": "NOT_EVALUATED",
  "noise_robustness": "NOT_EVALUATED",
  "timestamp_accuracy": "NOT_EVALUATED",
  "pronunciation_quality": "NOT_EVALUATED",
  "quality_scope": "ONE_SPEAKER_RU_READ_AND_SPONTANEOUS_EN_BASIC_READ_ONLY",
  "broad_admission": "NOT_ESTABLISHED",
  "provider_admission": "NOT_ESTABLISHED",
  "aws_configuration": "NOT_VERIFIED",
  "live_aws_preflight": "NOT_RUN",
  "phase_a_successor": "NOT_CREATED",
  "phase_b": "NOT_RUN",
  "aws_evaluation": "NOT_RUN",
  "budget_usd": {
    "total": 10,
    "asr": 2,
    "ancillary_tax": 8
  },
  "stage_6_3": "NOT_EXECUTED",
  "historical_phase_a_and_amendments": "UNCHANGED",
  "next_preconditions": [
    "Verified AWS configuration and all live preflight checks",
    "Complete prospective Phase A successor bound to this manifest/config/client",
    "Publish and refetch that exact Phase A before the first AWS evaluation"
  ]
}
```
