from base64 import b64decode
from Crypto.PublicKey import RSA
from Crypto.Signature import PKCS1_v1_5
from Crypto.Hash import SHA256
from hashlib import sha256
import ecdsa
import os.path
import sys
import time
from google.cloud import datastore
from google.cloud import storage

datastore_client = datastore.Client()
last_bucket_check = 0
last_key_refresh = 0
ONE_MINUTE_MILLIS = 60 * 1000  # 1 minute in milliseconds

# Pull agency IDs & public keys from datastore, save them
def read_public_keys():
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
    return int(round(time.time() * 1000))

# For new instances of GRaaS, replace 'graas-resources' with a globally unique directory name in the below two functions:
def update_bucket_timestamp():
    client = storage.Client()
    bucket = client.get_bucket('graas-resources')
    blob = bucket.blob('server/last_public_key_update.txt')
    # Could use util.get_current_time_millis() but it would create circular dependency
    now = int(round(time.time() * 1000))
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
