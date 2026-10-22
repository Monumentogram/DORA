"""Packaged version labels must match independently pinned APK identities."""
import unittest

from version_label import check_label


class VersionLabelTests(unittest.TestCase):
    def table(self, package, label, qualifier='(default)'):
        return (f'    config {qualifier}:\n'
                f'      resource 0x7f080000 {package}:string/alpha_version_label: t=0x03 d=0x40\n'
                f'        (string8) "{label}"\n')

    def test_all_actual_variant_identities(self):
        for variant, package, code, name, prefix in (
            ('release', 'com.monumentogram.dora', '5', '0.1.0-alpha.3', 'DORA Alpha'),
            ('debug', 'com.monumentogram.dora.debug', '5', '0.1.0-alpha.3-debug', 'DORA Alpha'),
            ('alphaTest', 'com.monumentogram.dora.alpha.test', '5', '0.1.0-alpha.3', 'DORA Alpha'),
            ('ownerPilot', 'com.monumentogram.dora.alpha.pilot', '2', '0.1.0-owner-alpha.2', 'DORA Owner Alpha'),
        ):
            label = f'{prefix} {name} ({code})'
            badging = f"package: name='{package}' versionCode='{code}' versionName='{name}'"
            with self.subTest(variant=variant):
                self.assertEqual(label, check_label(badging, self.table(package, label), variant))

    def test_missing_stale_wrong_name_code_and_qualified_overrides_fail(self):
        package = 'com.monumentogram.dora.alpha.pilot'
        badging = f"package: name='{package}' versionCode='2' versionName='0.1.0-owner-alpha.2'"
        good = self.table(package, 'DORA Owner Alpha 0.1.0-owner-alpha.2 (2)')
        for broken in ('', good.replace('owner-alpha.2', 'owner-alpha.1'),
                       good.replace('(2)', '(5)'), self.table(package, 'DORA Alpha 0.1.0-alpha.3 (5)'),
                       good + self.table(package, 'wrong', 'ru'), good + good,
                       good.replace('(string8)', '(reference)'),
                       good.replace('t=0x03', 't=0x01'), good.replace('(default)', 'ru')):
            with self.subTest(broken=broken), self.assertRaises(ValueError):
                check_label(badging, broken, 'ownerPilot')
        # A self-consistent but unapproved package version is not a release pass.
        with self.assertRaises(ValueError):
            check_label(badging.replace("versionCode='2'", "versionCode='1'"),
                        good.replace('(2)', '(1)'), 'ownerPilot')


if __name__ == '__main__':
    unittest.main()
