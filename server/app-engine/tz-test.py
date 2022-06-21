from datetime import datetime
import pytz
import util

PACIFIC_TZ = pytz.timezone("America/Los_Angeles")
UTC_TZ = pytz.timezone("UTC")

"""
def get_current_time_millis():
    return int(round(time.time() * 1000))

def get_seconds_since_midnight(seconds = None, tz = PACIFIC_TZ):
    if seconds is None:
        now = datetime.now(tz)
    else:
        now = datetime.fromtimestamp(seconds)

    return int((now - now.replace(hour=0, minute=0, second=0, microsecond=0)).total_seconds())

def get_yyyymmdd(date = None, tz = PACIFIC_TZ):
    if date is None:
        date = datetime.now(tz)

    return f'{date.year:04}-{date.month:02}-{date.day:02}'
"""

s = '06/03/22 5:14 pm'
then = datetime.strptime(s, '%m/%d/%y %I:%M %p')
print(f'- then: {then}')

psecs = util.get_seconds_since_midnight(None, PACIFIC_TZ)
phours = int(psecs / 3600)
print(f'- phours: {phours}')

usecs = util.get_seconds_since_midnight(None, UTC_TZ)
uhours = int(usecs / 3600)
print(f'- uhours: {uhours}')

if uhours < phours:
    uhours += 24

hdiff = uhours - phours
print(f'- hdiff: {hdiff}')

if hdiff != 7:
    raise Exception(f'expected hdiff of 7, got {hdiff}, uhours: {uhours}, phours: {phours}')

pdt = datetime.fromtimestamp(int(then.timestamp()), PACIFIC_TZ)
udt = datetime.fromtimestamp(int(then.timestamp()), UTC_TZ)

print(f'- pdt: {pdt}')
print(f'- udt: {udt}')

pymd = util.get_yyyymmdd(pdt, PACIFIC_TZ)
print(f'- pymd: {pymd}')

uymd = util.get_yyyymmdd(udt, UTC_TZ)
print(f'- uymd: {uymd}')

if pymd != '2022-06-03' or uymd != '2022-06-04':
    raise Exception('get_yyymmdd() failure, pymd: {pymd}, uymd: {uymd}')

print('=============> tz test succeeded!')

