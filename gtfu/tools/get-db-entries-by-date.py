from google.cloud import datastore
from datetime import datetime
import warnings
import sys, getopt, time

warnings.filterwarnings("ignore", "Your application has authenticated using end user credentials")

def get_cutoff_seconds(str):
    splitat = len(str) - 1
    num, unit = str[:splitat], str[splitat:]
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

def main(argv):
    cutoff = int(round(time.time())) - 60 * 60 * 2
    print_timestamp = True
    print_id = True

    try:
        opts, args = getopt.getopt(argv, "c:d:t", ["cutoff=", "date=", "today"])
    except getopt.GetoptError:
        print('get-db-entries-by-date.py -c <n>m|<n>h|<n>d')
        sys.exit(2)
    for opt, arg in opts:
        if opt in ("-c", "--cutoff"):
            cutoff = int(round(time.time())) - get_cutoff_seconds(arg)

        if opt in ("-t", "--today"):
            cutoff = midnight_seconds()

        if opt in ("-d", "--date"):
            cutoff = midnight_seconds(arg)

    print('vehicle-id,timestamp,lat,long,trip-id,agency-id')

    cutoffend = cutoff + 86400

    datastore_client = datastore.Client()
    query = datastore_client.query(kind='position')

    query.add_filter('timestamp', '>=', cutoff)
    query.add_filter('timestamp', '<=', cutoffend)

    query.order = ['timestamp']
    positions = query.fetch()

    for pos in positions:
        if not 'vehicle-id' in pos:
            continue

        lat = pos['lat']
        lon = pos['long']
        ts = pos['timestamp']
        vid = pos['vehicle-id']
        tid = pos['trip-id']
        aid = pos['agency-id']

        if ts >= 2000000000:
            continue

        dn = ''
        if 'driver-name' in pos:
            dn = f" ({pos['driver-name']})"

        try:
            dt = datetime.fromtimestamp(int(pos['timestamp'])).strftime("%a %b %d @ %I:%M:%S %p")
        except ValueError:
            print('* bad timestamp: ' + str(pos['timestamp']))
            continue

        format = ''
        format = f'{ts},' + format
        format = f'{vid}{dn},' + format
        format = format + f'{lat},{lon}'
        format = format + f',{tid}'
        format = format + f',{aid}'

        print(format)

if __name__ == '__main__':
   main(sys.argv[1:])
