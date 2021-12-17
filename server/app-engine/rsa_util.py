

import sys
from Crypto.PublicKey import RSA
from Crypto.Signature import PKCS1_v1_5
from Crypto.Hash import SHA256
from base64 import b64decode, b64encode

class rsa:
    @classmethod
    def verify(cls, data, key, signature):
        print('verify()')
        print('- data: ' + data)
        print('- key: ' + str(key))
        print('- signature: ' + signature)
        print('- len(key): ' + str(len(key)))

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

    @classmethod
    def sign(cls, data, key):
        rsakey = RSA.importKey(b64decode(key))
        signer = PKCS1_v1_5.new(rsakey)
        digest = SHA256.new()
        digest.update(data.encode('utf-8'))
        return b64encode(signer.sign(digest)).decode('utf-8')

def main(argv):
    verified = rsa.verify(argv[0], argv[1], argv[2])
    print(verified)

if __name__ == '__main__':
   main(sys.argv[1:])

