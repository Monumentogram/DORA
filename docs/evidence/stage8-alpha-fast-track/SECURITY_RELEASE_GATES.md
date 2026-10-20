# Release gates remain separate

`DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN`.
`SIGNED_ALPHA_RELEASE = BLOCKED`.

The release source-set implementations of `DevelopmentAppLockOverride` and
`DevelopmentDeviceSecurityOverride` deny the development exceptions. The release
diagnostic fence also denies protected diagnostic access. The release JVM suite
executes the existing negative controls. Normal debug/release APKs must contain
no private successor-policy pin, policy, signing key or credential material.
The build here is unsigned release verification, not a signed user Alpha.

Closing the physical restoration gate still requires the owner-approved secure
device procedure in ADR-DEV-002: real Android credential, both development
markers disabled, restart, actual App Lock prompt, background/device-lock
revocation, fresh authentication for Resume, and continued encrypted-vault
readability. This task cannot establish those facts on the historical POCO.

| Decision | Required evidence | Current boundary |
| --- | --- | --- |
| Stage8 limited software | Runtime cap, regressions, crypto/Recovery tests, review and exact-SHA CI | This branch's evidence and CI |
| Stage9 implementation | Stable source/read/identity/version contracts | Independent implementation admission after software verification |
| Limited physical Alpha | One <=300s screen-off recording on a separate safe device with full authenticated readback | NOT_RUN / SAFE_DEVICE_UNAVAILABLE |
| Signed user Alpha | Physical acceptance plus security restoration and existing signing/release gates | BLOCKED |

No physical POCO access or credential change occurred in this task.
`LONG02_ROOT_CAUSE = NOT_PROVEN`.
`LONG02_PHYSICAL_PROTECTION = NOT_INSTALLED`.
`STAGE_8_6 = NOT_READY`.
