import sys
import json

# a small tool to filter server logs. Intended usage is 'gcloud app logs tail | python log-filter.py'
# this approach to filtering is brittle as is depends on server debug output string format

KEY = 'data_str: '

def main(args):
    while True:
        line = sys.stdin.readline().strip()
        index = line.find(KEY)

        if  index > 0:
            str = line[index + len(KEY):]
            obj = json.loads(str)
            print(f'{obj["agent"]}: {obj["agency-id"]}#{obj["vehicle-id"]} {obj["lat"]} {obj["long"]}')
            sys.stdout.flush()

if __name__ == '__main__':
    try:
        main(sys.argv[1:])
    except KeyboardInterrupt:
        pass
