
import time
import sys

class Cache:
"""

Todo:
    * `getSize()` method that returns size of cache in bytes

  + general cache
    . add item with name and expiration
    . retrieve item (if expired, return None and remove from cache)
    . separate module?
    . get overall cache size in bytes
    . get number of cache entries
    . strategies: grow forever, when above limit delete items (oldest to newest)

cache entry:
- name
- value
- expiration timestamp
"""

    def __init__(self):
        """ Create empty cache instance.

        """
        self.map = {}

    def add(self, name, value, seconds):
        self.map[name] = {
            'value': value,
            'expires': int(time.time()) + seconds,
            'last-access': int(time.time())
        };

    def remove(self, name):
        self.map.pop(name, None)

    def clear(self):
        self.map.clear()

    def get(self, name):
        entry = self.map.get(name, None)

        if entry is None:
            return None

        if entry['expires'] < int(time.time()):
            self.remove(name)
            return None

        entry['last-access'] = int(time.time())
        return entry['value']

    def count(self):
        return len(self.map)

    def memory(self):
        """
        """
        return util.memory(self.map)
