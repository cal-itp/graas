import requests
import json
import sys
from rsa_util import rsa

def read_key(fn):
    f = open(fn, 'r')
    s = ''
    for l in f.readlines():
        if l.startswith('-----'):
            continue
        s += l.strip()
    f.close()
    return s

def main(arg):
    if len(arg) == 0 or arg[0] != 'refresh-server-keys':
        print('usage: send-server-command --help|<cmd> <path-to-der-file>')
        print('  only cmd currently supprted is \'refresh-server-keys\'')
        exit(0)

    cmd = arg[0]
    #print(f'- cmd: {cmd}')

    pk = read_key(arg[1])
    #print(f'- pk: {pk}')

    sig = rsa.sign(cmd, pk)
    #print(f'- sig: {sig}')

    msg = {"cmd": cmd, "sig": sig}
    print(f'- msg: {msg}')

    r = requests.post('https://lat-long-prototype.wl.r.appspot.com/cmd', json=msg)
    #r = requests.post('https://127.0.0.1:8080/cmd', verify=False, json=msg)
    print(f'- r.status_code: {r.status_code}')
    print(f'- r.json(): {r.json()}')

if __name__ == '__main__':
    # refresh-server-keys
    main(sys.argv[1:])
