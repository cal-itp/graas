"""
IMPORTANT NOTE: this a terrible DB simulation meant for only one thing - to let developers get
a first impression of the system without having to set up an actual database. The implementation
is naive, inefficient, incomplete and may well lose all data you add to it. You have been warned.

to do:
- util.py
  + replace 'from google.cloud import datastore' with 'import from db import db'
  + 'datastore_client = datastore.Client()' -> 'datastore_client = db.Client()'
"""
import operator
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
    _db_mode = _DB_LOCAL_MODE

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

            print(f'- self.entities: {self.entities}')

    def query(self, kind):
        if _db_mode == _DB_CLOUD_MODE:
            #print(f'- kind: {kind}')
            #print(f'- self._cloud_client: {self._cloud_client}')
            return self._cloud_client.query(kind = kind)
        else:
            return Query(kind, self)

    def get(self, key):
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.get(key)
        else:
            return self.entities.get(str(key), None)

    def _write(self):
        if os.path.exists(_DB_FILE):
            shutil.copyfile(_DB_FILE, _DB_FILE_BAK)
        with open(_DB_FILE, 'w') as f:
            for e in self.entities.values():
                e.write(f)

    def put(self, entity, flush=True):
        print(f'Client.put()')
        print(f'- entity: {entity}')
        if _db_mode == _DB_CLOUD_MODE:
            return self._cloud_client.put(entity)
        else:
            self.entities[str(entity['id'])] = entity

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
            print(f'- arg: {arg}')
            obj = json.loads(arg)
            print(f'- obj: {obj}')
            for key in obj.keys():
                self[key] = obj[key]
        else:
            raise ValueError('valid arg types are Key and str')

    def write(self, f):
        f.write(json.dumps(self) + '\n')

    def __getitem__(self, key):
        #print(f'Entity.__getitem__()')
        #print(f'- key: {key}')
        val = dict.__getitem__(self, key)
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
        self.id = hex(random.getrandbits(128))[2:]

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

    def _multisort(self, xs, specs):
        for key, reverse in reversed(specs):
            xs.sort(key=attrgetter(key), reverse=reverse)
        return xs

    def _matches(self, entity, filter):
        v = entity[filter['field']]

        if not isinstance(v, (int, long, float, str)):
            raise ValueError(f'entity type error: {type(v)}')

        if not isinstance(filter['value'], (int, long, float, str)):
            raise ValueError(f'filter type error: {type(filter["value"])}')

        if field['operand'] == '=':
            return v == filter['value']
        elif field['operand'] == '<':
            return v < filter['value']
        else:
            raise ValueError(f'unsupported operand: {field["operand"]}')

    def fetch(self, limit=sys.maxsize):
        self.ret = []

        for e in self.client.entities.values():
            if len(self.ret) >= limit:
                break

            if not self.kind == e['kind']:
                continue

            matched = True

            for f in self.filters:
                if not _matches(e, f):
                    matched = False
                    break

            if matched:
                self.ret.append(e)

        args = []
        for o in self.order:
            reverse = False

            if o.startswith('-'):
                o = o[1: -1]
                reverse = True

            if o.startswith('+'):
                o = o[1: -1]

            args.append((o, reverse))

        print(f'- self.ret: {self.ret}')
        print(f'- args: {args}')
        print(f'- tuple(args): {tuple(args)}')
        self.ret = self._multisort(self.ret, tuple(args))

        return self.ret

    def add_filter(self, field, operand, value):
        self.filters.append({'field': field, 'operand': operand, 'value': value})
