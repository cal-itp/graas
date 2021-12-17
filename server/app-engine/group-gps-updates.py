import csv
import sys
from datetime import datetime

SECONDS_PER_MINUTE = 60
TIME_FORMAT = "%a, %b %d @ %I:%M %p"

time_table = {}
sd_table = {}
output_table = {}

def format_time(seconds):
    return datetime.fromtimestamp(seconds).strftime(TIME_FORMAT)

def get_last_seconds(id):
    return time_table.get(id, 0)

def set_last_seconds(id, seconds):
    time_table[id] = seconds

def get_start_date(id):
    return sd_table.get(id, None)

def set_start_date(id, date):
    #print('set_start_date()')
    #print(f'- id: {id}')
    #print(f'- date: {date}')
    sd_table[id] = date

def add_run(id, to):
    #print('add_run()')
    #print(f'- id: {id}')
    #print(f'- to: {to}')
    list = output_table.get(id)

    if list is None:
        list = []
        output_table[id] = list

    list.append(f'{id}: {get_start_date(id)} - {to}')

def display_runs():
    for id in output_table:
        for s in output_table.get(id):
            print(s)
        print()

def main(argv):
    file_name = argv[0]

    with open(file_name) as f:
        reader = csv.reader(f)

        for row in reader:
            id = row[0] + ' trip ID ' + row[4]
            seconds = int(row[1])
            last_seconds = get_last_seconds(id)

            if seconds - last_seconds > 5 * SECONDS_PER_MINUTE:
                hms1 = format_time(last_seconds)
                hms2 = format_time(seconds)

                if get_start_date(id) is None:
                    set_start_date(id, hms2)
                else:
                    add_run(id, hms1)
                    set_start_date(id, hms2)

            set_last_seconds(id, seconds)

        #hms2 = format_time(seconds)
        #add_run(id, hms2)

        for id in time_table:
            hms2 = format_time(time_table[id])
            add_run(id, hms2)

        display_runs()

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(f'usage: {sys.argv[0]} <path-to-raw-gps-updates>')
        quit()

    main(sys.argv[1:])

