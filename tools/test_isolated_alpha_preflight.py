"""Synthetic host guards; never invoke ADB or read a physical vault."""
import unittest

import isolated_alpha_preflight as guard


USERS = '''Users:
  UserInfo{0:Owner:4c13} serialNo=0 isPrimary=true
    Type: android.os.usertype.full.SYSTEM
  UserInfo{12:Synthetic:410} serialNo=19 isPrimary=false
    Type: android.os.usertype.full.SECONDARY
    Flags: 1040 (FULL|INITIALIZED)
'''


class PreflightTests(unittest.TestCase):
    def test_owner_rejected_before_any_adb(self):
        calls = []
        with self.assertRaisesRegex(ValueError, 'STOP / OWNER_USER_SELECTED'):
            guard.preflight(lambda *args: calls.append(args), 0, 0)
        self.assertEqual([], calls)

    def test_explicit_secondary_identity(self):
        self.assertEqual({'user_id': 12, 'serial_number': 19},
                         guard.secondary_user(USERS, 12, 19))

    def test_reused_user_id_rejected(self):
        with self.assertRaises(ValueError):
            guard.secondary_user(USERS, 12, 20)

    def test_nonexistent_user_rejected(self):
        with self.assertRaises(ValueError):
            guard.secondary_user(USERS, 13, 19)

    def test_profile_guest_restricted_unknown_and_duplicate_rejected(self):
        for kind in ('profile.MANAGED', 'full.GUEST', 'full.RESTRICTED', 'full.DEMO', ''):
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                guard.secondary_user(USERS.replace('full.SECONDARY', kind), 12, 19)
        with self.assertRaises(ValueError):
            guard.secondary_user(USERS + USERS, 12, 19)

    def test_disabled_or_removing_user_rejected(self):
        for flags in ('450', '510', '490', '610'):
            with self.subTest(flags=flags), self.assertRaises(ValueError):
                guard.secondary_user(USERS.replace('Synthetic:410', 'Synthetic:' + flags), 12, 19)

    def test_explicit_unsafe_lifecycle_rejected_with_otherwise_valid_flags(self):
        for marker in ('<partial>', '<removing>', '<pre-created>'):
            modified = USERS.replace('serialNo=19 isPrimary=false',
                                     'serialNo=19 isPrimary=false ' + marker)
            with self.subTest(marker=marker), self.assertRaisesRegex(ValueError,
                                                                    'UNSAFE_USER_LIFECYCLE'):
                guard.secondary_user(modified, 12, 19)

    def test_only_read_only_commands_and_empty_candidate(self):
        calls = []
        def command(*args):
            calls.append(args)
            if args == ('get-state',):
                return 'device'
            if args == ('shell', 'dumpsys', 'user'):
                return USERS
            if args == ('shell', 'am', 'get-current-user'):
                return '12'
            return ''
        result = guard.preflight(command, 12, 19)
        self.assertEqual(12, result['user_id'])
        self.assertEqual('READY_FOR_SEPARATELY_APPROVED_INSTALL', result['status'])
        self.assertFalse(result['device_mutated'])
        self.assertTrue(all('install' not in row and 'create-user' not in row for row in calls))

    def test_any_global_or_retained_candidate_rejected(self):
        def command(*args):
            if args == ('get-state',):
                return 'device'
            if args == ('shell', 'dumpsys', 'user'):
                return USERS
            if args == ('shell', 'am', 'get-current-user'):
                return '12'
            return 'package:com.monumentogram.dora.alpha.test'
        with self.assertRaisesRegex(ValueError, 'CANDIDATE_ALREADY_KNOWN'):
            guard.preflight(command, 12, 19)

    def test_owner_foreground_rejected_without_switch(self):
        def command(*args):
            if args == ('get-state',):
                return 'device'
            if args == ('shell', 'dumpsys', 'user'):
                return USERS
            return '0'
        with self.assertRaisesRegex(ValueError, 'SELECTED_USER_NOT_FOREGROUND'):
            guard.preflight(command, 12, 19)


if __name__ == '__main__':
    unittest.main()
