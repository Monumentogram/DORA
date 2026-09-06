# Dora MVP 1 — REC-I3 microfile controller hardening review correction

Correction ID: `rec-i3-microfile-controller-hardening-correction-stage0-v0.1`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15\
Reviewed implementation checkpoint: `2621242bdc5691768f14b128cbf7cf238d336ea0` / tree `d4088f967e5745c1f086244c72236f68ae36d0d0`

This additive correction implements the publication controller protections already required by the
sequential microfile scope and closes two lower-severity authority/evidence defects found during
independent review. It adds no new runtime stage, dependency or admission.

1. Focused host tests cover negative, zero and over-count write progress; safe final collision;
   unsafe final/path inspection failure; and open, write, file-fsync, close, rename and parent-fsync
   failures for each of the unit-envelope, unit-ciphertext, manifest-envelope and manifest-
   ciphertext artifacts. They assert the exact failed protocol step, conservative side-effect
   remainder, descriptor closure where a descriptor was acquired, zero publication capability and
   no journal commit. These fakes prove controller routing only; Android `lstat`, descriptor and
   durability behavior remains gated platform evidence.
2. Shared-lease tests cover candidate-vs-candidate exclusion for the same run and independent
   progress for different runs across distinct controller/storage/journal instances.
3. `CandidatePublicationCapability` accepts only a private controller-held proof created after
   successful `endTransaction` return. Same-module code with an arbitrary proof cannot mint it.
4. Mutable in-flight durability progress is private to the controller. Every returned result owns
   a deep immutable snapshot whose nested artifact fields cannot be changed by a same-module
   caller after return.

The test matrix remains host-only source evidence. All ten readiness blockers remain open,
`fullRecI3Completed=false`, and no device, emulator, preflight, fault campaign, measurement, PASS,
production admission or merge is claimed.
