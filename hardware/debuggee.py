import os
import urllib.request
import subprocess
import sys
import time
import socket
import random
import traceback
import util

def get_current_debuggee():
    n = random.randrange(100000)
    #util.debug(f'- n: {n}')
    url = f'http://storage.googleapis.com/graas-resources/debugging/debuggee.txt?foo={n}'
    #util.debug(f'- url: {url}')
    with urllib.request.urlopen(url) as response:
        lines = response.read()
        lines = lines.decode('utf-8')
    #util.debug(f'- lines: {lines}')
    index = lines.find('\n')
    if index > 0:
        return lines[0:index]
    else:
        return lines

def get_hostname():
    return socket.gethostname()

def is_ngrok_running():
    output = subprocess.run(['./psg.sh'], capture_output=True).stdout
    output = output.decode('utf-8').strip()
    #util.debug(f'- output: {output}')
    #util.debug(f'- len(output): {len(output)}')
    return len(output) > 0

def main(argv):
    while True:
        try:
            debuggee = get_current_debuggee()
            util.debug(f'- debuggee: {debuggee}')
            hostname = get_hostname()
            #util.debug(f'- hostname: {hostname}')

            is_debuggee = debuggee == hostname
            #util.debug(f'- is_debuggee: {is_debuggee}')

            if is_debuggee and not is_ngrok_running():
                util.debug(f'+ starting debug connection')
                with open('/dev/null', 'w') as devnull:
                    tunnel = os.getenv('GRASS_TUNNEL')
                    util.debug(f'- tunnel: {tunnel}')

                    subprocess.Popen(
                        [
                            '/home/pi/bin/ngrok',
                            'tcp',
                            '--region=us',
                            f'--remote-addr={tunnel}',
                            '22'
                        ],
                        stdout=devnull
                    )

            if not is_debuggee and is_ngrok_running():
                util.debug(f'+ stopping debug connection')
                subprocess.Popen(['killall', '--signal', 'KILL', 'ngrok'])

            #util.debug('')

        except:
            traceback.print_exc(file=sys.stdout)

        time.sleep(5)

if __name__ == '__main__':
    main(sys.argv[1:])
