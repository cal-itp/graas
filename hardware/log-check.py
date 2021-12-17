import sys
import re
import socket
import util

SUCCESS_STR = 'server_response: ok'
WIFI_STR = 'server_response: 999'
GPS_STR = 'gps not ready'

def remove_terminal_control_codes(s):
    return re.sub('.\[[0-9][0-9]m', '', s)

def tabulate(s, n, total=None):
    l = s + ':'
    while len(l) < 10:
        l += ' '
    ns = str(n)
    while len(ns) < 7:
        ns = ' ' + ns

    ts = ''

    if not total is None:
        if total == 0:
            pct = 0
        else:
            pct = round(float(n) / total * 100)
        ts = str(pct)

        while len(ts) < 3:
            ts = ' ' + ts

        ts = f' ({ts}%)'

    return l + ns + ts

def get_time(line):
    index = line.find(' ')
    if index < 0:
        return ''
    return line[:index]

def replace_char(str, index, c):
    tmp = list(str)
    tmp[index] = c
    return "".join(tmp)

def print_verbose(obj):
    print()
    print(f'timestamp: {obj["timestamp"]}')
    print(f'run time: {obj["runtime"]}')
    print(tabulate('success', obj["success"], obj["total_attempts"]))
    print(tabulate('wifi_fail', obj["wifi_fail"], obj["total_attempts"]))
    print(tabulate('gps_fail', obj["gps_fail"], obj["total_attempts"]))
    print(tabulate('total', obj["total_attempts"]))
    print()

    if obj["total_attempts"] != 0:
        print(f'update frequency: {obj["frequency"]} seconds')
        print(f'max gap: {obj["max_gap"]} seconds @ {obj["gap_time"]}')

def print_csv(obj):
    if obj["total_attempts"] == 0:
        return
    success_pct = round(float(obj["success"]) / obj["total_attempts"] * 100)
    wifi_fail_pct = round(float(obj["wifi_fail"]) / obj["total_attempts"] * 100)
    gps_fail_pct = round(float(obj["gps_fail"]) / obj["total_attempts"] * 100)
    print(f'{obj["hostname"]},{obj["timestamp"]},{obj["runtime"]},{obj["success"]},{success_pct},{obj["wifi_fail"]},{wifi_fail_pct},{obj["gps_fail"]},{gps_fail_pct},{obj["frequency"]},{obj["max_gap"]}')

def main(filename, csv_format):
    success = 0
    wifi_fail = 0
    gps_fail = 0
    total_seconds = 0
    last_hhmmss = ''
    max_gap = 0
    gap_time = ''

    timestamp = filename[-15:-4]
    timestamp = replace_char(timestamp, 2, '/')
    timestamp = replace_char(timestamp, 5, ' ')
    timestamp = replace_char(timestamp, 8, ':')
    # print(f'timestamp: {timestamp}')

    hostname = socket.gethostname()

    with open(filename) as f:
        while True:
            hhmmss = ''
            line = f.readline()

            if not line:
                break

            line = line.strip()

            line = remove_terminal_control_codes(line)
            if len(line) > 2048:
                print(f'* found very long line: {line}')

            if line.find(SUCCESS_STR) >= 0:
                hhmmss = get_time(line)
                success += 1

            if line.find(WIFI_STR) >= 0:
                hhmmss = get_time(line)
                wifi_fail += 1

            if line.find(GPS_STR) >= 0:
                hhmmss = get_time(line)
                gps_fail += 1

            if hhmmss:
                if not last_hhmmss:
                    last_hhmmss = hhmmss
                seconds = util.hhmmss_to_seconds(hhmmss)
                last_seconds = util.hhmmss_to_seconds(last_hhmmss)

                if seconds >= last_seconds:
                    gap = seconds - last_seconds
                    total_seconds += gap

                    if gap > max_gap:
                        max_gap = gap
                        gap_time = hhmmss

                last_hhmmss = hhmmss

    total_attempts = success + wifi_fail + gps_fail

    if total_attempts == 0:
        frequency = 0
    else:
        frequency = total_seconds / total_attempts

    stats = {
        'hostname': hostname,
        'timestamp': timestamp,
        'runtime': util.seconds_to_hhmmss(total_seconds),
        'total_attempts': total_attempts,
        'success': success,
        'wifi_fail': wifi_fail,
        'gps_fail': gps_fail,
        'frequency': f'{frequency:.2f}',
        'max_gap': max_gap,
        'gap_time': gap_time
    }

    if csv_format:
        print_csv(stats)
    else:
        print_verbose(stats)

if __name__ == '__main__':
    csv_format = False

    if sys.argv[1] == '-csv':
        csv_format = True
        del(sys.argv[1])

    main(sys.argv[1], csv_format)
