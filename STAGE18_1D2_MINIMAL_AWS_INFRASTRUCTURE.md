# DORA — Stage18.1D.2 Minimal AWS Infrastructure Preparation

Дата: 2026-10-27. Статус подготовки: **PASS / AWS_INFRASTRUCTURE_PREPARED**.
Это готовность пакета к отдельно разрешённому развёртыванию. AWS deployment,
live Transcribe, RU/EN smoke и POCO: **NOT_RUN**. Новых AWS ресурсов/roles/keys: **0**.

База: `54cd19d96b33a1c6783fe5b04da0aa27fa1f3055`,
[Stage18.1C Draft PR #116](https://github.com/Monumentogram/DORA/pull/116), без merge.
Ветка подготовки: `codex/stage18-1d2-aws-infrastructure`. Принятая ветка и main
не изменены; текущая linked worktree переключена на отдельную ветку от заданной базы.

## 1. Основания и фактическая AWS-конфигурация

Прочитан локальный `STAGE18_1D_AWS_PREFLIGHT_RESULT.md` из рабочего каталога владельца;
он не публикуется вместе с приватными идентификаторами. Прочитаны релевантные разделы
Technical Plan §§13/23/24/28, [ADR-0011](docs/adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md),
[ADR-0012](docs/adr/ADR-0012-alpha-retention-periods-and-provider-copy-limits.md),
[ADR-0025](docs/adr/ADR-0025-stage18-manual-cloud-asr.md), backend config, admission,
adapter, store, cleanup, CLI и существующие deployment examples. ADR-0025 явно
ограничивает Alpha одним host/SQLite/runtime identity и сохраняет разные CMK.

Подтверждённые сведения предыдущего preflight: effective Transcribe optOut,
eu-central-1, нет DORA media buckets/CMKs и подходящей подтверждённой runtime role;
есть отдельный CloudFormation template bucket и AWS-managed key. Их не переиспользуем.
В этой задаче через существующий AWS Console/CloudShell выполнены только чтения:
пагинированный EC2 DescribeInstances — **0 non-terminated instances**,
Lightsail GetInstances — **0**, DescribeVpcs — **1 default VPC** в Frankfurt.
Это не inventory всех сервисов/регионов и не доказательство наличия готового subnet.
Backend README также указывает, что прежние research resources были retired.
Подходящий существующий host не установлен; root Console не является runtime identity.

Price List GetProducts и публичные AWS price catalogs использованы для цен. Доступ
к этим read-only операциям не блокировался. Локальных AWS credentials по-прежнему
нет; они не извлекались из браузера/CloudShell и не переносились в файлы.

## 2. Выбранная архитектура и сравнение

**Рекомендация:** два приватных S3 buckets, два distinct regional customer CMK,
одна EC2 workload role + instance profile, один EC2 t3.micro Linux, 12-GiB root и
отдельный 8-GiB data EBS gp3, один Elastic IPv4, один security group. HTTPS nginx
перед одним FastAPI process; отдельный systemd watchdog использует тот же SQLite.
Без NAT, load balancer, RDS, Kubernetes, контейнерной оркестрации или второго host.
Одна агрегированная CloudWatch metric/alarm + SNS email, один AWS Budget.

### S3

| Вариант | Совместимость и последствия | Решение |
|---|---|---|
| A: 1 bucket, input/output prefixes | S3 поддерживает prefix IAM/lifecycle, cleanup по точным ключам концептуально возможен. Но LiveConfig запрещает равные buckets; admission/namespace и согласованная схема разделены. Нужны изменение контракта, тесты и новый ledger; нельзя просто заменить имя в существующем SQLite. Rollback миграции усложнится | Не выбран: у S3 buckets нет фиксированной платы, финансовой пользы почти нет |
| B: 2 buckets | Совместим с adapter/config; отдельные политики, encryption defaults и независимая проверка/удаление. Оба временные с одинаковым потолком срока. Rollback сохраняет исходные bindings | **Выбран** |

### KMS

| Вариант | Контроль и изменения | Фиксированная плата свежих ключей | Решение |
|---|---|---|---|
| SSE-S3/AES256 | Приватность S3 IAM сохраняется, но нет отдельного customer-key custody/rotation. Не проходит preflight и проверку output; нужен пересмотр ADR, config и adapter | $0 CMK/month | Не допускается текущим контрактом |
| 1 CMK для input/output | Остаётся customer control, но единая граница доступа/управления ключом. LiveConfig требует разные ARN; нужны пересмотр ADR и тесты | $1/month | Экономия $1 не оправдывает изменение согласованного разделения |
| 2 CMKs | Отдельные input/output key ARN, default encryption и output binding, rotation, совместимый cleanup | **$2/month** | **Выбран** |

Один Alpha workload вправе использовать оба ключа — это явно разрешённое ADR-0025
упрощение ролей, не полная изоляция процессов от компрометации host. Администрирование
KMS остаётся у оператора. Backend custody key — отдельные 32 байта AES-GCM на data EBS,
не третий CMK. EBS использует AWS-managed `alias/aws/ebs`, без дополнительной платы
за customer key. Shared CMK нельзя отключать как способ удаления одной записи.

### Hosting: три ограниченных варианта

| Вариант | Оценка для текущего кода |
|---|---|
| Существующая DORA VM в Frankfurt | Первый предпочтительный путь при наличии подходящей identity, persistent disk и supervision. В проверенных EC2/Lightsail её нет; нельзя считать Windows desktop подходящим EU host |
| **Новый EC2 t3.micro** | **Выбран**: 1 GiB RAM, amd64/Python3.12, привычный systemd/nginx, IMDSv2 temporary role credentials, EBS survives reboot/host replacement. Standard CPU-credit mode ограничивает CPU вместо дополнительных unlimited charges; нагрузка Alpha требует последующей проверки памяти/задержки |
| Lightsail Linux с persistent disk | Возможен как VM, но для безопасных временных AWS credentials потребуется отдельная схема федерации (например, Roles Anywhere) и её эксплуатация. Не выбираем static keys ради небольшой экономии; преимущества пакета не компенсируют новый identity path |

[EC2 workload roles](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html),
[CPU standard mode](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-standard-mode.html).

## 3. IAM и подготовленные ресурсы

[stack.json](backend/deployment/alpha/stack.json) содержит 18 CloudFormation resources:
2 Bucket + 2 BucketPolicy, 2 Key, RuntimeRole + InstanceProfile, Host + SecurityGroup,
DataVolume + VolumeAttachment, EIP + EIPAssociation, SNS Topic + TopicPolicy,
CloudWatch Alarm, Budget. Root EBS и ENI — создаваемые EC2 вложенные ресурсы.
VPC/subnet/Internet Gateway/DNS hostname/SSH key pair предполагаются существующими
и проверяются до deployment; стек их не создаёт и не меняет.

- `StartTranscriptionJob` исправлен: отдельный `Resource: "*"`, требуемый API,
  с exact region/output bucket/output CMK и `output/dora-*.json`.
- Get/Delete — только `transcription-job/dora-*`; List — `*` + Frankfurt.
  Start API не имеет resource ARN/job-name condition: имя контролирует backend,
  output-prefix constraint не является полным IAM ограничением имени задания.
- S3 Put/Get/Delete — только два object-prefix patterns, account condition.
  Не даём runtime ListAllMyBuckets, управления IAM/S3/billing, изменения key policy.
- KMS Encrypt/Decrypt/GenerateDataKey — только два CMK, CallerAccount и ViaService
  `s3.eu-central-1.amazonaws.com` / `transcribe.eu-central-1.amazonaws.com`.
  DescribeKey/GetKeyRotationStatus — только эти ключи.
- Admission S3 Get controls — только эти buckets; Organizations DescribeEffectivePolicy
  требует `Resource: "*"`; глобальный административный endpoint не получает контент.
- Дополнительное операционное разрешение — PutMetricData только namespace DORA/Alpha,
  регион Frankfurt; служебная metric содержит одну boolean величину, без PII/content.
- Trust — только `ec2.amazonaws.com` / AssumeRole. Android не получает AWS keys.
- **Runtime `iam:PassRole` не нужен**: текущий adapter не передаёт JobExecutionSettings
  или DataAccessRoleArn. Оператору при deployment нужен отдельный ограниченный
  PassRole для привязки конкретной workload role к EC2; не к Transcribe.

[Transcribe authorization reference](https://docs.aws.amazon.com/service-authorization/latest/reference/list_transcribe.html),
[StartTranscriptionJob API](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_StartTranscriptionJob.html).
KMS key policy использует стандартную account-IAM delegation/recovery форму;
её root principal не означает запуск backend от root. Реальные operator policies,
SCP/boundaries и отсутствие лишних runtime grants проверяются после создания.
[KMS default key policy](https://docs.aws.amazon.com/kms/latest/developerguide/key-policy-default.html).

## 4. Стоимость: USD, без налогов, скидок и предполагаемого free tier

Проверено 2026-10-27. **Локальная подготовка — $0 за provisioning/Transcribe**:
шаблоны не применены, jobs не запускались. Это не утверждение о нулевой invoice
аккаунта: read API и уже существующие ресурсы учитываются AWS отдельно.
У новой схемы нет upfront reservation/license fee. После создания начисления
идут до остановки/retirement; Retain не останавливает billing.

### Фиксированные и регулярные расходы при 730 часах в месяце

| Статья | Тариф/формула | USD/month |
|---|---|---:|
| EC2 t3.micro Linux On-Demand | 730 × $0.012/h | 8.760 |
| EBS gp3, 12+8 GiB | 20 × $0.0952/GB-month; baseline IOPS/throughput | 1.904 |
| Один public IPv4/EIP | 730 × $0.005/h | 3.650 |
| Два свежих CMK | 2 × $1/key-month | 2.000 |
| CloudWatch: одна metric + один standard alarm | $0.30 + $0.10 | 0.400 |
| Health PutMetricData раз в минуту | 43,800 × $0.01/1000 | 0.438 |
| Budget notifications, IAM, CFN AWS resource management | Без actions/reports, без сторонних registry resources | 0 |
| Локальные logs | Volatile journal ≤16 MiB, 1-day bound; nginx rotation, без payload | Включено в host/disk |
| **Плановая регулярная сумма** | До трафика/ASR/S3/KMS requests/SNS | **17.152 ≈ $17.16** |

Собственно фиксированные позиции без health API ≈$16.714; $0.438 — прогноз
регулярных API calls. Новые DNS/domain/платный сертификат не включены: существующий
hostname и бесплатный trusted certificate дают $0 новой AWS платы. Если их нет,
отдельно получить цену регистрации/renewal до согласования deployment; не выдавать
неизвестную стоимость внешнего домена за ноль. Route53/NAT/ALB не обязательны и не создаются.

Первые две rotation каждого CMK добавляют по $1/key-month за сохраняемый key material;
для двух ключей fixed KMS может вырасти с $2 до $4, затем $6/month. Rotation включена,
её не отключаем ради экономии. Время месяца/неполный месяц меняет prorating.
[KMS pricing](https://aws.amazon.com/kms/pricing/), [VPC IPv4](https://aws.amazon.com/vpc/pricing/),
[EC2 pricing](https://aws.amazon.com/ec2/pricing/on-demand/),
[EBS pricing](https://aws.amazon.com/ebs/pricing/),
[CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/),
[Budget pricing](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/).

Региональные EC2/EBS ставки получены через Price List GetProducts, effective
2026-10-01: Linux/shared/t3.micro/no-preinstalled-software/Used, Frankfurt;
gp3 storage Frankfurt. Дополнительные IOPS/throughput не заказаны.
CloudWatch catalog version 20261007115540 подтверждает $0.30 metric, $0.10 alarm,
$0.01/1000 API; бесплатные allowances не вычтены.
[CloudWatch Price List](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonCloudWatch/current/eu-central-1/index.json).

### Переменные расходы

| Услуга | Платный тариф |
|---|---|
| Transcribe standard Batch Frankfurt | $0.0001/sec = **$0.006/min**, посекундно без minimum duration |
| S3 Standard | $0.0245/GB-month; PUT/COPY/POST/LIST $0.0054/1000; GET/other $0.0043/10,000; DELETE бесплатно |
| KMS usage | $0.03/10,000 requests |
| SNS standard | $0.50/million requests; email $2/100,000 notifications, без зачёта free tier |
| Internet egress Frankfurt | $0.09/GB в первой платной ступени; входящий трафик и S3↔EC2 внутри региона без отдельной transfer платы |

[Transcribe pricing](https://aws.amazon.com/transcribe/pricing/) и
[catalog](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/transcribe/current/eu-central-1/index.json)
version 20260911124646, SKU 9BDG889E5Y3GVM5S, effective 2026-06-01.
[S3 catalog](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonS3/current/eu-central-1/index.json)
version 20261015173540; [KMS catalog](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/awskms/current/eu-central-1/index.json)
version 20261026201814; [SNS catalog](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonSNS/current/eu-central-1/index.json)
version 20260915151944, SMTP delivery. 100 email alerts + 100 requests ≈$0.00205.
Internet egress проверен Price List GetProducts (`AWSDataTransfer`, Frankfurt →
External, AWS Outbound): $0.09/GB для 0–10,240 GB платного usage.
[Data transfer pricing](https://aws.amazon.com/ec2/pricing/on-demand/).

**100 минут:** Transcribe **$0.60**. Для явного сценария 100 jobs по 60s, без retries:
200 PUT, 10,000 GET/other, 1,000 LIST и 1,000 KMS calls дают $0.01378; хранение
~0.28 GiB input+ограниченных outputs на 2 суток ≈$0.00046. Это сценарный запас,
не измеренный usage. Добавить фактический интернет-egress ответов и control requests;
при 100 MiB ответов итог переменных расходов около **$0.63**. Полный месяц с таким
объёмом — примерно **$17.8**, без нового домена/налогов и внештатного трафика.
Повторные downloads, большие ответы API, атаки/ошибки, долгий outage меняют оценку.

**Первый RU/EN smoke:** два jobs ≤15s каждый — Transcribe **≤$0.003**.
Сценарий 4 PUT, 20 LIST, 500 GET, 100 KMS calls, 3 MiB S3 на 2 суток даёт
**≈$0.00365** до внешнего egress; округлённый variable запас **$0.01**.
Сюда не включён новый месячный host/CMK baseline; он начисляется отдельно независимо
от числа распознаваний. Это оценка будущего теста, не его разрешение и не bill cap.

### Контроль расходов

Budget — **$20/month на весь аккаунт**, actual 50/80/100% и forecast 100% на private
email: такой охват не пропустит untagged Transcribe/KMS расходы. Существующие сторонние
ресурсы также попадут в budget. Нет budget actions, paid reports, автоснятия cleanup
прав или автоматики, убивающей host с незавершённой очисткой.
AWS Budgets опаздывает и не является жёсткой остановкой.
[Обновление budgets](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html).

Runtime сохраняет one-active-action, 10 actions/day, максимум 2 attempts, daily
reserve $0.25 и conservative $0.03/min + $0.01/attempt. Это не опубликованный тариф
и не общий AWS cap. Для отдельного Stage18.1E подготовить $0.035/day reserve,
ровно два action ID/два ≤15s fixtures, запрет retry на ingress, одно UTC окно без
midnight rollover; эта задача не включает такую live активацию. После cost alert
оператор закрывает новое admission, сохраняя cleanup/keys до подтверждённого завершения.

## 5. Временные данные, мониторинг и rollback

Оба buckets: BPA×4, BucketOwnerEnforced, TLS-only, exact SSE-KMS, rotation; без
versioning, object lock, replication, archival tiers, payload logs и backup.
Загрузка с другим encryption/key отклоняется bucket policy. Удаление input/output
и terminal job выполняет текущий durable watchdog; результат сначала фиксируется
в зашифрованном SQLite. Первый accepted byte фиксирует 24h deadline, retry его не
продлевает. Lifecycle 1 day + abort incomplete 1 day — только backstop: округление
и асинхронное исполнение не доказывают удаление за 24h.
[Lifecycle semantics](https://docs.aws.amazon.com/AmazonS3/latest/userguide/intro-lifecycle-rules.html).

Отдельный health timer читает metadata SQLite без decrypt, публикует boolean.
Уведомление при пропаже метрики/host через два периода, неактивном watchdog,
отсутствии mount, свободном месте ≤512MiB, остающихся chunks или pending cleanup
за час до deadline. Ошибки чтения закрываются как unhealthy. Требуются подтверждённая
доставка email и доступный оператор, способный восстановить cleanup в этом окне.
Один host остаётся общей точкой отказа; alarms не дают hard SLA и не отменяют
известные provider-copy ограничения. Hung process может считаться active до того,
как сработает deadline guard. Это раскрыто, не названо доказанным uptime.

Retain/UpdateReplacePolicy сохраняют keys, buckets **с политиками** и data EBS.
При rollback сначала закрыть admission, оставить watchdog и права, дождаться
terminal/reconciliation/delete receipts, проверить отсутствие точных объектов/jobs.
Не выключать CMK/host и не удалять ledger ради остановки счёта. Для recovery —
один replacement host в том же AZ, тот же диск/key/config; не форматировать его,
не восстанавливать старые snapshots. Дальнейшая retirement retained resources
отдельно согласуется с metadata/tombstone retention; Retain продолжает billing.

## 6. Подготовленные файлы и минимальные изменения

- `backend/deployment/alpha/stack.json`, `parameters.example.json`, `trust-policy.json`:
  воспроизводимая IaC; шаблоны не содержат реального account/resource/email.
- `backend/deployment/runtime-policy.template.json`: исправленный Start и KMS account condition.
- `alpha/render_config.py`: offline Outputs → валидный **disabled** config, exclusive
  private file вне repo; custody key вместе с ledger на retained volume.
- `alpha/health.py`, `dora-health.service`, `dora-health.timer`, `persistent-data.conf`,
  `journald.conf`: минимальная эксплуатация; отсутствие data mount блокирует API/watchdog.
- `backend/deployment/nginx.conf.example`: явное отключение request/response disk
  buffering и HTTP/1.1 upstream; 100-KiB buffer соответствует лимиту входного body.
- `backend/dora_cloud/preflight.py`: **единственная production Python поправка** —
  принимает эквивалентные whole-bucket lifecycle representations (empty Filter,
  Filter Prefix empty, legacy Prefix empty). CFN использует Prefix; отсутствие,
  conflicting/scoped/tag/size filters остаются запрещены. Security guarantee не ослаблена.
- Три новых test modules, pinned offline cfn-lint tools и шаг backend CI.
- [Пошаговый runbook](backend/deployment/alpha/README.md) содержит параметры, реальные
  команды change-set/deploy, host setup, verification, recovery и rollback.

## 7. Проверки и пределы готовности

- cfn-lint **1.58.0**, регион eu-central-1: PASS, без warnings/errors, включая
  schema/intrinsics/dependency validation. CloudFormation API validation не запускался.
- Backend unittest suite: **52 tests PASS**, без credentials/AWS calls. Новые tests
  проверяют lifecycle negative cases, разделение custody/IAM/host controls, disabled
  renderer/отказ overwrite, missing ledger и pre-deadline alert semantics.
- RED→GREEN: исходный preflight отклонял допустимые формы; новые tests подтвердили
  исправление. Проверка health на Windows выявила незакрытый SQLite connection;
  явное закрытие исправлено, полный suite PASS.
- Независимое read-only ревью всего пакета: без Critical/Important/Minor findings;
  reviewer отдельно повторил 51-test suite до добавления последнего metric-boundary
  test. Авторский финальный suite — 52 PASS. Проверка цен и AWS inventory выполнена
  автором по указанным первоисточникам; reviewer их независимо не сертифицировал.
- AWS policy simulation, service output authorization, Linux boot, systemd/nginx,
  SNS delivery, TLS и live cleanup **NOT_RUN**. Статический PASS не заменяет их.
- Android runtime не менялся; локальная emulator/POCO проверка не запускалась.
  CI включает прежние software gates и новое IaC validation; результат точного SHA
  сообщается в PR/handoff отдельно, без присвоения старому CI нового результата.
- Нет новых P0/P1 противоречий в выбранной конфигурации для **подготовки**. Deployment
  admission остаётся закрытым до заполнения operator parameters, non-root deployment
  identity, текущих read checks и отдельного разрешения. DNS/AMI/subnet/CIDR/email —
  параметры конкретного окружения, а не неявное разрешение создавать недостающие вещи.

## 8. Точный следующий порядок и согласование

1. Владелец отдельно утверждает перечень ресурсов ниже, ориентир **$17.16/month + usage**,
   budget alerts $20, private hostname/CIDR/email и non-root administrator.
2. После этого: заполнить/проверить private parameters; создать и проверить change set,
   применить только согласованную инфраструктуру, не начиная ASR. Команды — в runbook.
3. Настроить persistent mount, закрытый backend, TLS, watchdog, health timer, alarms;
   проверить crash/mount/alarm recovery до любого аудио. Выполнить preflight под role,
   проверить фактические IAM/KMS/S3/optOut/billing/quota. Сервис config остаётся false.
4. Отдельное разрешение Stage18.1E: два коротких синтетических RU/EN jobs, согласованный
   budget и внешний smoke gate. После чистых receipts можно обсуждать POCO отдельным этапом.

Создание/изменение AWS, IAM, billing, deployment, audio/jobs, POCO и merge в этой
работе **не выполнялись**. Никакой следующий этап не начат автоматически.

| Ресурс | Количество | Стоимость | Зачем нужен |
|---|---:|---|---|
| S3 | 2 private buckets | $0 fixed + $0.0245/GB-month и requests | Раздельные временные input/output, cleanup и контроль encryption |
| KMS | 2 customer CMKs | $2/month fresh + requests/rotation | Принятые независимые input/output key boundaries |
| IAM | 1 workload role + 1 instance profile | $0 | Временные EC2 credentials и ограниченные runtime/admission permissions |
| Backend hosting | 1 t3.micro + 20 GiB gp3 + 1 IPv4 | ≈$14.314/month | Один API, независимый watchdog, persistent SQLite/key и HTTPS |
| Monitoring/cost control | 1 metric, 1 alarm, 1 SNS topic, 1 budget | ≈$0.838/month + SNS notifications | Отказ/cleanup deadline и уведомление о расходах |
