# Задание: повторный read-only Engineering/Security review POC-RECOVERY-001 v0.2

Проведи recovery-scoped Engineering/Security review только exact remediation commit
governance/readiness package `POC-RECOVERY-001` после disposition `CHANGES_REQUIRED`. Не реализуй
harness, не добавляй зависимости, не запускай device tests, kill campaign или benchmarks и не
меняй production `:app`. Reviewer должен быть distinct accountable reviewer; текущая Codex
remediation не заявляет формальную независимость.

## Обязательные входы

Прочитай полностью и в порядке repository precedence:

1. `AGENTS.md`;
2. релевантные recovery/storage/security/device разделы
   `docs/DORA_MVP1_TECHNICAL_PLAN.md`;
3. релевантные recovery/error-state разделы `docs/DORA_MVP1_DESIGN_SPEC.md`;
4. `DEC-044` в `docs/DORA_MVP1_PRODUCT_DECISIONS.md` и
   `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
5. `docs/evidence/poc-recovery-001/governance-remediation-v0.2.md`;
6. `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_2.md`;
7. `docs/stage0/poc-recovery-gate-set-stage0-v0.2.json` и
   `docs/stage0/poc-recovery-protocol-stage0-v0.2.json`;
8. v0.1 Gate Set/protocol только как superseded audit artifacts для проверки полноты remediation;
9. все остальные файлы `docs/evidence/poc-recovery-001/`, кроме этого задания.

Укажи distinct accountable reviewer/роль, дату, exact 40-character remediation commit и подтверди,
что review read-only и не является Production Security approval.

## Обязательные вопросы

1. Проверь `DURABLE_ONE_SEGMENT_LOOKAHEAD`: для `q` durable fully output non-final segments
   `ciphertextPrefixBytes=q*4096`, `R=0` при `q<2`, иначе
   `R=4056+(q-2)*4080`; sacrificial lookahead не входит в `C`; bound 8160 bytes/0.255 s. Проверь
   4056/4080 reads, accounting только successful read return, полный discard exception buffer и
   только `read()==-1` как authenticated normal EOF.
2. Проверь exact non-deprecated public Streaming construction:
   `AesGcmHkdfStreamingParameters`, input/derived key 16/16 bytes, HKDF-SHA256, 4096-byte ciphertext
   segments, fresh run keyset, one derived AES key per stream, nonce-prefix/index/last uniqueness,
   AAD once per stream, `RegistryConfiguration.get()` и запрет `StreamingAeadKeyTemplates`.
3. Проверь microfile `AES256_GCM_TINK_IV12_TAG16`: 32-byte key, 12-byte IV, 16-byte tag, TINK
   variant, fresh keyset/microfile, cadence 5 s, 160000/160033 bytes. 15/30 s не могут PASS.
4. Побайтно проверь `DORA_RECOVERY_MANIFEST_V1_BINARY_BE` и четыре exact big-endian/LP16 ASCII AAD
   schema: field order, generation/digest chain, strict entries, 721/512-KiB caps, no trailing bytes,
   cross-run/generation binding. Подтверди ограниченную rollback claim: controller ledger — внешний
   anchor; без него нет global cryptographic anti-rollback guarantee.
5. Проверь Android Keystore path: exact lowercase UUID alias; только `generateNewAeadKey()` при
   создании и `getAead()` при recovery; запрет `AndroidKeysetManager`, `getOrGenerateNewAeadKey()` и
   replacement; non-deprecated four-argument `TinkProtoKeysetFormat` с key-envelope AAD. Проверь
   пять exact KEY classifications и key mismatch/envelope swap behavior.
6. Проверь semantic commit: successful SQLite `endTransaction()` return после durable data и
   publication; controller event только evidence. Докажи `0 <= C <= R <= A`, committed loss 0,
   oracle equality `[0,R)`, reconstruction `C` из SQLite+authenticated publication и threshold
   `returnedBytesAuthenticatedPercentMinimum=100.0`.
7. Проверь exact file order, immutable no-overwrite paths, public `android.system.Os` calls,
   canonical containment/lstat/regular-file/no-symlink rules, exact active/quarantine roots и
   quarantine intent→rename→two-directory-fsync→completion protocol.
8. Проверь SQLite WAL/FULL, `wal_autocheckpoint=0`, `foreign_keys=ON`, single writer,
   `beginTransactionNonExclusive()`, exact filename/length/SHA rows, deterministic SHA-256
   `processingIntentId` и `UNIQUE`. Проверь fresh emulator/D2 preflight contract, включая
   `sqlite_source_id()` и canonical compile-options digest.
9. Проверь candidate-specific public K01–K12 mappings: streaming downstream-OutputStream K02,
   `MICROFILE_AFTER_AEAD_RETURN_BEFORE_TEMP_WRITE`, exact K05–K08 publication boundaries, K12
   immutable seeds/canonical result, plus unchanged 120/100/8 counts, allocations and replacement
   rules.
10. Проверь всю mandatory fault matrix, особенно COR-04..06, KEY-04..05, RBK-01..02, PAR-01,
    QUA-03 и EVT-01 с Phase A repetitions, symlink/path traversal, snapshot rollback,
    alias replacement, envelope swap и SQLite-commit/controller-event gap.
11. Проверь exact JAR/POM/transitive hashes, shaded protobuf 4.33.6, LICENSE/NOTICE/patent notes,
   advisory snapshot и отсутствие native payload. Укажи, нужен ли дополнительный security source
   или artifact evidence до implementation.
12. Проверь, что Phase A без D1/D5 не может PASS, recovery SQLite provenance preflight достаточен
    только для Stage 0, а review не создаёт dependency/production admission.

## Требуемый результат review

Верни один versioned review record с:

- disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`, `CHANGES_REQUIRED` или `REJECT`;
- таблицей по каждому вопросу: evidence, conclusion, required change и blocking/non-blocking level;
- verdict по каждому owner-selected v0.2 design field: `VERIFIED`, `CHANGES_REQUIRED` или
  `REJECTED`; не заполняй implementation/runtime facts без реального отдельного evidence;
- списком остаточных P0/P1, владельцем и условием закрытия;
- подтверждением, что никакие implementation/execution/measurement не выполнялись;
- явной формулировкой: «Этот review не является Production Security approval, Production Legal
  approval, dependency admission или разрешением execution».

Даже disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` не меняет
`executionAllowed=false`. Product/IP final approval остаётся отдельным решением; затем нужны
separately scoped harness implementation/non-metric verification, exact future resolved graph и
fresh emulator/D2 preflight. Только после этого Project owner может отдельной записью разрешить
Phase A. Не переводить PR из Draft и не merge.
