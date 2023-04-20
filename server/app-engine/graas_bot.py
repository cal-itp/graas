import requests
import sys
from google.cloud import datastore
import os
import time

# 'all-your-base' -> 'All Your Base'
def to_display_name(s):
    r = ''
    last_c = '-'

    for c in s:
        if c == '-':
            r += ' '
        elif c.islower() and last_c == '-':
            r += c.upper()
        else:
            r += c

        last_c = c

    return r

"""

Generate an ASCII table similar to the example below based on input
- counts: dictionary with client message names and counts
- agencies: set of agency IDs

+------------------------------------------+
| Client Updates                           |
+----+----------+--------------------------+
|    | Position |                    8,131 |
|    +----------+--------------------------+
|    | Alert    |                        0 |
+----+----------+--------------------------+
| Active Agencies                          |
+----+-------------------------------------+
|    | Altamont Corridor Express           |
|    +-------------------------------------+
|    | Santa Ynez Valley Transport         |
+----+-------------------------------------+
"""
def make_ascii_table(counts, agencies):
    alist = list(agencies)
    alist.sort()
    print(f'- alist: {alist}')

    maxalen = 0

    for a in alist:
        if len(a) > maxalen:
            maxalen = len(a)

    print(f'- maxalen: {maxalen}')

    maxklen = 0

    for k in counts:
        if len(k) > maxklen:
            maxklen = len(k)

    print(f'- maxklen: {maxklen}')

    rowlen = maxalen + 17
    print(f'- rowlen: {rowlen}')
    output = ''

    line = '+'
    line = line.ljust(rowlen - 1, '-')
    line += '+\n'
    output += line

    line = '| Client Updates'
    line = line.ljust(rowlen - 1, ' ')
    line += '|\n'
    output += line

    line = '+----+-'
    line = line.ljust(len(line) + maxklen, '-')
    line += '-+'
    line = line.ljust(rowlen - 1, '-')
    line += '+\n'
    output += line

    i = 0
    for c in counts:
        line = '|    | '
        line += to_display_name(c)
        line = line.ljust(len(line) + maxklen - len(c), ' ')
        line += ' |'
        num = f'{counts[c]:,}'
        line = line.ljust(rowlen - len(num) - 2, ' ')
        line += num
        line += ' |\n'
        output += line

        if i < len(counts) - 1:
            line = '|    +-'
            line = line.ljust(len(line) + len(c), '-')
            line += '-+-'
            line = line.ljust(rowlen - 1, '-')
            line += '+\n'
            output += line
        else:
            line = '+----+-'
            line = line.ljust(len(line) + maxklen, '-')
            line += '-+'
            line = line.ljust(rowlen - 1, '-')
            line += '+\n'
            output += line

        i = i + 1

    line = '| Active Agencies'
    line = line.ljust(rowlen - 1, ' ')
    line += '|\n'
    output += line

    line = '+----+-'
    line = line.ljust(rowlen - 1, '-')
    line += '+\n'
    output += line

    i = 0
    for a in alist:
        line = '|    | '
        line += to_display_name(a)
        line = line.ljust(rowlen - 2, ' ')
        line += ' |\n'
        output += line

        if i < len(alist) - 1:
            line = '|    +-'
            line = line.ljust(rowlen - 1, '-')
            line += '+\n'
            output += line
        else:
            line = '+----+-'
            line = line.ljust(rowlen - 1, '-')
            line += '+\n'
            output += line

        i = i + 1

    return output

def get_db_entries(datastore_client, entry_kind, start_time, end_time):
    query = datastore_client.query(kind=entry_kind)

    query.add_filter('timestamp', '>=', start_time)
    query.add_filter('timestamp', '<=', end_time)

    entries =  query.fetch()

    return entries

def main(argv):
    webhook = os.environ.get('GRAAS_BOT_WEBHOOK', 'not found')

    if webhook == 'not found':
        print('please set env variable "GRAAS_BOT_WEBHOOK"')
        exit(1)

    datastore_client = datastore.Client()

    end_time = int(round(time.time()))
    start_time = end_time - 86400

    print(f'- start_time: {start_time}')
    print(f'- end_time  : {end_time}')

    kinds = ['position', 'alert']
    counts = {'position': 8131, 'alert': 0}
    agencies = set()

    for k in kinds:
        for pos in get_db_entries(datastore_client, k, start_time, end_time):
            ts = pos.get('timestamp', 'not found')

            counts[k] = counts[k] + 1

            agency = pos.get('agency-id', 'not found')

            if agency != 'not found':
                agencies.add(agency)

    output = ':bar_chart: *Stats for the Past 24 Hours*\n'
    output += '```\n'
    output += make_ascii_table(counts, agencies)
    output += '```\n'

    print('\n' + output)

    payload = f'{{"text": "{output}"}}'
    result = requests.post(webhook, payload)
    print(f'- result: {result}')

if __name__ == '__main__':
   main(sys.argv[1:])
