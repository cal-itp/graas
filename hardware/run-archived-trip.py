import inference
import sys
import time
import util
from area import Area
from shapepoint import ShapePoint

def main(data_file, cache_folder, static_gtfs_url):
    util.debug(f'main()')
    util.debug(f'- data_file: {data_file}')
    util.debug(f'- cache_folder: {cache_folder}')
    util.debug(f'- static_gtfs_url: {static_gtfs_url}')

    inf = inference.TripInference(cache_folder, static_gtfs_url, 15)
    util.debug(f'- inference.TripInference.VERSION: {inference.TripInference.VERSION}')

    with open(data_file, 'r') as f:
        for line in f:
            line = line.strip()

            #print(f'- line: {line}')
            tok = line.split(',')
            seconds = int(tok[0])
            lat = float(tok[1])
            lon = float(tok[2])
            grid_index = inf.grid.get_index(lat, lon)
            util.debug(f'current location: lat={lat} long={lon} grid_index={grid_index}')
            trip_id = inf.get_trip_id(lat, lon, seconds)
            print(f'- trip_id: {trip_id}')

def usage():
    print(f'usage: {sys.argv[0]} -d|--data-file <data-file> -c|--cache-foler <cache-folder> -u|--static-gtfs-url <static-gtfs-url>')
    exit(1)

if __name__ == '__main__':
    data_file = None
    cache_folder = None
    static_gtfs_url = None
    i = 0

    while True:
        i += 1
        if i >= len(sys.argv):
            break

        arg = sys.argv[i]

        if (arg == '-d' or arg == '--data-file') and i < len(sys.argv) - 1:
            data_file = sys.argv[i + 1]
            i += 1
            continue

        if (arg == '-c' or arg == '--cache-folder') and i < len(sys.argv) - 1:
            cache_folder = sys.argv[i + 1]
            i += 1
            continue

        if (arg == '-u' or arg == '--static-gtfs-url') and i < len(sys.argv) - 1:
            static_gtfs_url = sys.argv[i + 1]
            i += 1
            continue

        usage()

    if data_file is None or cache_folder is None or static_gtfs_url is None:
        usage()

    main(data_file, cache_folder, static_gtfs_url)