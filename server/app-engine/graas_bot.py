import requests
import sys
from google.cloud import datastore
from datetime import date, datetime
import os
import warnings
import time


def get_cutoff_seconds(str):
    splitat = len(str) - 1
    num, unit = str[:splitat], str[splitat:]
    # print('num: ', num)
    # print('unit: ', unit)
    if unit == 'm':
        return int(num) * 60
    if unit == 'h':
        return int(num) * 60 * 60
    if unit == 'd':
        return int(num) * 60 * 60 * 24
    raise ValueError('cutoff must be given as \'[num]m|h|d\'')

def midnight_seconds(date_string=None):
    if date_string is None:
        tm = datetime.now()
    else:
        tm = datetime.strptime(date_string, '%m/%d/%y')
    tm = tm.replace(hour=0, minute=0, second=0, microsecond=0)
    return int(tm.timestamp())

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
    counts = {'position': 0, 'alert': 0}
    agencies = set()

    for k in kinds:
        for pos in get_db_entries(datastore_client, k, start_time, end_time):
            ts = pos.get('timestamp', 'not found')

            counts[k] = counts[k] + 1

            agency = pos.get('agency-id', 'not found')

            if agency != 'not found':
                agencies.add(agency)

    output = f':bar_chart: *Statistics*\n'
    output += f'- Update Counts\n'

    for c in counts:
        output += f'  - {c}: {counts[c]}\n'

    if len(agencies) > 0:
        output += f'- Active Agencies\n'

    for a in agencies:
        output += f'  - {a}\n'

    print(output)

    payload = f'{{"text": "{output}"}}'
    result = requests.post(webhook, payload)
    print(f'- result: {result}')

if __name__ == '__main__':
   main(sys.argv[1:])
