from google.cloud import datastore
from datetime import datetime
import warnings
import sys, getopt, time

warnings.filterwarnings("ignore", "Your application has authenticated using end user credentials")

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

def main(argv):
    uuid = ''
    agency_id = ''
    agent_string = ''
    cutoff = int(round(time.time())) - 60 * 60 * 2
    print_time = False
    print_timestamp = False
    print_trip_id = False
    print_driver = False
    print_vehicle_id = False
    print_id = False

    try:
        opts, args = getopt.getopt(argv,"u:a:n:c:tTid:Cxyz",["uuid=", "agency-id=", "agent-string=","cutoff=", "timestamp","friendly-timestamp","tripid","date=","currentday","print_driver","print_vehicle","print_id"])
    except getopt.GetoptError:
        print('get-pos.py -u <uuid>')
        sys.exit(2)
    for opt, arg in opts:
        if opt in ("-u", "--uuid"):
            uuid = arg
        if opt in ("-a", "--agency-id"):
            agency_id = arg
        if opt in ("-n", "--agent-string"):
            agent_string = arg
        if opt in ("-c", "--cutoff"):
            cutoff = int(round(time.time())) - get_cutoff_seconds(arg)
        if opt in ("-t", "--timestamp"):
            print_timestamp = True
        if opt in ("-T", "--friendly-timestamp"):
            print_time = True
        if opt in ("-i", "--tripid"):
            print_trip_id = True
        if opt in ("-d", "--date"):
            cutoff = midnight_seconds(arg)
        if opt in ("-C", "--currentday"):
            cutoff = midnight_seconds()
        if opt in ("-x", "--print-driver"):
            print_driver = True
        if opt in ("-y", "--print-vehicle"):
            print_vehicle_id = True
        if opt in ("-z", "--print-id"):
            print_id = True


    cutoffend = cutoff + 86400

    if len(agency_id) == 0 and len(uuid) == 0:
        print('must set either UUID or agency ID')
        return

    datastore_client = datastore.Client()
    query = datastore_client.query(kind='position')

    if len(uuid) > 0:
        query.add_filter('uuid', '=', uuid)
    else:
        query.add_filter('agency-id', '=', agency_id)

    query.add_filter('timestamp', '>=', cutoff)
    query.add_filter('timestamp', '<=', cutoffend)

    query.order = ['timestamp']
    positions = query.fetch()

# create column headers
    header = ''

    if print_timestamp:
        header = header + 'timestamp,'


    if print_time:
        header = header + 'time-string,'

    header = header + 'lat,long,'

    if print_trip_id:
        header = header + 'trip-id,'

    if print_vehicle_id:
        header = header + 'vehicle-id,'

    if len(agent_string) > 0:
        header = header + 'agent,'

    if print_driver:
        header = header + 'driver-name,'

    if print_id:
        if len(uuid) > 0:
            header = header + 'uuid,'
        if len(agency_id) > 0:
            header = header + 'agency-id,'

    #use [:-1] to remove trailing "," from each line
    if(header[-1] == ','):
        header = header[:-1]
    print(header)

    for pos in positions:
            if int(pos['timestamp']) > 2000000000:
                continue

            #filter out all entries that aren't from the given agent
            if len(agent_string) > 0 and 'agent' in pos and agent_string not in pos['agent']:
                continue

            if print_time:
                try:
                    pos['time-string'] = datetime.fromtimestamp(int(pos['timestamp'])).strftime("%a %b %d @ %I:%M:%S %p")

                except ValueError:
                    print('* bad timestamp: ' + pos['timestamp'])
                    continue

            format = ''
            for column_name in header.split(","):
                format = format + str(pos[column_name]) + ","

            #use [:-1] to remove trailing "," from each line
            if(format[-1] == ','):
                format = format[:-1]
            print(format)

if __name__ == '__main__':
   main(sys.argv[1:])