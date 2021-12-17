import csv
import math
import sys
from datetime import datetime

EARTH_RADIUS_IN_FEET = 20902231;
def haversine_distance(lat1, lon1, lat2, lon2):
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lam = math.radians(lon2 - lon1)

    a = (math.sin(delta_phi / 2) * math.sin(delta_phi / 2)
        + math.cos(phi1) * math.cos(phi2)
        * math.sin(delta_lam / 2) * math.sin(delta_lam / 2))
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    return EARTH_RADIUS_IN_FEET * c

def hms(ts):
    date = datetime.fromtimestamp(ts)
    return f'{date.strftime("%H:%M:%S")}'

def main(argv):
    if len(argv) < 1:
        print('usage: check-trip <file-name>')
        return

    f = open(argv[0])
    reader = csv.reader(f)
    rows = list(reader)

    last_ts = 0
    last_lat = float(rows[0][2])
    last_lon = float(rows[0][3])

    for r in rows:
        ts = int(r[1])
        lat = float(r[2])
        lon = float(r[3])

        d = int(haversine_distance(last_lat, last_lon, lat, lon))
        if abs(ts - last_ts) > 5 or haversine_distance(last_lat, last_lon, lat, lon) > 1500:
            print(f'{hms(ts)} {d}')

        last_ts = ts
        last_lat = lat
        last_lon = lon

if __name__ == '__main__':
   main(sys.argv[1:])
