# Задание: независимый Engineering/Security review POC-RECOVERY-001

Проведи независимый recovery-scoped Engineering/Security review только опубликованного
governance/readiness package `POC-RECOVERY-001`. Не реализуй harness, не добавляй зависимости, не
запускай device tests, kill campaign или benchmarks и не меняй production `:app`.

## Обязательные входы

Прочитай полностью и в порядке repository precedence:

1. `AGENTS.md`;
2. релевантные recovery/storage/security/device разделы
   `docs/DORA_MVP1_TECHNICAL_PLAN.md`;
3. релевантные recovery/error-state разделы `docs/DORA_MVP1_DESIGN_SPEC.md`;
4. `DEC-044` в `docs/DORA_MVP1_PRODUCT_DECISIONS.md` и
   `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
5. `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md`;
6. `docs/stage0/poc-recovery-gate-set-stage0-v0.1.json` и
   `docs/stage0/poc-recovery-protocol-stage0-v0.1.json`;
7. все файлы `docs/evidence/poc-recovery-001/`, кроме этого задания.

Review должен быть независим от автора package. Укажи имя/роль reviewer, дату, проверенный commit и
подтверди, что этот review не является Production Security approval.

## Обязательные вопросы

1. Докажи или опровергни, что `tink-android:1.23.0` позволяет через **только public
   `StreamingAead` API** определить durable commit point и после `SIGKILL` вернуть аутентифицированный
   contiguous prefix без зависимости от uncommitted tail. Отдельно разберись с EOF против
   authentication/truncation failure. Internal API/reflection запрещены.
2. Утверди, отклони или предложи точную замену для Proposed
   `AES128_GCM_HKDF_4KB`. Если утверждаешь, зафиксируй non-deprecated public construction path,
   exact parameters, key/nonce limits и rationale для 4 KiB. Не начинай реализацию.
3. Для five-second microfiles выбери или отклони exact public Tink `Aead` template и зафиксируй
   canonical manifest encoding, authentication, generation/AAD binding, anti-rollback, file naming,
   ordering, atomic publish и parent-directory durability. 15/30 секунд не могут PASS текущий gate.
4. Проверь Android Keystore wrapping и разделение ключей: уникальный run key, streaming-derived
   segment keys или уникальные microfile segment keys, отсутствие replacement key под старым
   reference, обязательная классификация `KEY_UNAVAILABLE` отдельно от `CORRUPT`, отсутствие ключей
   в Git/логах/evidence.
5. Проверь нормативные определения commit point, committed prefix, recovered authenticated prefix,
   committed loss и tail loss. Gate нельзя ослабить: committed loss `0`, tail `<=5.000 s` на каждом
   valid hard kill.
6. Проверь data → authenticated publication → SQLite ordering, `fsync`/rename semantics и
   PoC-local `android.database.sqlite` durability profile. Выбери exact journal mode или потребуй
   изменение. Room, SQLCipher, WorkManager и production schema запрещены.
7. Проверь все 12 kill strata, 120 base attempts/candidate, minimum 100 valid, Phase A allocation
   emulator+D2, full D1/D2/D5 allocation, valid-hard-kill definition, invalidation и максимум один
   явный replacement (`-R1`) на eligible invalid, не более 20/candidate. Candidate failure нельзя
   invalidировать или заменить.
8. Проверь все corruption/truncation/key-loss/split-brain/quarantine/idempotency/cleanup cases и
   ожидаемое fail-closed поведение, включая kill во время recovery/quarantine/cleanup.
9. Проверь exact JAR/POM/transitive hashes, shaded protobuf 4.33.6, LICENSE/NOTICE/patent notes,
   advisory snapshot и отсутствие native payload. Укажи, нужен ли дополнительный security source
   или artifact evidence до implementation.
10. Проверь, что Phase A без D1/D5 не может PASS, recovery SQLite provenance preflight достаточен
    только для Stage 0, а review не создаёт dependency/production admission.

## Требуемый результат review

Верни один versioned review record с:

- disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`, `CHANGES_REQUIRED` или `REJECT`;
- таблицей по каждому вопросу: evidence, conclusion, required change и blocking/non-blocking level;
- точными approved значениями для всех ранее `null`/`PENDING` crypto, manifest, journal и barrier
  полей либо явным отказом их утверждать;
- списком остаточных P0/P1, владельцем и условием закрытия;
- подтверждением, что никакие implementation/execution/measurement не выполнялись;
- явной формулировкой: «Этот review не является Production Security approval, Production Legal
  approval, dependency admission или разрешением execution».

Даже disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` не меняет
`executionAllowed=false`. После review владелец должен отдельно принять package changes; затем
нужны отдельно scoped harness implementation/non-metric verification, exact future resolved graph
и device/SQLite preflight. Только после этого Project owner может отдельной записью разрешить Phase A.
