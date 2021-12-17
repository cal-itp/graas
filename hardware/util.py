from base64 import b64decode, b64encode
from hashlib import sha256
import math
import requests
from urllib import request
import time
from datetime import datetime
import os
import sys
import random
from shapepoint import ShapePoint
from zipfile import ZipFile

EARTH_RADIUS_IN_FEET = 20902231
FEET_PER_LAT_DEGREE = 364000
FEET_PER_LONG_DEGREE = 288200
FEET_PER_MILE = 5280

debug_callback = None

# UI colors
LIGHT            = 'ffc0c0c0'
DARK             = 'ff000020'
DANGER           = 'ffa00000'
MAP_POINT        = 'ff00ff00'
MAP_POINT_MISSED = 'ffff0000'

class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    STANDARD = '\033[37m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

def to_b64(s):
    return b64encode(str(s).encode('utf-8')).decode('utf-8')

def from_b64(s):
    return b64decode(s).decode('utf-8')

def get_current_time_millis():
    return int(round(time.time() * 1000))

def get_seconds_since_midnight():
    now = datetime.now()
    return (now - now.replace(hour=0, minute=0, second=0, microsecond=0)).total_seconds()

def hhmmss_to_seconds(s):
    arr = s.split(':')
    seconds = int(arr[0]) * 60 * 60
    seconds += int(arr[1]) * 60
    seconds += int(arr[2])
    return seconds

def seconds_to_hhmmss(s):
    hours = int(s / 60 / 60)
    s -= hours * 60 * 60
    minutes = int(s / 60)
    s -= minutes * 60
    return f'{hours}:{str(minutes).zfill(2)}:{str(s).zfill(2)}'

def seconds_to_ampm_hhmm(s):
    ampm = 'am'
    hours = int(s / 60 / 60)
    s -= hours * 60 * 60

    if hours == 0:
        hours = 12
    else:
        if hours >= 12:
            ampm = 'pm'
            hours -= 12

    minutes = int(s / 60)
    s -= minutes * 60
    return f'{hours}:{str(minutes).zfill(2)} {ampm}'

def get_feet_as_lat_degrees(feet):
    return feet / FEET_PER_LAT_DEGREE

def get_feet_as_long_degrees(feet):
    return feet / FEET_PER_LONG_DEGREE

# return the Haversine distance between two lat/long
# pairs in feet
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

def get_distance_string(feet):
    if (feet < FEET_PER_MILE):
        return f'{feet} ft'
    else:
        return f'{int(feet / FEET_PER_MILE)} mi'

def set_debug_callback(cb):
    global debug_callback
    debug_callback = cb

def debug(s):
    ts = datetime.now().strftime('%H:%M:%S ')

    if debug_callback is None:
        print(ts + s)
        sys.stdout.flush()
    else:
        debug_callback(s)

def error(s):
    print(f'* {s}')
    sys.stdout.flush()

def to_decimal(v, dir):
    #debug('to_decimal()')
    #debug(f'- v: {v}')
    slen = len(v)
    #debug(f'- slen: {slen}')
    min = v[-9:]
    #debug(f'- min: {min}')
    deg = v[0:len(v) - len(min)]
    #debug(f'- deg: {deg}')
    dec = int(deg) + float(min) / 60
    #debug(f'- dec: {dec}')

    mul = 1
    if dir == 'S' or dir == 'W':
        mul = -1

    return dec * mul

def get_random_int(low, high):
    return int(low + (random.random() * (high - low)))

def get_random_point(area):
    lat_delta = area.bottom_right.lat - area.top_left.lat
    lon_delta = area.bottom_right.lon - area.top_left.lon

    return ShapePoint(
        area.top_left.lat + (random.random() * lat_delta),
        area.top_left.lon + (random.random() * lon_delta)
    )

def sign(str, sk):
    #debug('elliptic_curve.sign()')
    #debug(f'- str: {str}')
    #debug(f'- sk: {sk}')
    try:
        sig = sk.sign(str.encode('utf-8'), hashfunc=sha256)
        #debug(f'- sig: {sig}')
        return b64encode(sig).decode('utf-8')
    except:
        debug(f'*** signature failure: {sys.exc_info()[0]}')
        return None

def read_file(path):
    #debug('read_file()')
    #debug(f'- path: {path}')
    with open(path, 'r') as f:
        return f.read()

def update_cache_if_needed(cache_path, url):
    debug(f'update_cache_if_needed()')
    debug(f'- cache_path: {cache_path}')
    debug(f'- url: {url}')

    if not os.path.isdir(cache_path):
        os.makedirs(cache_path)

    r = requests.head(url)
    url_time = r.headers.get('last-modified', None)
    debug(f'- url_time: {url_time}')

    if url_time is None:
        debug(f'* can\'t access static GTFS URL {url}, aborting cache update')
        return

    url_date = datetime.strptime(url_time, '%a, %d %b %Y %H:%M:%S %Z')
    file_date = datetime.fromtimestamp(0, url_date.tzinfo)
    file_name = cache_path + 'gtfs.zip'
    if os.path.exists(file_name):
        file_date = datetime.fromtimestamp(os.path.getmtime(file_name))

    if datetime.timestamp(url_date) <= datetime.timestamp(file_date):
        debug('+ gtfs.zip up-to-date, nothing to do')
        return

    debug('+ gtfs.zip out of date, downloading...')
    req = request.Request(url)
    resp = request.urlopen(req)
    debug(f'- resp.code: {resp.code}')
    content = resp.read()

    with open(file_name, 'wb') as f:
        f.write(content)
        f.close()

    debug('+ gtfs.zip downloaded')
    gtfs_zip = ZipFile(file_name)
    names = ['calendar.txt', 'routes.txt', 'stops.txt', 'stop_times.txt', 'shapes.txt', 'trips.txt']
    for n in names:
        debug(f'-- zip entry: {n}')
        ze = gtfs_zip.open(n)
        content = ze.read().decode('utf-8')

        with open(cache_path + n, 'w') as ff:
            ff.write(content)
            ff.close()

