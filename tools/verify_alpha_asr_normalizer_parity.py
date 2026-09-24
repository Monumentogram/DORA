#!/usr/bin/env python3
"""Opt-in, pinned-source semantic parity check. No network or package dependencies.

Supply only the official basic.py at the profile's revision. The exact SHA-256 is
checked BEFORE parsing or executing. Omit its unused third-party regex import;
split_letters=False never uses regex. All executed normalizer AST nodes are intact.
This verifier is a host test utility, not an application/runtime dependency.
"""
import ast
import hashlib
import json
from pathlib import Path
import sys

import alpha_asr_eval_text_contract as contract
from test_alpha_asr_eval_text_contract import FIXTURES


def fixture_inputs():
    """Closed deterministic generated cases: 26 known answers + 4096 + 576."""
    yield from (source for source, _ in FIXTURES)
    for index in range(4096):
        point = (index * 263) % 0x110000
        if 0xD800 <= point <= 0xDFFF:
            point = 0x20
        yield " A" + chr(point) + "Б "
    atoms = ("HELLO", "ПРИВЕТ", "CAFÉ", "И\u0306", "１２", "ᴬ", "İ", "<x]", "[x>",
             "(x(y)z)", "[]", "$")
    separators = ("", " ", "\t\n", "\u00a0")
    for left in atoms:
        for right in atoms:
            for sep in separators:
                yield left + sep + right


def verify(source):
    contract.check_environment()
    contract.require(type(source) is bytes and hashlib.sha256(source).hexdigest()
                     == contract.UPSTREAM_SHA256, "BLOCKED_NORMALIZATION_PARITY")
    tree = ast.parse(source.decode("utf-8"))
    removed = [node for node in tree.body if isinstance(node, ast.Import)
               and len(node.names) == 1 and node.names[0].name == "regex"]
    contract.require(len(removed) == 1, "BLOCKED_NORMALIZATION_PARITY")
    tree.body = [node for node in tree.body if node not in removed]
    namespace = {}
    exec(compile(tree, "<pinned-openai-basic-normalizer>", "exec"), namespace)
    upstream = namespace["BasicTextNormalizer"](remove_diacritics=False, split_letters=False)
    for text, expected in FIXTURES:
        contract.require(upstream(text) == expected, "BLOCKED_NORMALIZATION_PARITY")
    count = 0
    digest = hashlib.sha256()
    for text in fixture_inputs():
        want, got = upstream(text), contract.normalize(text)
        contract.require(want == got and want.split() == contract.normalized_tokens(text),
                         "BLOCKED_NORMALIZATION_PARITY")
        # Deterministic length-framed transcript of synthetic checks, never emitted.
        encoded = json.dumps([text, want], ensure_ascii=True, separators=(",", ":")).encode("ascii")
        digest.update(len(encoded).to_bytes(4, "big"))
        digest.update(encoded)
        count += 1
    return {"result": "PASS", "upstreamSourceSha256": contract.UPSTREAM_SHA256,
            "fixtureCount": count, "literalKnownAnswers": len(FIXTURES),
            "fixtureResultsSha256": digest.hexdigest(),
            "thirdPartyImportOmitted": "regex", "splitLetters": False,
            "normalizerAstChanged": False, "realCorpusCases": 0}


def main():
    try:
        contract.require(len(sys.argv) == 2, "BLOCKED_NORMALIZATION_PARITY")
        with Path(sys.argv[1]).open("rb") as stream:
            source = stream.read(2065)
        result = verify(source)
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 0
    except (OSError, ValueError, TypeError, SyntaxError, RuntimeError):
        print('{"error":"BLOCKED_NORMALIZATION_PARITY"}')
        return 2


if __name__ == "__main__":
    sys.exit(main())
