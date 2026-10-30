"""Packaged version labels must match independently pinned APK identities."""
import unittest

from version_label import check_label
from run_version_label_device import installed_path


class VersionLabelTests(unittest.TestCase):
    def test_installed_apk_path_accepts_legacy_and_randomized_android_layouts(self):
        for path in ('/data/app/com.monumentogram.dora.debug-fixture==/base.apk',
                     '/data/app/~~fixture-token==/com.monumentogram.dora.debug-fixture==/base.apk'):
            self.assertEqual(path, installed_path('package:' + path))

    def test_installed_apk_path_rejects_split_ambiguous_and_outside_package_tree(self):
        valid = 'package:/data/app/~~fixture==/package-fixture==/base.apk'
        for output in ('', valid + '\n' + valid, valid.replace('/base.apk', '/split.apk'),
                       valid.replace('/data/app/', '/sdcard/'), valid.replace('~~fixture==', '..'),
                       valid + ';unexpected', valid.replace('base.apk', 'base.apk/extra')):
            with self.subTest(output=output), self.assertRaises(ValueError):
                installed_path(output)

    def table(self, package, label, qualifier='(default)'):
        return (f'    config {qualifier}:\n'
                f'      resource 0x7f080000 {package}:string/alpha_version_label: t=0x03 d=0x40\n'
                f'        (string8) "{label}"\n')

    def test_all_actual_variant_identities(self):
        for variant, package, code, name, prefix in (
            ('release', 'com.monumentogram.dora', '5', '0.1.0-alpha.3', 'DORA Alpha'),
            ('debug', 'com.monumentogram.dora.debug', '5', '0.1.0-alpha.3-debug', 'DORA Alpha'),
            ('alphaTest', 'com.monumentogram.dora.alpha.test', '5', '0.1.0-alpha.3', 'DORA Alpha'),
            ('ownerPreview', 'com.monumentogram.dora.alpha.preview', '8', '0.1.0-owner-preview.8', 'DORA Owner Preview'),
            ('ownerPilot', 'com.monumentogram.dora.alpha.pilot', '4', '0.1.0-owner-alpha.4', 'DORA Owner Alpha'),
        ):
            label = f'{prefix} {name} ({code})'
            badging = f"package: name='{package}' versionCode='{code}' versionName='{name}'"
            with self.subTest(variant=variant):
                self.assertEqual(label, check_label(badging, self.table(package, label), variant))

    def test_missing_stale_wrong_name_code_and_qualified_overrides_fail(self):
        package = 'com.monumentogram.dora.alpha.pilot'
        badging = f"package: name='{package}' versionCode='4' versionName='0.1.0-owner-alpha.4'"
        good = self.table(package, 'DORA Owner Alpha 0.1.0-owner-alpha.4 (4)')
        for broken in ('', good.replace('owner-alpha.4', 'owner-alpha.1'),
                       good.replace('(4)', '(5)'), self.table(package, 'DORA Alpha 0.1.0-alpha.3 (5)'),
                       good + self.table(package, 'wrong', 'ru'), good + good,
                       good.replace('(string8)', '(reference)'),
                       good.replace('t=0x03', 't=0x01'), good.replace('(default)', 'ru')):
            with self.subTest(broken=broken), self.assertRaises(ValueError):
                check_label(badging, broken, 'ownerPilot')
        # A self-consistent but unapproved package version is not a release pass.
        with self.assertRaises(ValueError):
            check_label(badging.replace("versionCode='4'", "versionCode='1'"),
                        good.replace('(4)', '(1)'), 'ownerPilot')


if __name__ == '__main__':
    unittest.main()
