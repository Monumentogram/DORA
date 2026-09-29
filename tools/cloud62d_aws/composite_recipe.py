"""Exact persisted bytes for the owned-corpus technical composite recipe.

The owned corpus has always hashed sorted compact UTF-8 JSON followed by one LF.
Keep this single-byte terminator in producer, manifest validation and Lambda code.
"""

import hashlib
import json


def canonical_recipe_bytes(parts):
    if not isinstance(parts, list) or not parts:
        raise ValueError('composite recipe parts must be a nonempty list')
    return (json.dumps(parts, ensure_ascii=False, sort_keys=True,
                       separators=(',', ':'), allow_nan=False) + '\n').encode('utf-8')


def recipe_sha256(parts):
    return hashlib.sha256(canonical_recipe_bytes(parts)).hexdigest()


def require_recipe_sha256(parts, expected_sha256):
    if recipe_sha256(parts) != expected_sha256:
        raise ValueError('composite recipe SHA differs')


def require_recipe_artifact_bytes(body, expected_sha256):
    if not isinstance(body, bytes):
        raise ValueError('composite recipe artifact must be bytes')
    try:
        parts = json.loads(body)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError('composite recipe artifact malformed') from error
    if body != canonical_recipe_bytes(parts):
        raise ValueError('composite recipe artifact bytes differ')
    require_recipe_sha256(parts, expected_sha256)
    return parts
