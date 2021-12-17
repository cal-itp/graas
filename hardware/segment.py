from area import Area
import csv
import random
import util

MAX_LOCATION_DISTANCE = 1000 # feet
MAX_TIME_DISTANCE = 900 # seconds

"""
### TODO add cache for way points with predicted arrival times:
    - key: hash of min_file_offset, max_file_offset, start_time and end_time
    - value: grid index, list of way points with predicted arrival times
    - add class method set_current_grid_index(), which will flush all
      cache entries with non-matching grid index
    - in get_score(), either get way points from cache or create and
      put into cache
"""
class Segment:
    id_base = 0

    def __init__(self, trip_id, trip_name, bounding_box, start_time, end_time, min_file_offset, max_file_offset):
        self.id = Segment.id_base
        Segment.id_base += 1

        util.debug(f'segment: id={self.id} top_left={bounding_box.top_left} bottom_right={bounding_box.bottom_right} start_time={util.seconds_to_hhmmss(start_time)} end_time={util.seconds_to_hhmmss(end_time)}')

        self.trip_id = trip_id
        self.trip_name = trip_name
        self.bounding_box = bounding_box
        self.start_time = start_time
        self.end_time = end_time
        self.min_file_offset = min_file_offset
        self.max_file_offset = max_file_offset

    def get_score(self, lat, lon, seconds, path):
        if not self.bounding_box.contains(lat, lon):
            return -1

        ### REMOVE ME: for testing only
        #if random.random() < .5:
        #    util.debug(f'segment update: id={self.id} trip-name={util.to_b64(self.trip_name)} score={0.0000001}')

        if seconds < self.start_time - MAX_TIME_DISTANCE or seconds > self.end_time + MAX_TIME_DISTANCE:
            return -1

        list = []

        with open(path + '/shapes.txt', 'r') as f:
            names = f.readline().strip()
            csvline = csv.CSVLine(names)

            f.seek(self.min_file_offset)

            while True:
                line = f.readline().strip()
                r = csvline.parse(line)

                lat = float(r['shape_pt_lat'])
                lon = float(r['shape_pt_lon'])

                list.append({'lat': lat, 'lon': lon})

                if f.tell() > self.max_file_offset:
                    break

        delta_time = self.end_time - self.start_time
        min_distance = 1000000
        min_index = -1

        for i in range(len(list)):
            distance = util.haversine_distance(list[i]['lat'], list[i]['lon'], lat, lon)

            if distance < min_distance:
                min_distance = distance
                min_index = i

            fraction = i / len(list)
            time = int(self.start_time + fraction * delta_time)
            list[i]['time'] = time

        if min_distance > MAX_LOCATION_DISTANCE:
            return -1

        time_distance = abs(seconds - list[min_index]['time'])

        if time_distance > MAX_TIME_DISTANCE:
            return -1

        location_score = .5 * (MAX_LOCATION_DISTANCE - min_distance) / MAX_LOCATION_DISTANCE
        time_score = .5 * (MAX_TIME_DISTANCE - time_distance) / MAX_TIME_DISTANCE

        util.debug(f'segment update: id={self.id} trip-name={util.to_b64(self.trip_name)} score={location_score + time_score}')
        return location_score + time_score

    def get_trip_id(self):
        return self.trip_id
