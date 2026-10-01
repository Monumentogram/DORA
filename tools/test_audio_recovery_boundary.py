"""Negative controls for the bounded Stage 8.1 successor, not runtime certification."""
import copy
import unittest

import validate_audio_recovery_boundary as audio
import validate_alpha_release as release
import validate_poc_recovery_governance as recovery
import validate_security_identity_contract as security


class AudioBoundaryTest(unittest.TestCase):
    def test_one_implementation_and_optional_evidence_child(self):
        history = ["a" * 40 + " " + audio.BASE]
        paths = [list(audio.PATHS)]
        audio.validate_history(history, paths, "a" * 40)
        history.append("b" * 40 + " " + "a" * 40)
        paths.append([audio.REPORT])
        audio.validate_history(history, paths, "b" * 40)

    def test_wrong_baseline_merge_revert_extra_commit_and_inventory_rejected(self):
        good = (["a" * 40 + " " + audio.BASE], [list(audio.PATHS)], "a" * 40)
        mutations = (
            lambda x: x[0].__setitem__(0, "a" * 40 + " " + "b" * 40),
            lambda x: x[0].__setitem__(0, x[0][0] + " " + "c" * 40),
            lambda x: x[1][0].append("android/poc/recovery/forbidden.kt"),
            lambda x: x[1][0].append("docs/evidence/recovery-clean-replacement-integration-v0.1.json"),
            lambda x: x[1][0].remove(audio.ADR),
            lambda x: x[0].extend(["b" * 40 + " " + "a" * 40, "c" * 40 + " " + "b" * 40]),
        )
        for mutation in mutations:
            bad = copy.deepcopy(good)
            mutation(bad)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                audio.validate_history(*bad)
        with self.assertRaises(ValueError):
            audio.validate_history(good[0] + ["b" * 40 + " " + "a" * 40],
                                   good[1] + [[audio.KOTLIN + "RecoveryAudioBridge.kt"]], "b" * 40)

    def test_frozen_six_transition_policy_is_composed_not_bypassed(self):
        commits = [recovery.REC_CLEAN_REMEDIATION_HEAD, recovery.REC_CLEAN_CLOSURE_GOVERNANCE_HEAD,
                   recovery.REC_CLEAN_FINALIZATION_HEAD, security.BASE, security.CORRECTION_BASE,
                   audio.BASE, "a" * 40]
        parents = [recovery.REC_CLEAN_INTEGRATED_ANCHOR] + commits[:-1]
        paths = [list(recovery.REC_CLEAN_GOVERNANCE_PATHS), list(recovery.REC_CLEAN_GOVERNANCE_PATHS),
                 list(recovery.REC_CLEAN_CLOSURE_PATHS), list(recovery.REC_CLEAN_CORRECTION_PATHS),
                 list(security.PATHS), list(security.PATHS), list(audio.PATHS)]
        changes = {"committed": sorted(set().union(*map(set, paths))), "staged": [], "unstaged": [], "untracked": []}
        data = [changes, {}, {}, [c + " " + p for c, p in zip(commits, parents)], commits[-1], paths]
        recovery.validate_rec_clean_integrated_state(*data)
        for layer in ("staged", "unstaged", "untracked"):
            bad = copy.deepcopy(data); bad[0][layer].append(audio.ADR)
            with self.subTest(layer=layer), self.assertRaises(ValueError):
                recovery.validate_rec_clean_integrated_state(*bad)
        for mutate in (lambda d: d[1].update(protected="changed"),
                       lambda d: d[3].__setitem__(5, "b" * 40 + " " + security.CORRECTION_BASE),
                       lambda d: d[5][0].append("forbidden-then-reverted")):
            bad = copy.deepcopy(data); mutate(bad)
            with self.assertRaises(ValueError): recovery.validate_rec_clean_integrated_state(*bad)

    def test_source_and_composition_mutations_rejected(self):
        files = {p: (audio.ROOT / p).read_text(encoding="utf-8") for p in audio.PATHS if not p.endswith(".lockfile")}
        audio.validate_sources(files)
        mutations = (
            (audio.PREFIX + "build.gradle.kts", "poc/recovery/src/main/kotlin", "copied/engine"),
            (audio.PREFIX + "build.gradle.kts", "tink-android:1.23.0", "tink-android:other"),
            (audio.KOTLIN + "ProductAudioPort.kt", '16_000, 1', '48_000, 1'),
            (audio.KOTLIN + "RecoveryAudioBridge.kt", 'capability.authorizes(prefix)', 'true'),
            (audio.KOTLIN + "RecoveryAudioBridge.kt", 'catalog.reserve(', 'unsafe.reserve('),
            (audio.KOTLIN + "RecoveryAudioBridge.kt", 'requireComplete = true', 'requireComplete = false'),
            (audio.KOTLIN + "RecoveryAudioBridge.kt", 'quarantine: RecoveryQuarantineController', 'quarantine: Any'),
            (".github/workflows/android-ci.yml", ':core:audio:lintDebug', ':core:audio:help'),
        )
        for path, before, after in mutations:
            bad = dict(files); self.assertIn(before, bad[path]); bad[path] = bad[path].replace(before, after)
            with self.subTest(path=path, mutation=before), self.assertRaises(ValueError): audio.validate_sources(bad)
        for bypass in ("AndroidRecoveryJournalDatabase(", "FileOutputStream", "MemoryAudioCatalog", "AesGcmJce"):
            bad = dict(files); bad[audio.KOTLIN + "RecoveryAudioBridge.kt"] += bypass
            with self.subTest(bypass=bypass), self.assertRaises(ValueError): audio.validate_sources(bad)

    def test_existing_workflow_gates_are_unchanged(self):
        current = (audio.ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
        baseline = audio.git(audio.ROOT, "show", audio.BASE + ":.github/workflows/android-ci.yml").decode()
        self.assertEqual(current.replace(audio.STEP, "", 1), baseline)
        original = audio.git(audio.ROOT, "show", "351874fff41774f10298e8a186bdc78bf6bb720f:.github/workflows/android-ci.yml").decode()
        release.validate_successor_workflow(original, current)
        with self.assertRaises(ValueError): release.validate_successor_workflow(original, current.replace(":poc:recovery:testDebugUnitTest", ":poc:recovery:help"))

    def test_historical_status_and_runtime_disclaimer_cannot_be_rewritten(self):
        for path in audio.STATUS:
            old = audio.git(audio.ROOT, "show", audio.BASE + ":" + path).decode()
            text = (audio.ROOT / path).read_text(encoding="utf-8")
            audio.validate_status_text(text, old)
            for bad in (text.replace("Stage 8.2 = NOT_STARTED", "Stage 8.2 = PASS"), text[:-1],
                        text.replace("Encrypted product persistence runtime = NOT_ACCEPTED", "Runtime PASS")):
                with self.assertRaises(ValueError): audio.validate_status_text(bad, old)


if __name__ == "__main__":
    unittest.main()
