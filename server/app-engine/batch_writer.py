
import time

class BatchWriter:
    """
    Batch writes to google cloud datastore for better efficiency

    Note: this module should not be used to batch writes for critical data such as service alerts,
    as there is no guarantee for when data will be flushed to DB. For entities such as `positions`,
    `current_position` and `stop_time` this should not be an issue, as updates will come in every few
    seconds up until the end of a trip. This means that the last update of a trip may or may not be
    flushed in a timely fashion, or ever. In practice, this shouldn't be a problem though.
    """

    def __init__(self, datastore_client, max_time_delta, max_queue_size):
        self.datastore_client = datastore_client
        self.max_time_delta = max_time_delta
        self.max_queue_size = max_queue_size
        self.last_write = time.time()
        self.map = {}

    """
    Queue an entity for writing to DB. See class commment for important caveat.

    Args:
        entity (datastore entity): the data to queue for writing to the DB
        key (str): a key for identifying entities of the same "type". When
           multiple entities with the same key are queued in between flushes,
           newer versions overwrite older versions, e.g. of two updates with
           key `position-agencyfoo-vehicle123` only the last one persists
    """
    def add(self, entity, key):
        if key is None:
            print(f'* missing key arg for BatchWriter.add(), discarding: {entity}')
            return

        print(f'- key: {key}')

        self.map[key] = entity

        if time.time() - self.last_write > self.max_time_delta or len(self.map) > self.max_queue_size:
            self.flush()

    def flush(self):
        if len(self.map) == 0 or time.time() - self.last_write <= self.max_time_delta:
            return len(self.map) == 0

        self.datastore_client.put_multi(list(self.map.values()))
        self.map = {}
        self.last_write = time.time()

        return True
