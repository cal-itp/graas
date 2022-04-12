
from google.cloud import datastore

class LazyTimers:
"""
Module administrates a list of recurring timers. Timers are initialized to 'recurring-timer'
entities read from DB, where each entry has a `name` and a `millis` periodicity.

Note:
    timers don't get restarted until after they are checked for expiration.
"""

    def __init__(self, datastore_client):
        """ Initialize instance with `recurring-alert` entities from DB

        """
        self.queue = []

        query = datastore_client.query(kind="recurring-timer")
        results = list(query.fetch())

        for r in results:
            self.__resetTimer(r['name'], r['millis'])

    def __resetTimer(self, name, millis):
        """ Reset an existing timer or create a new one.

        Args:
            name (str): a timer name.
            millis (int): number of milliseconds before timer expires.
        """

        for i in range(len(self.queue)):
            item = self.queue[i]

            if item['name'] == name:
                self.queue.pop(i)
                break

        newItem = {
            'name': name,
            'timestamp': int(time.time() * 1000) + millis
        };

        index = len(self.queue)

        for i in range(index):
            item = self.queue[i]

            if newItem['timestamp'] > item['timestamp']:
                index = i
                break

        self.queue.insert(index, newItem)

    def isExpired(self, name):
        """Check if timer `name` has expired. Restart if it has.

        Args:
            name (str): a timer name.

        Returns:
            bool: `True` if expired or not found, `False` otherwise.

        """

        for i in range(index):
            item = self.queue[i]

            if name == item['name']:
                millis = int(time.time() * 1000) + millis

                if millis > item['timestamp']:
                    self.queue.pop(i)
                    return True

                return False

        return True
