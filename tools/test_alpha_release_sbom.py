"""Fail-closed release graph/SBOM controls; synthetic coordinates only."""
import copy
import unittest

try:
    import alpha_release_sbom as sbom
except ImportError:
    sbom = None

SOURCE = "a" * 40


def graph():
    return {
        "configuration": "releaseRuntimeClasspath",
        "root": "project::app",
        "components": [
            {"id": "project::app", "kind": "project", "path": ":app"},
            {"id": "project::core:model", "kind": "project", "path": ":core:model"},
            {"id": "maven:sample:runtime:1.2.3", "kind": "maven", "group": "sample", "name": "runtime", "version": "1.2.3", "artifacts": [{"name": "runtime-1.2.3.jar", "sha256": "b" * 64}]},
        ],
        "dependencies": [
            {"ref": "project::app", "dependsOn": ["project::core:model", "maven:sample:runtime:1.2.3"]},
            {"ref": "project::core:model", "dependsOn": ["maven:sample:runtime:1.2.3"]},
            {"ref": "maven:sample:runtime:1.2.3", "dependsOn": []},
        ],
        "requested_versions": ["1.2.3"], "unresolved": [],
    }


class ReleaseSbomTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(sbom, "release SBOM implementation is required")

    def test_deterministic_cyclonedx_and_complete_relationships(self):
        original = graph()
        result = sbom.generate(original, SOURCE, {"sample:runtime:1.2.3"}, original)
        self.assertEqual(result["specVersion"], "1.6")
        self.assertEqual(result["metadata"]["component"]["version"], "0.1.0-alpha.2")
        external = next(c for c in result["components"] if c["name"] == "runtime")
        self.assertEqual(external["purl"], "pkg:maven/sample/runtime@1.2.3")
        self.assertEqual(len(result["dependencies"]), 3)
        reordered = copy.deepcopy(original)
        reordered["components"].reverse()
        reordered["dependencies"].reverse()
        for item in reordered["dependencies"]:
            item["dependsOn"].reverse()
        self.assertEqual(sbom.canonical_bytes(result), sbom.canonical_bytes(sbom.generate(reordered, SOURCE, {"sample:runtime:1.2.3"}, original)))

    def test_dynamic_and_unresolved_dependencies_rejected(self):
        for value in ["1.+", "latest.release", "[1,2)", "1.0-SNAPSHOT"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                changed = graph(); changed["requested_versions"] = [value]
                sbom.generate(changed, SOURCE, {"sample:runtime:1.2.3"}, changed)
        changed = graph(); changed["unresolved"] = ["missing"]
        with self.assertRaises(ValueError):
            sbom.generate(changed, SOURCE, {"sample:runtime:1.2.3"}, changed)

    def test_lock_mismatch_and_missing_expected_component_rejected(self):
        with self.assertRaises(ValueError):
            sbom.generate(graph(), SOURCE, set(), graph())
        changed = graph(); changed["components"].pop()
        with self.assertRaises(ValueError):
            sbom.generate(changed, SOURCE, {"sample:runtime:1.2.3"}, graph())

    def test_changed_relationship_or_artifact_rejected(self):
        for mutate in [lambda g: g["dependencies"][0]["dependsOn"].pop(), lambda g: g["components"][2]["artifacts"][0].update(sha256="c" * 64)]:
            changed = graph(); mutate(changed)
            with self.assertRaises(ValueError):
                sbom.generate(changed, SOURCE, {"sample:runtime:1.2.3"}, graph())

    def test_sbom_source_version_graph_and_components_must_match(self):
        valid = sbom.generate(graph(), SOURCE, {"sample:runtime:1.2.3"}, graph())
        sbom.validate(valid, graph(), SOURCE, {"sample:runtime:1.2.3"}, graph())
        for mutate in [lambda b: b["metadata"]["component"].update(version="0.1.0-alpha.1"), lambda b: b["components"].pop(), lambda b: b["dependencies"].pop(), lambda b: b["metadata"]["properties"][0].update(value="c" * 40)]:
            changed = copy.deepcopy(valid); mutate(changed)
            with self.assertRaises(ValueError):
                sbom.validate(changed, graph(), SOURCE, {"sample:runtime:1.2.3"}, graph())

    def test_duplicate_and_dangling_nodes_rejected(self):
        for mutate in [lambda g: g["components"].append(g["components"][0]), lambda g: g["dependencies"][0]["dependsOn"].append("absent")]:
            changed = graph(); mutate(changed)
            with self.assertRaises(ValueError):
                sbom.generate(changed, SOURCE, {"sample:runtime:1.2.3"}, changed)


if __name__ == "__main__":
    unittest.main()
