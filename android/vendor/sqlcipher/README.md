# SQLCipher composite candidate for Stage 8.2

Status: **CANDIDATE_PENDING_RUNTIME_ADMISSION**. This directory does not certify
Stage 8.2 or any unexecuted platform, packaging, CI or publication gate.

The distinct artifact is
`com.monumentogram.dora.thirdparty:sqlcipher-android:4.17.0-dora.1`.
An exclusive local Maven repository prevents substitution from remote repositories.
`provenance.json` pins every packaged ABI, the published Java classes, generated
amalgamation and header, POM, AAR, source revisions and NDK. Only arm64-v8a,
armeabi-v7a, x86 and x86_64 are packaged. A build-directory RISC-V output is not an
admitted ABI and is never selected by the packager.

## Composite source identity

The published `net.zetetic:sqlcipher-android:4.17.0` AAR has SHA256
`44fc40c33d1de597c8339072a71fa0ff20e12d01ab352d6abe4ad5df668ead94`.
Its non-native entries, including `classes.jar`, are retained. The 37 Java
sources in its published source JAR match Android wrapper commit
`ae57a61052d8c41ce35cd48319b2f6f20f4de6bf` after line-ending normalization.

The wrapper tag's original core gitlink is
`e2a6040f2ae5cfff2b3e08eb3320007d93cdf3fc` (core 4.16.0). The composite deliberately
uses official core 4.17.0 commit
`810db22f575ee7cf94ea96a3e91622b5fcece3dc`, with SQLite 3.53.3. This is a distinct
composite build, not a byte-equivalent rebuild of the Maven artifact. Actual
stock runtime also reports core 4.17.0 / SQLite 3.53.3; the stale gitlink does not
establish the stock binary's core version.

LibTomCrypt is pinned at `476a9579ae94f32b9ea9e2747bfb04b302370259`.
The three exact patches in `patches/` make four narrow changes:

1. Append `SQLCIPHER_OMIT_LOG` while preserving all upstream compile flags.
2. Omit the JNI exception logging body under that flag.
3. Omit SQL profiling and reject `cipher_profile` when logging is omitted.
4. Exclude LibTomCrypt's ANSI clock-timing entropy fallback on Android. Failed
   or short OS entropy reads propagate to SQLCipher initialization failure.

No accepted Recovery source is patched. Default cipher parameters and format
remain those of core 4.17.0. The application separately disables Java logging
before library loading and supplies a fail-closed single-connection helper.

## Rebuild inputs and procedure

Use pristine checkouts at the three pinned revisions. Apply `android-no-log.patch`
at the wrapper root, `core-no-profile.patch` at the explicit core root, and
`libtomcrypt-no-timing-entropy.patch` at the LibTomCrypt submodule root. Verify
the patch digests against `patches/sha256.json` before applying.

Generate `sqlite3.c` and `sqlite3.h` from the patched explicit core using its
`configure --disable-tcl --with-tempstore=yes` and `make sqlite3.c` route. Copy
only these two generated files into the wrapper's `sqlcipher/src/main/jni/sqlcipher/`.
Do not copy a host `sqlite_cfg.h` or define `_HAVE_SQLITE_CONFIG_H` in the target
build. The original local generation used verified Zig 0.16.0 as the Windows
host C compiler, Git for Windows Tcl/sh, and the NDK make executable. These are
host generators; target compilation uses the pinned NDK exclusively.

From the wrapper's `sqlcipher/` directory, run NDK **28.2.13676358**:

```text
ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=src/main/jni/Android.mk
  NDK_APPLICATION_MK=src/main/jni/Application.mk
  APP_ABI="arm64-v8a armeabi-v7a x86 x86_64" APP_PLATFORM=android-28
  APP_OPTIM=release APP_SHORT_COMMANDS=true -j8
```

The application makefile selects static libc++, flexible page sizes and 16 KiB
link alignment. Do not set `SQLCIPHER_CFLAGS` to the logging macro alone: that
variable replaces the upstream cipher/security configuration.

Put the original AAR and the built wrapper tree in the input layout documented
by `tools/package_sqlcipher_candidate.py`, then run that tool with `--ndk` and
`--receipt`. ZIP entry names, timestamps, modes and ordering are fixed. The
packager checks the upstream AAR digest, exact ABI set, each ELF load segment's
16 KiB alignment/congruence and absence of Android logging imports. Packaging
does not replace runtime verification or source provenance review.

## Regression evidence and limits

The synthetic native entropy probe in `tools/native/sqlcipher_entropy_probe.c`
links the exact SQLite object and LibTomCrypt archive with linker wrappers for
`fopen` and `clock`. Its denied-entropy mode makes both random-device opens fail.
The pre-fix negative control reaches the forbidden clock fallback (exit 77).
The fixed build rejects initialization (`SQLITE_ERROR`) without that fallback;
the available-entropy control initializes successfully. The test wrappers are
not included in the shipped shared library.

Stock-versus-candidate SQL, path and profiling canaries reproduce the three
stock leaks and suppress them in the hardened candidate. Full final process-log
scans, bound-value canaries, platform matrix and final APK checks remain separate
acceptance requirements. Native libc++ fatal diagnostics still exist; no claim
of universal silence under every native crash is made.

## Licenses and notices

SQLCipher uses BSD-3-Clause; Android wrapper/JNI portions also carry Apache-2.0;
SQLite is public domain; LibTomCrypt offers public-domain/WTFPL alternatives and
this distribution selects WTFPL. Static libc++/libc++abi use the LLVM notices,
including Apache-2.0 WITH LLVM-exception and retained legacy notices where
applicable. Exact upstream license texts and NDK/LLVM notices are included in
the AAR under `META-INF/LICENSES/`. Host toolchain licenses are not statements
that those host programs are shipped in the APK. Release SBOM/license inventory
must include both the Maven composite and its native constituent components.
