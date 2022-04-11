
from google.cloud import datastore

class LazyTimers:
"""
Note that timers don't get restarted until after they are checked for expiration.

queue entry:
- name
- expiration timestamp

recurring-alert entity:
- name
- periodicity
"""

    def __init__(self, datastore):
        """ Initialize instance with `recurring-alert` entities from DB

        """
        self.queue = []

    def __resetTimer(self, name, seconds):
        """ Reset an existing timer or create a new one.

        Args:
            name (str): a timer name.
            seconds (int): nnumber od seconds before timer expires.

        # REMOVE ME
        - create time stamp from current time and second offset
        - iterate over queue and remove existing timer for name if present
        - insert timer so that prior elements have lower timestamp and following elements have higher timestamp
        """

    def isExpired(self, name):
        """Check if timer `name` has expired. Restart if it has.

        Args:
            name (str): a timer name.

        Returns:
            bool: `True` if expired, `False` otherwise.

        # REMOVE ME
        - check if timer exists, return `False` if not
        - if found, check if timestamp is <= current time
        - if not, return `False`
        - else call __resetTimer() and retur `True`

        """
