from base64 import b64decode, b64encode
from hashlib import sha256
import os
import ecdsa
import json
from datetime import datetime
import sys
from urllib import request, parse
import requests
from requests import get
import serial
import time
import traceback
import uuid
import socket   

def get_current_time_millis():
    return int(round(time.time() * 1000))

def debug(s):
    print(s)
    sys.stdout.flush()

startseconds = int(get_current_time_millis() / 1000)
hostname = socket.gethostname()
#ipaddr = socket.gethostbyname(hostname)
ipaddr = get('https://api.ipify.org').text

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

def get_agent_string():
    ### TODO get actual pi system info
    return f'raspberry {hostname} {ipaddr}'

def get_uuid():
    ### TODO check if /var/media/uuid.txt exists
    ### return stored value if it does. Else
    ### create new value and store
    return f'{uuid.uuid1()}'

#def dms_to_decimals(deg, min, sec):
#    return deg + util.sign(deg) * (min / 60) + util.sign(deg) * (sec / 3600)

def send_gps_data(gps, sk):
    #debug('send_gps_data()')
    #debug(f'- gps: {gps}')
    msg = {
        'uuid': get_uuid(),
        'agent': get_agent_string(),
        'timestamp': int(get_current_time_millis() / 1000),
        'lat': gps['lat'],
        'long': gps['lon'],
        'speed': gps['speed'],
        'heading': gps['heading'],
        'accuracy': gps['accuracy'],
        'trip-id': f'trip-{startseconds}', # get actual value
        'agency-id': 'test', # don't hardcode
        'vehicle-id': 'pi-vehicle-id',# don't hardcode
        'pos-timestamp': gps['timestamp']
    }

    data = json.dumps(msg, separators=(',', ':'))
    #debug(f'- data: {data}')
    sig = sign(data, sk)

    obj = {
        'data': msg,
        'sig': sig
    }

    debug(f'- obj: {json.dumps(obj)}')

    data = json.dumps(obj, separators=(',', ':')).encode('utf-8')
    req = request.Request('https://lat-long-prototype.wl.r.appspot.com/new-pos-sig', data=data)
    req.add_header('Content-Type', 'application/json')

    try:
        resp = request.urlopen(req)
        #debug(f'- resp.code: {resp.code}')
    except:
        debug(bcolors.FAIL + '* network exception' + bcolors.STANDARD)
        return

    server_response = bcolors.FAIL + str(resp.code) + bcolors.STANDARD
    if resp.code == 200:
        server_response = bcolors.OKGREEN + 'ok' + bcolors.STANDARD
    debug(f'- server_response: {server_response}')

def send_at(ser, command, back, timeout):
    rec_buff = ''
    ser.write((command+'\r\n').encode())
    time.sleep(timeout)
    if ser.inWaiting():
        time.sleep(0.01 )
        rec_buff = ser.read(ser.inWaiting())
    if rec_buff != '':
        if back not in rec_buff.decode():
            debug(command + ' ERROR')
            debug(command + ' back:\t' + rec_buff.decode())
            return None
        else:
            return rec_buff.decode()
    else:
        debug('GPS is not ready')
        return 0

# +CGPSINFO:[<lat>],[<N/S>],[<log>],[<E/W>],[<date>],[<UTC time>],[<alt>],[<speed>],[<course>]
# 3832.682076,N,12142.488254,W,280521,203223.0,6.2,0.0,303.7
def read_gps_data(ser):
    answer = send_at(ser, 'AT+CGPSINFO','+CGPSINFO: ',1)
    lines = answer.splitlines()
    #debug(f'- lines: {lines}')
    #debug(f'- len(lines): {len(lines)}')
    #debug(f'- lines[2]: {lines[2]}')
    if len(lines) < 2 or not lines[2].startswith('+CGPSINFO: '):
        return None

    nmea = lines[2][11:]
    #debug(f'- nmea: {nmea}')

    if nmea == ',,,,,,,,':
        return None

    tok = nmea.split(',')
    #debug(f'- len(tok): {len(tok)}')
    #debug(f'- tok: {tok}')

    try:
        ts = tok[4] + ' ' + tok[5][0:6] + ' PDT'
        #debug(f'- ts: {ts}')
        dt = datetime.strptime(ts, '%d%m%y %H%M%S %Z')
        #debug(f'- dt: {dt}')
    except:
        debug(f'* failed to parse date time: {ts}')
        return None

    gps = {
        'lat': to_decimal(tok[0], tok[1]), 
        'lon': to_decimal(tok[2], tok[3]), 
        'speed': tok[7], 
        'heading': tok[8], 
        'timestamp': int(dt.timestamp()) - 7 * 60 * 60,
        'accuracy': -1
    }

    return gps

def read_file(path):
    #debug('read_file()')
    #debug(f'- path: {path}')
    with open(path, 'r') as f:
     return f.read()

def main(arg):
    hostname = os.uname()[1]
    debug(f'- hostname: {hostname}')

    ser = serial.Serial('/dev/ttyS0',115200)
    ser.flushInput()

    pem = read_file(arg[0])
    #debug(f'- pem:\n{pem}')
    sk = ecdsa.SigningKey.from_pem(pem)

    debug('enabling GPS...')
    send_at(ser, 'AT+CGPS=1,1','OK',1)
    time.sleep(2) 

    try:
        while True:
            data = read_gps_data(ser)
            if data == None:
                debug('gps not ready...')
                continue
            debug(f'({data["lat"]}, {data["lon"]})')
            send_gps_data(data, sk)
            time.sleep(2)
    finally:
        #debug(f'* exception: {sys.exc_info()[0]}')
        traceback.print_exc()
        send_at(ser, 'AT+CGPS=0','OK',1)
        if ser != None:
            ser.close()

if __name__ == '__main__':
   main(sys.argv[1:])
