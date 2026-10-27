"""IaC compatibility must preserve whole-bucket retention admission."""
import unittest

from dora_cloud.preflight import verify_live
from dora_cloud.aws_adapter import ProviderError
from test_preflight import ReadOnlyAWS


class LifecycleRepresentations(unittest.TestCase):
    def check_scope(self, scope):
        aws = ReadOnlyAWS()
        def lifecycle(**kwargs):
            return {"Rules": [{"Status": "Enabled", "Expiration": {"Days": 1},
                              "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}, **scope}]}
        aws.get_bucket_lifecycle_configuration = lifecycle
        return verify_live(aws.cfg, aws, aws, aws, aws)

    def test_all_object_api_representations_are_equivalent(self):
        for scope in ({"Filter": {"Prefix": ""}}, {"Filter": {}}, {"Prefix": ""}):
            with self.subTest(scope=scope):
                self.assertEqual("VERIFIED", self.check_scope(scope)["status"])

    def test_scoped_size_tag_and_conflicting_filters_stay_blocked(self):
        for scope in ({}, {"Prefix": "input/"}, {"Filter": {"Prefix": "input/"}},
                      {"Filter": {"ObjectSizeGreaterThan": 0}},
                      {"Filter": {"Tag": {"Key": "a", "Value": "b"}}},
                      {"Filter": {}, "Prefix": "input/"}):
            with self.subTest(scope=scope), self.assertRaises(ProviderError):
                self.check_scope(scope)
