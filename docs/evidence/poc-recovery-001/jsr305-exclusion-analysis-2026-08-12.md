# POC-RECOVERY-001 — `jsr305:3.0.2` exclusion decision analysis

Status: **TECHNICAL EXCLUSION PATH PROVEN — OWNER/STAGE 0 PRODUCT-IP PROSPECTIVE POLICY APPROVED**\
Date: 12 August 2026\
Exact root: `com.google.crypto.tink:tink-android:1.23.0`\
Execution allowed: **no**

This is a governance and engineering compatibility analysis, not legal advice. It does not decide
which license governs `jsr305:3.0.2`, approve that artifact, admit Tink, create a Gradle graph or
authorize implementation or execution.

## Decision summary

The Project owner / Stage 0 Product-IP reviewer accepted **Option A, conditioned complete
exclusion**, only as prospective policy `REC-JSR305-EXCLUDE-001` for a later separately authorized
recovery harness:

1. exclude only the `com.google.code.findbugs:jsr305` edge on the exact Tink declaration;
2. require zero resolved `jsr305` components in every covered resolvable configuration and
   packaging/runtime/lock/verification input owned by future `:poc:recovery`;
3. add only the three exact R8 diagnostics suppressions recorded below; and
4. fail readiness until the future exact graph, debug/D8 build, release/R8 build and package scan
   prove those invariants.

A **bare** Gradle exclusion is rejected. R8 9.3.16 from the repository-pinned AGP 9.3.1 fails when
all Tink classes are preserved and no definitions exist for the three annotation types. The exact
three-line rule makes that same preservation probe pass. No `compileOnly` dependency or replacement
annotation artifact is technically required.

That result is deliberately limited to the JSR-305 condition. A second observation with all seven
remaining publisher-closure JARs supplied as R8 program inputs produced no JSR-305 missing-class
diagnostic after the narrow rule, but still failed on the independent
`javax.lang.model.element.Modifier` reference in
`com.google.errorprone:error_prone_annotations:2.41.0`. This is a separate future exact-graph /
release-build issue. It must be resolved on its own evidence and must not be hidden by broadening
the JSR-305 rule.

The underlying Apache-2.0/BSD-3-Clause evidence conflict remains unresolved. The approved
prospective treatment is to keep that artifact out of the future recovery graph, not to interpret
its terms. Project-owner / Stage 0 Product-IP acceptance of prospective policy
`REC-JSR305-EXCLUDE-001` is recorded in `OD-14`. Approval identity/date for a future actual graph
remain null because that graph does not exist and is a separate blocked disposition.

## Why the coordinate is upstream

The exact Tink POM SHA-256
`a2d27e7207e6a25764859b62924fc7b972f41884ce272cead9b946c15a1f410f` declares
`com.google.code.findbugs:jsr305:${jsr305.version}` directly under the root `<dependencies>` and
sets `jsr305.version` to `3.0.2`. It declares neither `<scope>` nor `<optional>`:

- Maven scope is therefore `compile` by default;
- `optional` is therefore `false` by default; and
- the edge is a direct, transitive root edge, not a dependency of Gson, AndroidX or another child.

Without an exclusion, Gradle consumes that Maven compile dependency on the normal compile and
runtime graph. The published closure remains recorded unchanged in `dependency-inventory.json`;
that evidence describes the publisher closure, not a repository Gradle graph.

## Exact repository and future recovery boundary

No repository-wide Tink/JSR-305 absence is claimed. At the reviewed PR #11 head,
`tink-android:1.23.0` is not wired and `:poc:recovery` does not exist, but base lockfiles of existing
modules contain Tink 1.18.0 and JSR-305 2.0.2/3.0.2 on tooling, lint, UTP, androidTest and other test
paths. PR #11 did not add or change those lockfiles. The exact inventory is
`base-lockfile-tooling-inventory-2026-08-12.json`; those unrelated existing paths are context, not
recovery admission evidence.

After separately authorized implementation, boundary `REC-JSR305-EXCLUDE-001` covers only the
future `:poc:recovery` module: every resolvable compile, runtime, unit-test, `androidTest`, benchmark
and release configuration, all packaging/runtime-artifact inputs, and that module's dependency
locks and dependency-verification metadata. It excludes buildscript/AGP/UTP/lint/tooling paths of
other existing modules and the existing app/capture/search lockfiles. A JSR-305 occurrence within
the covered recovery scope blocks readiness; unrelated base tooling occurrences alone do not.

## Exact use inside Tink

The exact Tink JAR SHA-256 is
`c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e`. Of its 1,878 class entries,
182 contain at least one JSR-305 annotation descriptor. The complete lexicographically sorted list
is `jsr305-reference-classes-2026-08-12.txt`; its canonical UTF-8/LF SHA-256 is
`325211cef459ba96a3c5721e5d754bff3464d15461e0c9f6da4a66a1f7ee2045`.

Descriptor presence by class constant pool is:

| Annotation | Class constant pools containing the descriptor | Retention |
|---|---:|---|
| `javax.annotation.Nullable` | 174 | runtime |
| `javax.annotation.concurrent.GuardedBy` | 8 | class |
| `javax.annotation.concurrent.ThreadSafe` | 1 | class |

One class contains more than one of these descriptors. Source inspection found 157 Java files with
JSR-305 imports: 150 `Nullable`, seven `GuardedBy` and one `ThreadSafe` files, with one overlap.

The bytecode scan found:

- zero `CONSTANT_Class` references to any JSR-305 type;
- zero field, method, `NameAndType` or `MethodType` descriptors containing a JSR-305 type;
- zero Tink reflection or `Class.forName` calls targeting JSR-305; the only `Class.forName` target
  in the source is optional Conscrypt; and
- no JSR-305 reference in Tink's only consumer rule, `META-INF/proguard/protobuf.pro`.

The references are therefore annotation attributes, not executable type dependencies or API member
types. Classes directly relevant to the exact v0.3 base inherited by protocol v0.4 that appear in the exact 182-class list include
`KeysetHandle` and its builder classes, the two internal `RegistryConfiguration` classes,
`AesGcmParameters$Builder`, `AesGcmKey`/builder/manager,
`AesGcmHkdfStreamingParameters$Builder`, `AesGcmHkdfStreamingKeyManager`, `StreamingAeadKey` and
`AesGcmHkdfStreamingProtoSerialization`. `AndroidKeystoreKmsClient`,
`AesGcmHkdfStreamingParameters` itself and `AesGcmParameters` itself do not contain a JSR-305
descriptor; their nested builders do.

## Compile, verifier, shrinker and runtime result

Safe probes ran outside the repository dependency graph and did not perform recovery, crypto
measurement or device execution.

| Probe | Exact result without `jsr305` |
|---|---|
| Kotlin consumer compile | Kotlin 2.2.10, `-Xjsr305=strict`, JVM 17 compiled seven exact public API types plus the v0.3 streaming builder, microfile builder and Keystore Builder chain; exit 0 |
| JVM load/reflection | OpenJDK 17 loaded without initialization and inspected fields, constructors, methods and annotations for all 182 classes; 182/182, zero failures |
| D8 | Android build-tools 36.0.0 D8 8.10.9-dev, min API 28, exact seven-artifact closure after removing `jsr305`; exit 0 |
| Bare R8 | R8 9.3.16 embedded in AGP builder 9.3.1, min API 28, tree-shaking and minification disabled; exit 1 for exactly `Nullable`, `GuardedBy`, `ThreadSafe` |
| Narrow-rule R8 | Same exact R8 preservation probe for all 1,878 Tink classes, with the other six non-JSR-305 closure artifacts on the classpath, plus the three exact rules below; exit 0, output SHA-256 `3acbed65809d204ccae51393c453bb2982563dd3cbbf6075beb22e8e612af537` |
| Full seven-program-artifact R8 observation | Same R8/min API with all seven remaining closure JARs as program inputs and the exact three rules; no JSR-305 missing class remained, but exit 1 on independent `javax.lang.model.element.Modifier` from `error_prone_annotations:2.41.0` |

Required future R8 rules:

```proguard
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn javax.annotation.concurrent.ThreadSafe
```

No wildcard such as `-dontwarn javax.annotation.**` is accepted. The narrow rule acknowledges only
the three types proven annotation-only in this exact JAR.

The full seven-program-artifact observation is not a reason to add another `dontwarn`. The future
real AGP graph and release build must pass with an empty unresolved-missing-class set. If
`Modifier` is present there, its source/variant role and remediation require separate engineering,
security and dependency evidence; `REC-JSR305-EXCLUDE-001` neither resolves nor waives it.

ART source at both repository boundaries—Android 9/API 28 tag `android-9.0.0_r1` and Android
16/API 36 tag `android-16.0.0_r2`—clears the pending resolution exception and continues past an
annotation whose class cannot be resolved. This agrees with the JVM reflection probe and means
ordinary class loading or Tink execution does not require `Nullable`. Application code must not
attempt to query these unavailable annotation types reflectively. A separately authorized future
harness still has to prove its exact debug/release Android build and package; this governance task
does not substitute for that evidence.

Recompiling Tink's own Java sources would require JSR-305 definitions because those sources import
the annotations. Dora consumes the published binary and is not rebuilding Tink. Removing the
definitions also removes Kotlin's JSR-305 nullability enhancement, so the future Kotlin harness
must use explicit null handling and tests instead of treating those annotations as an enforced
contract. That is a compile-time static-analysis tradeoff, not a Tink runtime behavior change.

## Options A–E

| Option | Technical / graph effect | License and governance effect | Required checks | Schedule / constraint |
|---|---|---|---|---|
| **A. Exclude completely — recommended with conditions** | Scoped root-edge exclude; future closure has zero resolved `jsr305`; no replacement or `compileOnly`; exact narrow R8 rule required | Avoids using/packaging the conflicted artifact but does not adjudicate its license; Product/IP must accept avoidance | all resolvable configurations and consumers enumerated; zero component count; debug/D8 and release/R8 pass with no unresolved missing classes; package defines zero JSR-305 classes | Lowest delay; bare exclude alone is forbidden; independent R8 issues cannot be suppressed under this policy |
| **B. Keep only as `compileOnly`** | Can provide compiler/shrinker definitions without packaging, but the conflicted coordinate is still resolved and used; violates zero-component policy | Conflict still requires Product/IP/Legal disposition | compile classpath/license review, runtime/package absence, R8 verification | No benefit over conditioned A; governance blocker remains |
| **C. Replace with a compatible annotation artifact** | No vetted drop-in in the reviewed closure: AndroidX, JetBrains and common annotation APIs use different packages or do not supply all three exact types; custom stubs create a new artifact and split-package/provenance surface | New license, provenance and compatibility review; must not copy disputed source silently | exact binary names/retention/targets, compiler and R8 behavior, artifact admission | More work and risk than A; not recommended |
| **D. Retain and seek Legal/IP clarification** | Keeps the publisher eight-coordinate graph unchanged | Only safe fallback if Product/IP rejects exclusion; conflict remains blocking until authoritative terms/counsel path is recorded | existing authenticity packet plus qualified disposition and exact graph review | External dependency and uncertain delay |
| **E. Abandon Tink** | Removes both v0.3 Tink candidates and requires a new crypto/recovery candidate | Avoids this coordinate but starts new security, license, provenance and compatibility review | new DEC/ADR, prospective Gate Set/protocol version, dependency and PoC evidence | Highest delay; use only if A and D are rejected |

## Prospective policy — not implemented in this task

The later scoped declaration must use a root-local exclusion equivalent to:

```kotlin
implementation("com.google.crypto.tink:tink-android:1.23.0") {
    exclude(group = "com.google.code.findbugs", module = "jsr305")
}
```

This snippet is policy evidence only; it was not added to Gradle. A global exclusion is not
recommended because it can conceal a new path elsewhere.

The future graph report must enumerate every resolvable configuration in `:poc:recovery` and every
module that consumes it, including compile, runtime, unit-test, instrumented-test, lint, D8/R8 and
packaging inputs for every variant. `com.google.code.findbugs:jsr305:3.0.2` must occur zero times.
`compileOnly`, test-only or another transitive path does not qualify as exclusion. The report must
also prove the scoped edge, exact three-line R8 rule, debug and release non-metric builds, and zero
packaged JSR-305 class definitions. The release evidence must list zero unresolved R8 missing
classes; in particular, the separate `javax.lang.model.element.Modifier` observation may not be
waived by expanding `dontwarn`. `tools/check_poc_recovery_run_readiness.py` fails closed on a
present nonconforming report and cannot pass while the report is absent.

## Recorded owner disposition

> For POC-RECOVERY-001 Stage 0 evaluation only, I approve policy
> REC-JSR305-EXCLUDE-001: a future separately scoped harness may declare exact
> `com.google.crypto.tink:tink-android:1.23.0` only with a Tink-local exclusion of exact
> `com.google.code.findbugs:jsr305:3.0.2`, zero resolved JSR-305 components in every covered
> compile/runtime/benchmark/test/packaging configuration, and only the exact three narrow R8
> `-dontwarn` rules recorded above. Broader suppression is forbidden; any recurrence fails closed
> for implementation verification and execution. This treats the conflicted artifact as excluded;
> it does not decide whether Apache-2.0 or BSD-3-Clause governs it and does not approve its use or
> distribution. Approval remains limited to the prospective policy and reviewed governance package
> for a future exact excluded Stage 0 graph. This decision does not admit Tink to production,
> authorize redistribution, assign Production Legal/Security, authorize implementation by itself,
> or change `executionAllowed=false`. Exact graph evidence and a release R8 build with no unresolved
> missing classes remain mandatory; any `javax.lang.model.element.Modifier` condition from
> `error_prone_annotations:2.41.0` must be resolved separately without a broad `dontwarn`, followed
> by accountable Engineering/Security review and a later separate execution authorization.

The disposition is recorded in
`docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md` against decision-input governance HEAD
`eb312feb2a0d5e5b24b45fcd045bacca94e8c9da`. It approves neither an actual graph nor implementation
or execution; all later fail-closed gates remain.
