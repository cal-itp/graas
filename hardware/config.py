import ecdsa # REMOVE ME
import os
import sys
import uuid

"""
standard config properties are:
- uuid
- agency_name
- agency_gtfs_id
- vehicle_id
- static_gtfs_url
"""
class Config:
    SEPARATOR = ': '

    def __init__(self, path):
        print(f'- path: {path}')
        self.map = {}

        with open(path, 'r') as f:
            while True:
                line = f.readline()
                if not line:
                    break

                line = line.strip()
                index = line.find(Config.SEPARATOR)
                #print(f'- index: {index}')
                if index > 0:
                    key = line[:index]
                    #print(f'- key: \'{key}\'')
                    value = line[index + len(Config.SEPARATOR):]
                    #print(f'- value: \'{value}\'')
                    self.map[key] = value

        if self.map.get('uuid') is None:
            #print(f'*create UUID and write config file')
            value = f'{uuid.uuid1()}'
            with open(path + 'new', 'w') as f:
                f.write(f'uuid: {value}\n')
                for key in self.map:
                    f.write(f'{key}: {self.map[key]}\n')
            os.rename(path + 'new', path)

    def get_property(self, name):
        return self.map.get(name)

if __name__ == '__main__':
    conf = Config(sys.argv[1])
    uuid = conf.get_property('uuid')
    #print(f'- uuid: \'{uuid}\'')
    agency_name = conf.get_property('agency_name')
    #print(f'- agency_name: \'{agency_name}\'')
    agency_gtfs_id = conf.get_property('agency_gtfs_id')
    #print(f'- agency_gtfs_id: \'{agency_gtfs_id}\'')
    vehicle_id = conf.get_property('vehicle_id')
    #print(f'- vehicle_id: \'{vehicle_id}\'')
    trip_id = conf.get_property('trip_id')
    #print(f'- trip_id: \'{trip_id}\'')
    static_gtfs_url = conf.get_property('static_gtfs_url')
    #print(f'- static_gtfs_url: \'{static_gtfs_url}\'')
    agency_key = conf.get_property('agency_key')
    #print(f'- agency_key: \'{agency_key}\'')

