from cache import Cache

class EntityKeyCache(Cache):
    def add(self, name, value):
        entry = self.map.get(name, None)

        if entry is not None and entry['state'] == 'expired-preliminary':
            entry['value'] = value,
            entry['expires'] = 0,
            entry['last-access'] =  int(time.time())
            entry['state'] = 'permanent'
        else:
            self.map[name] = {
                'value': value,
                'expires': int(time.time()) + 10,
                'last-access': int(time.time()),
                'state': 'preliminary'
            };

    def get(self, name):
        entry = self.map.get(name, None)

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
