"""Verify a privately retrieved frozen candidate; no network, upload or overwrite API."""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from . import build
except ImportError:
    import build


def base_manifest():
    return {
        'schemaVersion':1,'retrievalMethodVersion':'drive-owner-download-sha256-v1',
        'artifact':{'name':'sherpa-onnx-vad-1.13.8-dora.1-arm64.aar','version':'1.13.8-dora.1',
                    'bytes':23396212,'sha256':'64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db'},
        'sherpaSourceSha':'11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf',
        'sileroSha256':'1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3',
        'custody':{'provider':'Google Drive','folderId':'1YhHLWIg6X5AexyHwmxR_K1YviBMiP9sF',
                   'fileId':'1CRzSEnh6HOqIVt5l0cxPQc5VUM5dM_3N','public':False,
                   'access':'OWNER_ONLY_AUTHENTICATED_CONNECTOR_OR_DRIVE',
                   'immutability':'APPEND_ONLY_WORKFLOW_AND_SHA256_IDENTITY_NOT_WORM',
                   'overwriteAllowed':False,'ownerCanModifyOrDelete':True,
                   'ciCredentialsGranted':False},
    }


def require(condition,message):
    if not condition:raise ValueError(message)


def validate_manifest(manifest):
    for key,value in base_manifest().items():
        require(manifest.get(key)==value,'Frozen custody identity/access policy mismatch: '+key)


def verify_bytes(data,identity):
    require(len(data)==identity['bytes'],'VAD_BINARY_INTEGRITY_FAILURE: size mismatch')
    require(hashlib.sha256(data).hexdigest()==identity['sha256'],
            'VAD_BINARY_INTEGRITY_FAILURE: SHA256 mismatch')


def verify_file(artifact,manifest):
    validate_manifest(manifest)
    require(artifact.is_file() and not artifact.is_symlink(),'Regular retrieved file required')
    # Bound the read before checking exact bytes; never execute unverified content.
    require(artifact.stat().st_size==manifest['artifact']['bytes'],'VAD_BINARY_INTEGRITY_FAILURE: size mismatch')
    verify_bytes(artifact.read_bytes(),manifest['artifact'])
    build.check_aar(artifact)
    return {'result':'PASS_EXACT_RETRIEVED_AAR','bytes':manifest['artifact']['bytes'],
            'sha256':manifest['artifact']['sha256'],'entries':sorted(build.AAR_ENTRIES),
            'physicalRuntimeProved':False}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest',type=Path,required=True)
    parser.add_argument('--artifact',type=Path,required=True)
    args=parser.parse_args()
    print(json.dumps(verify_file(args.artifact,json.loads(args.manifest.read_text(encoding='utf-8'))),sort_keys=True))
