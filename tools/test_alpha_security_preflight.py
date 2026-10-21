"""Negative controls for successor release acceptance; no private fixtures."""
import unittest

try:
    import alpha_security_preflight as preflight
except ImportError:
    preflight = None


class ReleasePreflightTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(preflight, 'successor artifact verifier is required')

    def test_version_must_strictly_exceed_installed_product(self):
        for code in (3, 4):
            with self.assertRaises(ValueError):
                preflight.check_version(code, 4)
        preflight.check_version(5, 4)

    def test_build_receipt_binds_each_artifact_to_exact_clean_source(self):
        receipt = {'source_sha': 'a' * 40, 'sha256': 'b' * 64, 'source_clean': True}
        preflight.check_build_receipt(receipt, 'a' * 40, 'b' * 64)
        for changed in ({**receipt, 'source_sha': 'c' * 40}, {**receipt, 'sha256': 'd' * 64},
                        {**receipt, 'source_clean': False}, {}):
            with self.assertRaises(ValueError):
                preflight.check_build_receipt(changed, 'a' * 40, 'b' * 64)

    def test_dex_denial_rejects_true_branch_marker_reads_and_early_returns(self):
        good = '.method public final allowed(Ljava/lang/String;ZLjava/io/File;)Z\n .registers 4\n const-string p0, "packageName"\n invoke-static {p1, p0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V\n const/4 p0, 0x0\n return p0\n.end method'
        preflight.check_denial_code(good, 'allowed(Ljava/lang/String;ZLjava/io/File;)Z')
        annotated = good.replace(' .registers 4', ' .annotation system Ldalvik/annotation/Signature;\n value = {"()Z"}\n .end annotation\n .registers 4')
        preflight.check_denial_code(annotated, 'allowed(Ljava/lang/String;ZLjava/io/File;)Z')
        for broken in (good.replace('0x0', '0x1'), good.replace('return p0', 'return p1'),
                       good.replace('const/4', 'if-eqz p1, :grant\n const/4'),
                       good.replace('const/4', 'invoke-virtual {p3}, Ljava/io/File;->exists()Z\n const/4'),
                       good.replace('const/4', 'return p2\n const/4'),
                       good.replace('allowed(', 'other(')):
            with self.subTest(broken=broken), self.assertRaises(ValueError):
                preflight.check_denial_code(broken, 'allowed(Ljava/lang/String;ZLjava/io/File;)Z')

    def test_product_identity_rejects_debug_isolated_and_network(self):
        good = "package: name='com.monumentogram.dora' versionCode='5' versionName='0.1.0-alpha.3'"
        preflight.check_badging(good, 5, '0.1.0-alpha.3')
        for package in ('com.monumentogram.dora.debug', 'com.monumentogram.dora.alpha.test'):
            with self.assertRaises(ValueError):
                preflight.check_badging(good.replace("dora'", package.removeprefix('com.monumentogram.') + "'"), 5, '0.1.0-alpha.3')
        for permission in ('INTERNET', 'INTERACT_ACROSS_USERS', 'MANAGE_USERS', 'READ_EXTERNAL_STORAGE'):
            with self.assertRaises(ValueError):
                preflight.check_badging(good + "\nuses-permission: name='android.permission." + permission + "'", 5, '0.1.0-alpha.3')

    def test_manifest_fails_closed_on_debug_backup_exports_and_receivers(self):
        xml = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.monumentogram.dora"><application android:allowBackup="false"><activity android:name="com.monumentogram.dora.MainActivity" android:exported="true"/><service android:name="com.monumentogram.dora.recording.ProductRecordingService" android:exported="false"/></application></manifest>'''
        preflight.check_manifest(xml)
        receiver = '<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:permission="android.permission.DUMP" android:exported="true"/>'
        preflight.check_manifest(xml.replace('</application>', receiver + '</application>'))
        with self.assertRaises(ValueError):
            preflight.check_manifest(xml.replace('</application>', receiver.replace('android:permission="android.permission.DUMP"', '') + '</application>'))
        for broken in (xml.replace('allowBackup="false"', 'allowBackup="true"'),
                       xml.replace('<application ', '<application android:debuggable="true" '),
                       xml.replace('<application ', '<application android:testOnly="true" '),
                       xml.replace('ProductRecordingService" android:exported="false"', 'ProductRecordingService" android:exported="true"'),
                       xml.replace('</application>', '<receiver android:name="Boot" android:exported="false"/></application>'),
                       xml.replace('package=', 'android:sharedUserId="shared" package=')):
            with self.subTest(broken=broken), self.assertRaises(ValueError):
                preflight.check_manifest(broken)


if __name__ == '__main__':
    unittest.main()
