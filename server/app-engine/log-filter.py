import sys
import json
from datetime import datetime

# a small tool to filter server logs. Intended usage is 'gcloud app logs tail | python log-filter.py'
# this approach to filtering is brittle as is depends on server debug output string format

KEY = 'data_str: '

def main(args):
    while True:
        line = sys.stdin.readline().strip()
        index = line.find(KEY)

        if  index > 0:
            try:
                str = line[index + len(KEY):]
                obj = json.loads(str)
                ts = int(obj["timestamp"])
                if ts > 2000000000:
                    ts = ts / 1000
                hms = datetime.fromtimestamp(ts).strftime('%H:%M:%S')
                print(f'{hms} {obj.get("agent", None)}: {obj.get("agency-id", None)}#{obj.get("vehicle-id", None)} {obj.get("trip-id", None)} {obj.get("lat", None)} {obj.get("long", None)}')
                sys.stdout.flush()
            except:
                print(f'* exception for input \'{str}\'')

if __name__ == '__main__':
    try:
        main(sys.argv[1:])
    except KeyboardInterrupt:
        pass
