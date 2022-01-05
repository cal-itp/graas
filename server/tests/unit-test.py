import sys
import unittest

sys.path.append("server/app-engine")
import util

class TestUtil(unittest.TestCase):

    def test_current_time(self):
        self.assertTrue(util.get_current_time_millis())

    def test_public_key(self):
        self.assertTrue(util.key_map.get("pr-test"))

if __name__ == '__main__':
    unittest.main()