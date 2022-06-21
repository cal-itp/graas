"""
datastore interface:
Client():
- query(kind='<kind>') # returns Query instance
- get(<key>)
- put(<entity>)
- put_multi(<list of entities>)
- delete(<key>)
- delete_multi(<list of keys>)
- key('alert')) # datastore.Key
- Entity(key=<key>)

Query():
- fetch(limit=<n>) # returns Iterator
- add_filter('time_stop', '<', seconds) # property, operand, value
- order # property: list of order attributes, '+' for ascending by default, or '-' for descending


- util.py
  + replace 'from google.cloud import datastore' with 'import from db import db'
  + 'datastore_client = datastore.Client()' -> 'datastore_client = db.Client()'
  + query = datastore_client.query(kind="agency")
    results = list(query.fetch())
"""
import os
import random
import json
import sys

_DB_CLOUD_MODE = 'cloud'
_DB_LOCAL_MODE = 'local'
_DB_FILE = 'db-sim.json'

_db_mode = _DB_CLOUD_MODE

try:
    from google.cloud import datastore
except ImportError:
    _db_mode = _DB_CLOUD_MODE

class Client:
    def __init__(self):
        if _db_mode == _DB_CLOUD_MODE:
            self._cloud_client = datastore.Client()
        else:
            self.entities = {}

            if os.path.exists(_DB_FILE):
                with open(_DB_FILE) as f:
                    for line in f:
                        line = line.rstrip()
                        print(f'-- line: {line}')
                        e = Entity(line)
                        self.entities[e['key']] = e

    def query(kind):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.query(kind)
        else:
            return Query(kind, self)

    def get(key):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.get(key)
        else:
            return self.entities.get(str(key), None)

    def put(entity):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.put(entity)
        else:
            self.entities[str(entity['key'])] = entity

    def put_multi(entity_list):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.put_multi(entity_list)

    def delete(key):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.delete(key)

    def delete_multi(key_list):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.delete_multi(key_list)

    def key(kind):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.key(kind)
        else:
            return Key(kind)

    def entity(key):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.entity(key)
        else:
            return Entity(key)

class Entity(dict):
    def __init__(self, key):
        self['key'] = str(key)

    #def __str__ (self):
    #    return f'[entity: key={self.key} {super.__str__(self)}]'

    def read(f):
        pass

    def write(f):
        pass


class Key:
    def __init__(self, kind):
        self.kind = kind
        self.id = hex(random.getrandbits(128))

    def __str__ (self):
        return f'{self.kind}@{self.id}'

class Query:
    """
    order (property): list of order attributes, '+' for ascending by default, or '-' for descending
    """

    def __init__(self, kind, client):
        self.kind = kind
        self.client = client
        self.order = None
        self.filters = []

    def fetch(limit):
        """
        limit: max number of results
        returns Iterator

        - results = []
        - iterate over db file entries
        -- if kind == self.kind and entry matches filters, add to results
        - if orders is not None:
        -     results.sort(order)
        - return iter(results)
        """
        pass

    def add_filter(prop, op, v):
        """
        e.g. add_filter('time_stop', '<', seconds):
        # property, operand, value
        """
        self.filters.append((prop, op, v))


e = Entity(Key('position'))
e['foo'] = 'bar'

print(f'e: {e}')
print(f'json.dumps(e): {json.dumps(e)}')


