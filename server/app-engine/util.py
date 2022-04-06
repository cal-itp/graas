"""Various utility functions, centered mostly around cryptography and date/time.

"""

from base64 import b64decode
from Crypto.PublicKey import RSA
from Crypto.Signature import PKCS1_v1_5
from Crypto.Hash import SHA256
from hashlib import sha256
import ecdsa
import os.path
import sys
from datetime import datetime
import time
from google.cloud import datastore
from google.cloud import storage
import pytz

datastore_client = datastore.Client()
last_bucket_check = 0
last_key_refresh = 0
ONE_MINUTE_MILLIS = 60 * 1000  # 1 minute in milliseconds
PACIFIC_TZ = pytz.timezone("America/Los_Angeles")

# Pull agency IDs & public keys from datastore, save them
def read_public_keys():
    """Read public keys for agencies from DB and return as dictionary that maps IDs to keys.

    Returns:
        dict: Dictionary that maps IDs to keys.

    """
    print('reading agency IDs and public keys from DB:')
    query = datastore_client.query(kind="agency")
    results = list(query.fetch())
    key_map = {}

    for agency in results:
        print('-- id: ' + str(agency['agency-id']))
        print('-- key: ' + str(agency['public-key']))
        key_map[agency['agency-id']] = agency['public-key']

    return key_map

# TODO: Consider moving this call to read_public_keys(). Currently it runs whenever util.py is loaded
key_map = read_public_keys()

def verify_signature(agency_id, data, signature):
    """Verify the signature of some data. Signature is assumed to be in ECDSA-256 format

    Args:
        agency_id (str): Agency ID, used to retrieve public key for agency.
        data (str): The data to be verified.
        signature (str): The signature to use for verification.

    Returns:
        bool: True if signature could be verified, False otherwise.

    """

    global last_key_refresh, last_bucket_check, key_map
    print('verify_signature()')
    print('- agency_id: ' + agency_id)
    print('- data: ' + str(data))
    key = key_map.get(agency_id, None)
    print('- key: ' + str(key))

    if not key:
        print('no public key for agency ID: ' + str(agency_id))
        now = get_current_time_millis()
        print(f'Now: {now}')
        print(f'Last time bucket was checked: {last_bucket_check}')

        # If we haven't checked the bucket for a new timestamp in the last minute, check now
        if (now - last_bucket_check > ONE_MINUTE_MILLIS):
            last_bucket_check = now
            last_key_update = get_bucket_timestamp()
            print(f'Last time public key was updated: {last_key_update}')
            print(f'Last time public keys were refreshed: {last_key_refresh}')

            # If a key update has happened since the last refresh, and we haven't refreshed in the last minute, reload keys and then continue with verification
            if (last_key_update > last_key_refresh and now - last_key_refresh > ONE_MINUTE_MILLIS):
                last_key_refresh = now
                print('Re-running read_public_keys()...')
                key_map = read_public_keys()
                key = key_map.get(agency_id, None)
                if not key:
                    # If there IS a key in key_map for agency_id, verify_signature will continue with verification
                    return False
            else:
                return False
        else:
            return False

    print('- signature: ' + str(signature))

    if len(key) < 150:
        try:
            vk = ecdsa.VerifyingKey.from_der(b64decode(key), sha256)
            verified = vk.verify(b64decode(signature), data.encode('utf-8'))
            print('- ECDSA verified: ' + str(verified))
            return verified
        except:
            print('* ECDSA verification failure: ' + str(sys.exc_info()[0]))
            return False
    else:
        try:
            rsakey = RSA.importKey(b64decode(key))
            signer = PKCS1_v1_5.new(rsakey)
            digest = SHA256.new()
            digest.update(data.encode('utf-8'))
            verified = signer.verify(digest, b64decode(signature))
            print('- RSA verified: ' + str(verified))
            return verified
        except:
            print('* RSA verification failure: ' + str(sys.exc_info()[0]))
            return False

def get_current_time_millis():
    """Get the number of milliseconds since the epoch.

    Returns:
        int: The number of milliseonds since the epoch.

    """
    now = datetime.now()
    now = PACIFIC_TZ.localize(now)
    return int(round(time.time() * 1000))

def get_epoch_seconds(date_string = None):
    """Get the number of seconds since the epoch for a date string. If date string is omitted, the current date is assumed.

    Args:
        date_string (str): a date in the form of `yyyy-mm-dd`.

    Returns:
        int: The number of seconds since the epoch for a date string.

    """
    if date_string is None:
        now = datetime.now()
        now = PACIFIC_TZ.localize(now)
        print(f'+ ts: {int(now.timestamp())}')
        return int(now.timestamp())
    else:
        d = datetime.strptime(date_string, '%Y-%m-%d')
        d = PACIFIC_TZ.localize(d)
        print(f'+ ts: {int(d.timestamp())}')
        return int(d.timestamp())

def get_midnight_seconds(epoch_seconds):
    """Get the number of seconds at midnight of the provided timestamp.

    Args:
        epoch_seconds (int): a timestamp given in number of seconds since the epoch.

    Returns:
        int: The number of seconds at midnight of the provided timestamp.

    """
    now = datetime.fromtimestamp(epoch_seconds)
    now = PACIFIC_TZ.localize(now)
    print(f'. ts: {int(now.replace(hour=0, minute=0, second=0, microsecond=0).timestamp())}')
    return int(now.replace(hour=0, minute=0, second=0, microsecond=0).timestamp())

def get_seconds_since_midnight(seconds = None):
    """Get the number of seconds since midnight of the provided timestamp.

    Args:
        seconds (int): a timestamp given in number of seconds since the epoch. If omitted, the current timestamp is assumed.

    Returns:
        int: The number of seconds since midnight of the provided timestamp.

    """
    if seconds is None:
        now = datetime.now()
    else:
        now = datetime.fromtimestamp(seconds)

    now = PACIFIC_TZ.localize(now)
    return int((now - now.replace(hour=0, minute=0, second=0, microsecond=0)).total_seconds())

def get_yyyymmdd(date = None):
    """Get a string representation of the given date in the form `yyyy-mm-dd`.

    Args:
        date (datetime): a datetime object for which to generate a date string. If omitted, the current date and time is assumed.

    Returns:
        int: The number of seconds since midnight of the provided timestamp.

    """
    if date is None:
        date = datetime.now()

    date = PACIFIC_TZ.localize(date)
    return f'{date.year}-{date.month}-{date.day}'


# For new instances of GRaaS, replace 'graas-resources' with a globally unique directory name in the below two functions:
def update_bucket_timestamp():
    client = storage.Client()
    bucket = client.get_bucket('graas-resources')
    blob = bucket.blob('server/last_public_key_update.txt')
    # Could use util.get_current_time_millis() but it would create circular dependency
    now = get_current_time_millis()
    blob.upload_from_string(str(now))
    print(f'Latest public key update is now {now}')

def get_bucket_timestamp():
    client = storage.Client()
    bucket = client.get_bucket('graas-resources')
    blob = bucket.get_blob('server/last_public_key_update.txt')
    last_key_update = int(blob.download_as_text())
    return last_key_update

def get_file(filename, mode):
    try:
        src = os.path.join('.', filename)
        return open(src, mode).read()
    except IOError as exc:
        return str(exc)

def get_mtime(path):
    # Wed, 21 Oct 2015 07:28:00 GMT
    return time.strftime("%a, %d %b %Y %H:%M:%S GMT", time.gmtime(os.path.getmtime(path)))

def get_token(s, start):
    if start >= len(s):
        return None

    if s[start] == '"':
        i = start + 1

        while i < len(s):
            if s[i] == '"':
                break
            if s[i] == '\\' and i + 1 < len(s) and s[i + 1] == '"':
                i += 2
            else :
                i += 1

        return {'text': s[start: i + 1], 'type': 'string', 'length': i - start + 1}
    else:
        return {'text': s[start], 'type': 'symbol', 'length': 1}
