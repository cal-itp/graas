"""
IMPORTANT NOTE: this a terrible DB simulation meant for only one thing - to let developers get
a first impression of the system without having to set up an actual database. The implementation
is naive, inefficient, incomplete and may well lose all data you add to it. You have been warned.
"""
import operator
import os
import random
import json
import shutil
import sys

_DB_FILE     = 'db-sim.json'
_DB_FILE_BAK = 'db-sim.json.bak'

def serialize(obj):
    #print('serialize()')
    return obj.__dict__

class Client:
    def __init__(self):
        self.entities = {}

        if os.path.exists(_DB_FILE):
            with open(_DB_FILE) as f:
                for line in f:
                    line = line.rstrip()
                    #print(f'-- line: {line}')
                    e = Entity(line=line)
                    self.entities[str(e['key'])] = e

        #print(f'- self.entities: {self.entities}')

    def query(self, kind):
        return Query(kind, self)

    def get(self, key):
        return self.entities.get(str(key), None)

    def _write(self):
        if os.path.exists(_DB_FILE):
            shutil.copyfile(_DB_FILE, _DB_FILE_BAK)
        with open(_DB_FILE, 'w') as f:
            for e in self.entities.values():
                e.write(f)

    def put(self, entity, flush=True):
        #print(f'Client.put()')
        #print(f'- entity: {entity}')
        self.entities[str(entity.key)] = entity

        if flush:
            self._write()

    def put_multi(self, entity_list):
        for e in entity_list:
            self.put(e, False)

        self._write()

    def delete(self, key, flush=True):
        self.entities.pop(key)

        if flush:
            self._write()

    def delete_multi(self, key_list):
        for k in key_list:
            self.delete(k, False)

        self._write()

    def key(self, kind):
        return Key(kind)

    def entity(self, key=None, line=None, exclude_from_indexes=None):
        return Entity(key)

class Entity(dict):
    @property
    def key(self):
        return self['key']

    def __init__(self, key=None, line=None, exclude_from_indexes=None):
        # ignore 'exclude_from_indexes' in simulator
        if key is not None:
            self['key'] = key
        elif line is not None:
            #print(f'- line: {line}')
            obj = json.loads(line)
            #print(f'- obj: {obj}')
            for k in obj.keys():
                self[k] = obj[k]

                if hasattr(obj[k], '__iter__') and 'kind' in obj[k] and 'id' in obj[k]:
                    self[k] = Key(kind=obj[k]['kind'], id=obj[k]['id'])
        else:
            raise ValueError('either \'key\' or \'line\' must be given')

    def write(self, f):
        #print(f'write()')
        #print(f'- self: {self}')
        f.write(json.dumps(self, default=serialize) + '\n')

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
        for k, v in dict(*args, **kwargs).items():
            self[k] = v

class Key:
    def __init__(self, kind, id=None):
        self.kind = kind
        self.id = hex(random.getrandbits(128))[2:] if id is None else id

    def __repr__ (self):
        return f'{self.kind}@{self.id}'

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
        #print(f'_multisort()')
        #print(f'- xs: {xs}')
        #print(f'- specs: {specs}')
        for key, reverse in reversed(specs):
            #print(f'-- key: {key}')
            #print(f'-- reverse: {reverse}')
            xs.sort(key=operator.itemgetter(key), reverse=reverse)
        return xs

    def _matches(self, entity, filter):
        #print(f'_matches()')
        #print(f'- entity: {entity}')
        #print(f'- filter: {filter}')
        if not filter['field'] in entity:
            return False
        v = entity[filter['field']]

        if not isinstance(v, (int, float, str)):
            raise ValueError(f'entity type error: {type(v)}')

        if not isinstance(filter['value'], (int, float, str)):
            raise ValueError(f'filter type error: {type(filter["value"])}')

        # just the ops we need for GRaaS for now
        if filter['operand'] == '=':
            return v == filter['value']
        elif filter['operand'] == '<':
            return v < filter['value']
        else:
            raise ValueError(f'unsupported operand: {filter["operand"]}')

    def fetch(self, limit=sys.maxsize):
        self.ret = []

        for e in self.client.entities.values():
            if len(self.ret) >= limit:
                break

            if not self.kind == e['key'].kind:
                continue

            matched = True

            for f in self.filters:
                if not self._matches(e, f):
                    matched = False
                    break

            if matched:
                self.ret.append(e)

        args = []
        for o in self.order:
            reverse = False

            if o.startswith('-'):
                o = o[1:]
                reverse = True

            if o.startswith('+'):
                o = o[1:]

            args.append((o, reverse))

        #print(f'- self.ret: {self.ret}')
        #print(f'- args: {args}')
        #print(f'- tuple(args): {tuple(args)}')
        self.ret = self._multisort(self.ret, tuple(args))
        #print(f'- self.ret: {self.ret}')

        return self.ret

    def add_filter(self, field, operand, value):
        self.filters.append({'field': field, 'operand': operand, 'value': value})
