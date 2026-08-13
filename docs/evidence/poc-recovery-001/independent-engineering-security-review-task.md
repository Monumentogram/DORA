# Задание: accountable exact-package read-only Engineering/Security review POC-RECOVERY-001 v0.6

Проведи recovery-scoped Engineering/Security review exact v0.6 package at PR #12 HEAD
`b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd`, whose tree was squash-merged to `main` as
`f14c6f37d7acb37590be875f176653c100f0ae20`. Не реализуй
harness, не добавляй зависимости, не запускай device tests, kill campaign или benchmarks и не
меняй production `:app`. Reviewer должен быть distinct accountable reviewer; текущая Codex
remediation не заявляет формальную независимость.

Предыдущий non-formal evidence review: GPT-5.6 Sol, OpenAI, роль `AI documentary advisory
reviewer`, дата 2026-08-12, reviewed commit
`eca48ba62acd79007884710395cc40ea21a02611`, `formalReviewer=false`, disposition
`CHANGES_REQUIRED`. Active v0.6 remediation закрывает только documentary finding
`REC-REV-20260812-01`; `REC-REV-20260812-02` и `REC-RDY-02` остаются `OPEN_BLOCKING`.

Последующий non-formal evidence re-review: OpenAI Codex (GPT-5), OpenAI, роль
`AI documentary advisory reviewer`, дата 2026-08-13, reviewed commit
`b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd`, `formalReviewer=false`, disposition
`NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED`, actionable findings отсутствуют. Review не
публиковался как formal GitHub review и не закрывает `REC-REV-20260812-02` или `REC-RDY-02`.

## Обязательные входы

Прочитай полностью и в порядке repository precedence:

1. `AGENTS.md`;
2. релевантные recovery/storage/security/device разделы
   `docs/DORA_MVP1_TECHNICAL_PLAN.md`;
3. релевантные recovery/error-state разделы `docs/DORA_MVP1_DESIGN_SPEC.md`;
4. `DEC-044` в `docs/DORA_MVP1_PRODUCT_DECISIONS.md` и
   `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
5. `docs/evidence/poc-recovery-001/governance-remediation-v0.6.md` и все пять findings ledgers;
6. `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md`;
7. `docs/stage0/poc-recovery-gate-set-stage0-v0.6.json` и
   `docs/stage0/poc-recovery-protocol-stage0-v0.6.json`;
8. v0.1/v0.2/v0.3/v0.4/v0.5 Gate Set Markdown/Gate JSON/protocol JSON только как 15 SHA-256-pinned superseded audit artifacts;
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
5. Проверь Android Keystore path: exact lowercase UUID alias; при новом run только
   `generateNewAeadKey(alias)`, а получение `Aead` при создании/recovery только через
   `new AndroidKeystoreKmsClient.Builder().setKeyUri(alias).build().getAead(alias)`; recovery не
   генерирует и не заменяет key. Проверь key-confirmation ciphertext и exact ordered canonical
   eight-class taxonomy. Для new-run любое занятое alias/key-reference/temp/final namespace
   обязано дать `KEY_REF_COLLISION`. Для recovery проверь строгий порядок: no row + bootstrap remainder
   → `INCOMPLETE_KEY_BOOTSTRAP`; row + missing final → `KEY_CONFIRMATION_MISSING`; path/type/recorded
   ciphertext length/SHA mismatch → `CORRUPT_KEY_CONFIRMATION` без decrypt; alias absent/invalidated/
   unusable → `KEY_UNAVAILABLE`; exact-AAD decrypt auth failure →
   `KEY_UNAVAILABLE_KEY_MISMATCH` без replacement; post-decrypt plaintext parser/magic/schema/
   no-trailing/protocol/candidate/run/alias-digest mismatch → `CORRUPT_KEY_CONFIRMATION`; missing later
   key reference/envelope → `KEY_UNAVAILABLE`; envelope length/hash/encoding/parser failure →
   `CORRUPT_KEY_ENVELOPE`; structurally valid envelope auth failure →
   `KEY_ENVELOPE_AUTH_FAILURE`. Для единственного effective `KEY-04` отдельно докажи все восемь
   предусловий v0.6: durable run row; final confirmation; совпавшие path/type/recorded ciphertext
   length/SHA-256; существующий и доступный через Builder/`getAead` alias; exact active-protocol AAD;
   controller replacement underlying alias key другим valid AEAD key до recovery при сохранении
   прежних ciphertext bytes и recorded length/SHA-256; отсутствие создания/замены key recovery;
   `Aead.decrypt(existingConfirmationCiphertext, exactAad)` authentication/AAD failure. Единственный
   oracle — `KEY_UNAVAILABLE_KEY_MISMATCH`. Successful decrypt, post-decrypt malformed/wrong
   plaintext, ciphertext identity corruption и missing/invalidated/unusable alias для `KEY-04`
   запрещены. Побайтно проверь separate
   `DORAKC01` plaintext / `DORAKA01` AAD schemas и exact 13-step exclusive-create/write/file-fsync/
   rename/directory-fsync/SQLite bootstrap до любой publication.
6. Проверь semantic commit: successful SQLite `endTransaction()` return после durable data и
   publication; controller event только evidence. Докажи `0 <= C <= R <= A`, committed loss 0,
   oracle equality `[0,R)`, reconstruction `C` из SQLite+authenticated publication и threshold
   `returnedBytesAuthenticatedPercentMinimum=100.0`.
7. Проверь exact 9-step streaming setup, 13-step checkpoint и 21-step microfile publication
   sequences, включая оба microfile key envelopes, SQLite→`endTransaction()`→controller event.
   Проверь mapping `final + ".tmp"`, exclusive create/collision/no-overwrite, пять exact states,
   запрет name-only temp promotion и containment/lstat/regular/no-symlink для всех 8 final + 8 temp
   patterns, включая ninth-family `key-confirmation/run.kc` + temp, exact active/quarantine roots и
   confirmation-specific reconciliation/quarantine transaction.
8. Проверь SQLite WAL/FULL, `wal_autocheckpoint=0`, `foreign_keys=ON`, single writer,
   `beginTransactionNonExclusive()`, exact filename/length/SHA rows, deterministic SHA-256
   `processingIntentId` и `UNIQUE`. Проверь fresh emulator/D2 preflight contract, включая
   `sqlite_source_id()` и canonical compile-options digest.
9. Проверь candidate-specific public K01–K12 mappings: streaming downstream-OutputStream K02,
   `MICROFILE_AFTER_AEAD_RETURN_BEFORE_TEMP_WRITE`, exact K04–K11 publication boundaries, K12
   immutable seeds/canonical result, plus unchanged 120/100/8 counts, allocations and replacement
   rules.
10. Проверь ровно 46 unique active effective строк mandatory fault matrix, `KEY-04` ровно один раз:
    33 исторически inherited ID с v0.6 effective override для `KEY-04`, плюс KCB-01..06,
    KCF-01..06 и `KCF-07`. Исторический v0.3 `KEY-04` остаётся только immutable superseded audit
    row и не считается второй active row. Для `KCF-07` malformed/wrong plaintext должен быть
    encrypted правильным alias и AAD с
    обновлёнными outer length/SHA; decrypt проходит, а post-decrypt exact plaintext validation даёт
    только `CORRUPT_KEY_CONFIRMATION`. Проверь, что KCF-07 не смешан с KEY-04, а pre-decrypt
    path/type/length/hash mismatch даёт `CORRUPT_KEY_CONFIRMATION`, unusable alias —
    `KEY_UNAVAILABLE`, structurally valid later envelope auth failure —
    `KEY_ENVELOPE_AUTH_FAILURE`. Проверь symlink/path traversal, snapshot rollback, alias
    replacement, envelope swap и SQLite-commit/controller-event gap.
11. Проверь exact JAR/POM/transitive hashes, publisher checksums, detached PGP verification,
   fingerprint и upstream trust source по каждой координате, source tag/commit correspondence,
   per-coordinate LICENSE/copyright/NOTICE, shaded protobuf 4.33.6, advisory snapshot и отсутствие
   native payload. Не считай keyserver UID самостоятельным upstream binding: проверь full-fingerprint
   cryptographic result и publisher-bound либо exact multisource source correspondence. Отдельно
   вынеси disposition по конфликту signed Maven POM Apache-2.0 и exact release-source BSD-3-Clause
   для `com.google.code.findbugs:jsr305:3.0.2`; не подменяй его Product/IP approval. Проверь exact
   immutable JetBrains LICENSE/NOTICE locators/digests/preservation rule и honest recovery-only
   `REC-JSR305-EXCLUDE-001` boundary: unrelated existing tooling/lint/UTP/test paths не считать ни
   отсутствующими, ни recovery admission.
12. Независимо проверь два профиля. Phase A: 46 строк × (3 pinned emulator + 1 D2) = 184,
    допустимы только `FAIL`/`INCONCLUSIVE`. Full physical: 46 × (D1 + D2 + D5) = 138. D2 из Phase A
    reuse допустим только при exact match commit, protocol/Gate Set, fixture digest, injection,
    device identity/profile, fresh preflight и validity criteria; иначе D2 повторяется. При reuse
    остаются 92 D1/D5 injections. D1/D5 deferred, без полного профиля PASS запрещён. 120 hard-kill
    attempts/candidate — отдельный denominator. Проверь также exact ordered equality 11 canonical
    blocker IDs Gate Set и readiness, отсутствие альтернативных IDs и exact status/priority/owner/
    condition. Recovery SQLite provenance достаточен только для Stage 0, а review не создаёт
    dependency/production admission.

## Требуемый результат review

Верни один versioned review record с:

- disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`, `CHANGES_REQUIRED` или `REJECT`;
- таблицей по каждому вопросу: evidence, conclusion, required change и blocking/non-blocking level;
- verdict по каждому owner-selected v0.6 design field: `VERIFIED`, `CHANGES_REQUIRED` или
  `REJECTED`; не заполняй implementation/runtime facts без реального отдельного evidence;
- списком остаточных P0/P1, владельцем и условием закрытия;
- подтверждением, что никакие implementation/execution/measurement не выполнялись;
- явной формулировкой: «Этот review не является Production Security approval, Production Legal
  approval, dependency admission или разрешением execution».

Даже disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` не меняет
`implementationAllowed=false` и `executionAllowed=false`. Prospective policy и exact governance
packet evidence закрыты; Product/IP disposition будущего actual recovery graph остаётся отдельным
решением. Затем нужны
separately scoped harness implementation/non-metric verification, exact future resolved graph и
fresh emulator/D2 preflight. Только после этого Project owner может отдельной записью разрешить
Phase A. Не считать merge PR #12 формальным review или authorization; не переводить текущий
post-merge reconciliation PR из Draft и не merge.
