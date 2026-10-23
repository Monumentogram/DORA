"""Permanent local-product policy; Android's own screen lock remains independent."""
import re

FORBIDDEN = re.compile(
    r'BiometricPrompt|BiometricManager|FingerprintManager|createConfirmDeviceCredentialIntent|'
    r'AppLockAuthenticationActivity|AndroidAuthProof|AndroidAppLock|UnlockResult|'
    r'requestUnlock|openCredentialSetup|ACTION_SET_NEW_PASSWORD|ACTION_SECURITY_SETTINGS|'
    r'setUserAuthenticationRequired\s*\(\s*true\s*\)|isDeviceSecure|credentialAvailable|'
    r'Разблокиру[йе]|Разблокировать|Настройте PIN|USE_BIOMETRIC|USE_FINGERPRINT'
)


def violations(root):
    result = []
    for source in (root / 'android').rglob('src'):
        if 'build' in source.relative_to(root).parts:
            continue
        for variant in source.iterdir():
            if not variant.is_dir() or 'test' in variant.name.lower():
                continue
            for path in variant.rglob('*'):
                if path.suffix not in {'.kt', '.java', '.xml'}:
                    continue
                text = path.read_text(encoding='utf-8-sig')
                if FORBIDDEN.search(text):
                    result.append(path.relative_to(root).as_posix())
    return sorted(result)
