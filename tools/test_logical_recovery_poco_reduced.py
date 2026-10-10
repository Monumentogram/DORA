"""CI bridge for reduced POCO evidence validators; synthetic inputs are not physical proof."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).parent / 'poco_non_battery'))


def load_tests(loader, tests, pattern):
    directory = str(Path(__file__).parent / 'poco_non_battery')
    return unittest.TestLoader().discover(directory, pattern='test_*.py', top_level_dir=directory)


if __name__ == '__main__':
    unittest.main()
