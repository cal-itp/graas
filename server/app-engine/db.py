"""
IMPORTANT NOTE: this a terrible DB simulation meant for only one thing - to let developers get
a first impression of the system without having to set up an actual database. The implementation
is naive, inefficient, incomplete and may well lose all data you add to it. You have been warned.

to do:
- util.py
  + replace 'from google.cloud import datastore' with 'import from db import db'
  + 'datastore_client = datastore.Client()' -> 'datastore_client = db.Client()'
"""
import os
import random
import json
import shutil
import sys

_DB_CLOUD_MODE = 'cloud'
_DB_LOCAL_MODE = 'local'
_DB_FILE     = 'db-sim.json'
_DB_FILE_BAK = 'db-sim.json.bak'

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
                        self.entities[e['id']] = e

    def query(self, kind):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.query(kind)
        else:
            return Query(kind, self)

    def get(self, key):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.get(key)
        else:
            return self.entities.get(str(key), None)

    def _write(self):
        shutil.copyfile(_DB_FILE, _DB_FILE_BAK)
        with open(_DB_FILE, 'w') as f:
            for e in self.entities.values():
                e.write(f)

    def put(self, entity, flush=True):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.put(entity)
        else:
            self.entities[str(entity['key'])] = entity

        if flush:
            self._write()

    def put_multi(self, entity_list):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.put_multi(entity_list)
        else:
            for e in entity_list:
                self.put(e, False)

        self._write()

    def delete(self, key, flush=True):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.delete(key)

        if flush:
            self._write()

    def delete_multi(self, key_list):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.delete_multi(key_list)
        else:
            for k in key_list:
                self.delete(k, False)

        self._write()

    def key(self, kind):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.key(kind)
        else:
            return Key(kind)

    def entity(self, key):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.entity(key)
        else:
            return Entity(key)

class Entity(dict):
    def __init__(self, arg):
        if isinstance(arg, Key):
            self['kind'] = arg.kind
            self['id'] = arg.id
        elif isinstance(arg, str):
            obj = json.parse(arg)
            for key in obj.keys():
                self[key] = obj[key]
        else:
            raise ValueError('valid arg types are Key and str')

    def write(self, f):
        f.write(json.dumps(self))

    def __getitem__(self, key):
        val = dict.__getitem__(self, key)
        #print('GET', key)
        return val

    def __setitem__(self, key, val):
        #print('SET', key, val)
        dict.__setitem__(self, key, val)

    def __repr__(self):
        dictrepr = dict.__repr__(self)
        return '%s(%s)' % (type(self).__name__, dictrepr)

    def update(self, *args, **kwargs):
        #print('update', args, kwargs)
        for k, v in dict(*args, **kwargs).iteritems():
            self[k] = v

class Key:
    def __init__(self, kind):
        self.kind = kind
        self.id = hex(random.getrandbits(128))[2:-1]

    def __str__ (self):
        return f'{self.kind}@{self.id}'

class Query:
    """
    order (property): list of order attributes, '+' for ascending by default, or '-' for descending
    """

    def __init__(self, kind, client):
        self.kind = kind
        self.client = client
        self.order = []
        self.filters = []

    def fetch(self, limit):
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

        self.ret = []

        for e in self.client.entities:
            if len(self.ret) >= limit:
                break

            matched = True

            for f in self.filters:
                if not _matches(e, f):
                    matched = False
                    break

            if matched:
                self.ret.append(e)

        # sort by self.order

        return self.ret

    def add_filter(self, prop, op, v):
        """
        e.g. add_filter('time_stop', '<', seconds):
        # property, operand, value
        """
        self.filters.append((prop, op, v))


e = Entity(Key('position'))
e['foo'] = 'bar'

print(f'e: {e}')
print(f'json.dumps(e): {json.dumps(e)}')


