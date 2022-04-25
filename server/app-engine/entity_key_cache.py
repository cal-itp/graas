from cache import Cache
import time

class EntityKeyCache(Cache):
    def add(self, name, value):
        #print(f'EntityKeyCache.add()')
        #print(f'- name: {name}')
        #print(f'- value: {value}')

        entry = self.map.get(name, None)
        #print(f'- entry: {entry}')

        if entry is not None and entry['state'] == 'expired-preliminary':
            entry['value'] = value
            entry['expires'] = 0
            entry['last-access'] = int(time.time())
            entry['state'] = 'permanent'
        else:
            self.map[name] = {
                'value': value,
                'expires': int(time.time()) + 10,
                'last-access': int(time.time()),
                'state': 'preliminary'
            };

        #print(f'. entry: {entry}')

    def get(self, name):
        #print(f'EntityKeyCache.get()')
        #print(f'- name: {name}')

        entry = self.map.get(name, None)
        #print(f'- entry: {entry}')

        if entry is None:
            return None

        expiration = entry['expires']

        if expiration > 0 and expiration < int(time.time()):
            if entry['state'] == 'permanent':
                self.remove(name)
            else:
                entry['state'] = 'expired-preliminary'
            return None

        entry['last-access'] = int(time.time())
        return entry['value']
