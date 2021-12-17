from base64 import b64decode, b64encode
import ecdsa
from ecdsa.util import sigencode_der
from hashlib import sha256
import sys

class elliptic_curve:
    def sign(str, key):
        print('elliptic_curve.sign()')
        print(f'- str: {str}')
        print(f'- key: {key}')
        try:
            sk = ecdsa.SigningKey.from_der(b64decode(key), sha256)
            #print(f'- sk: {sk}')
            encoded = str.encode('utf-8')
            #print(f'+ encoded: {encoded.hex()}')
            sig = sk.sign(encoded, hashfunc=sha256)
            print(f'- len(sig): {len(sig)}')
            return b64encode(sig).decode('utf-8')
        except:
            print(f'* signature failure: {sys.exc_info()[0]}')
            return None

    def verify(str, key, sig):
        print('elliptic_curve.sign()')
        print(f'- str: {str}')
        print(f'- key: {key}')
        print(f'- sig: {sig}')
        try:
            vk = ecdsa.VerifyingKey.from_der(b64decode(key), sha256)
            print(f'- vk: {vk}')
            encoded = str.encode('utf-8')
            print(f'+ encoded: {encoded.hex()}')
            verified = vk.verify(b64decode(sig), encoded)
            print(f'- verified: {verified}')
            return verified
        except:
            print(f'* verification failure: {sys.exc_info()[0]}')
            return False

def main(argv):
    cmd = argv[0]
    #print(f'- cmd: {cmd}')

    if cmd == 'sign':
        sig = elliptic_curve.sign(argv[1], argv[2])
        print(sig)
    elif cmd == 'verify':
        verified = elliptic_curve.verify(argv[1], argv[2], argv[3])
        print(verified)

"""
usage:
  - sign <message> <private-key-der>
    (returns base64-encoded signature)
  - verify <message> <public-key-der> <b64-signature>
    (returns 'True' or 'False')
"""

if __name__ == '__main__':
   main(sys.argv[1:])

