#from csvline import CSVLine
import csv
import datetime
import time
import util
from shapepoint import ShapePoint
from area import Area
from grid import Grid
from segment import Segment
from timer import Timer

FEET_PER_MILE = 5280
STOP_PROXIMITY = 150
SCORE_THRESHOLD = 50
MIN_FLUSH_TIME_DELTA = 30 * 60

class TripInference:
    VERSION = '0.2 (12/07/21)'

    def __init__(self, path, url, subdivisions):
        if path[-1] != '/':
            path += '/'

        util.update_cache_if_needed(path, url)

        self.trip_candidates = {}
        self.last_candidate_flush = time.time()

        self.path = path
        calendar_map = self.get_calendar_map()
        util.debug(f'- calendar_map: {calendar_map}')

        route_map = self.get_route_map()
        util.debug(f'- route_map: {route_map}')

        self.area = Area()
        self.populateBoundingBox(self.area)
        util.debug(f'- self.area: {self.area}')
        self.grid = Grid(self.area, subdivisions)
        dow = datetime.datetime.today().weekday()
        util.debug(f'- dow: {dow}')

        self.stops = self.get_stops()
        #util.debug(f'-- stops: {stops}')

        self.preload_stop_times()
        self.preload_shapes()

        with open(path + '/trips.txt', 'r') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            count = 1

            load_timer = Timer('load')

            for r in rows:
                loop_timer = Timer('loop')
                trip_id = r['trip_id']
                service_id = r['service_id']
                shape_id = r['shape_id']

                if not service_id in calendar_map:
                    util.debug(f'* service id \'{service_id}\' not found in calendar map, skipping trip \'{trip_id}\'')
                    continue

                if calendar_map[service_id][dow] != 1:
                    util.debug(f'* dow \'{dow}\' not set, skipping trip \'{trip_id}\'')
                    continue

                util.debug(f'')
                util.debug(f'-- trip_id: {trip_id} ({count}/{len(rows)})')
                count += 1

                route_id = r['route_id']
                shape_id = r['shape_id']
                #util.debug(f'-- shape_id: {shape_id}')
                timer = Timer('way points')
                way_points = self.get_shape_points(shape_id)
                util.debug(timer)
                #util.debug(f'-- way_points: {way_points}')
                util.debug(f'-- len(way_points): {len(way_points)}')

                if len(way_points) == 0:
                    util.debug(f'* no way points for trip_id \'{trip_id}\', shape_id \'{shape_id}\'')
                    continue

                timer = Timer('stop times')
                stop_times = self.get_stop_times(trip_id)
                util.debug(timer)
                #util.debug(f'-- stop_times: {stop_times}')
                util.debug(f'-- len(stop_times): {len(stop_times)}')
                timer = Timer('interpolate')
                self.interpolate_way_point_times(way_points, stop_times, self.stops)
                util.debug(timer)

                trip_name = route_map[route_id]['name'] + ' @ ' + util.seconds_to_ampm_hhmm(stop_times[0]['arrival_time'])
                util.debug(f'-- trip_name: {trip_name}')
                segment_length = 2 * FEET_PER_MILE ### TODO derive segment length from trip length
                timer = Timer('segments')
                self.make_trip_segments(trip_id, trip_name, way_points, segment_length)
                util.debug(timer)
                util.debug(loop_timer)

        util.debug(load_timer)
        util.debug(f'-- self.grid: {self.grid}')

        self.stop_time_map = {}
        self.shape_map = {}

    def populateBoundingBox(self, area):
        with open(self.path + '/shapes.txt', 'r') as f:
            names = f.readline().strip()
            #util.debug(f'-- names: {names}')
            csvline = csv.CSVLine(names)

            while True:
                line = f.readline()

                if not line:
                    break

                line = line.strip()
                r = csvline.parse(line)

                lat = float(r['shape_pt_lat'])
                lon = float(r['shape_pt_lon'])
                area.update(lat, lon)

    def preload_shapes(self):
        self.shape_map = {}

        with open(self.path + '/shapes.txt', 'r') as f:
            names = f.readline().strip()
            #util.debug(f'-- names: {names}')
            csvline = csv.CSVLine(names)

            while True:
                file_offset = f.tell()
                #util.debug(f'-- file_offset: {file_offset}')
                line = f.readline()

                if not line:
                    break

                line = line.strip()
                r = csvline.parse(line)
                shape_id = r['shape_id']
                #debug.log(f'-- sid: {sid}')

                lat = float(r['shape_pt_lat'])
                lon = float(r['shape_pt_lon'])

                plist = self.shape_map.get(shape_id, None)

                if plist is None:
                    plist = []
                    self.shape_map[shape_id] = plist

                plist.append({'lat': lat, 'long': lon, 'file_offset': file_offset})

    def get_shape_points(self, shape_id):
        return self.shape_map.get(shape_id, None)

    def get_stops(self):
        slist = {}

        with open(self.path + '/stops.txt', 'r') as f:
            reader = csv.DictReader(f)
            rows = list(reader)

            for r in rows:
                # util.debug(f'-- r: {r}')
                id = r['stop_id']
                lat = float(r['stop_lat'])
                lon = float(r['stop_lon'])

                slist[id] = {'lat': lat, 'long': lon}

        return slist

    def get_route_map(self):
        #util.debug(f'get_route_map()')
        route_map = {}

        with open(self.path + '/routes.txt', 'r') as f:
            reader = csv.DictReader(f)
            rows = list(reader)

            for r in rows:
                route_id = r['route_id']
                #debug.log(f'-- route_id id: {route_id}')
                short_name = r['route_short_name'] ### TODO short_name is optional
                long_name = r['route_long_name']
                name = short_name if len(short_name) > 0 else long_name

                route_map[route_id] = {'name': name}

        return route_map

    def get_calendar_map(self):
        #util.debug(f'get_calendar_map()')
        calendar_map = {}

        with open(self.path + '/calendar.txt', 'r') as f:
            dow = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']
            reader = csv.DictReader(f)
            rows = list(reader)

            for r in rows:
                service_id = r['service_id']
                #util.debug(f'-- service id: {service_id}')
                cal = []

                for d in dow:
                    cal.append(int(r[d]))
                #util.debug(f'-- cal: {cal}')

                calendar_map[service_id] = cal

        return calendar_map

    def preload_stop_times(self):
        self.stop_time_map = {}

        with open(self.path + '/stop_times.txt', 'r') as f:
            reader = csv.DictReader(f)
            rows = list(reader)

            for r in rows:
                trip_id = r['trip_id']

                arrival_time = r['arrival_time']
                if len(arrival_time) == 0:
                    continue

                stop_id = r['stop_id']

                slist = self.stop_time_map.get(trip_id, None)

                if slist is None:
                    slist = []
                    self.stop_time_map[trip_id] = slist

                slist.append({'arrival_time': util.hhmmss_to_seconds(arrival_time), 'stop_id': stop_id})

    def get_stop_times(self, trip_id):
        return self.stop_time_map.get(trip_id, None)

    def get_distance(self, way_points, wi, stop_times, stops, si):
            wp = way_points[wi]
            sp = stops[stop_times[si]['stop_id']]
            return util.haversine_distance(wp['lat'], wp['long'], sp['lat'], sp['long'])

    """
    This is a naive, brute force approach that may well fail for self-intersecting or
    self-overlapping routes, or just very twisty routes. A better approach may be:
    - assign fraction to stops based on where their arrival time falls for overall trip duration.
      distance along route or a combination of both
    - set closest-stop attr for way points with corresponding fractions
    - search n neighbors on either side of points with attr and move attr to neighbor if closer
    - repeat until stable
    """
    def interpolate_way_point_times(self, way_points, stop_times, stops):
        index_list = []
        firstStop = True

        for st in stop_times:
            sp = stops[st['stop_id']]

            if firstStop:
                sp['first_stop'] = True
                firstStop = False

            min_distance = 1000000
            min_index = -1

            for i in range(len(way_points)):
                wp = way_points[i]
                distance = util.haversine_distance(wp['lat'], wp['long'], sp['lat'], sp['long'])

                if distance < min_distance:
                    min_distance = distance
                    min_index = i

            index_list.append({'index': min_index, 'time': st['arrival_time']})

        #util.debug(f'- index_list: {index_list}')
        #util.debug(f'- len(index_list): {len(index_list)}')

        for i in range(len(index_list) - 1):
            start = index_list[i]
            end = index_list[i + 1]
            tdelta = end['time'] - start['time']
            idelta = end['index'] - start['index']

            #util.debug('--------------------------')

            for j in range(idelta):
                fraction = j / idelta
                time = start['time'] + int(fraction * tdelta)
                way_points[start['index'] + j]['time'] = time
                hhmmss = util.seconds_to_hhmmss(time)
                #util.debug(f'--- {hhmmss}')

        #util.debug('--------------------------')

        index = index_list[0]['index']
        time = index_list[0]['time']

        while index >= 0:
            index -= 1
            way_points[index]['time'] = time

        index = index_list[-1]['index']
        time = index_list[-1]['time']

        while index < len(way_points):
            way_points[index]['time'] = time
            index += 1

        ### REMOVE ME: for testing only
        for i in range(len(way_points)):
            if not 'time' in way_points[i]:
                util.debug(f'.. {i}')

    def make_trip_segments(self, trip_id, trip_name, way_points, max_segment_length):
        segment_start = 0
        index = segment_start
        last_index = index
        segment_length = 0

        area = Area()
        index_list = []

        while index < len(way_points):
            lp = way_points[last_index]
            p = way_points[index]

            area.update(p['lat'], p['long'])

            grid_index = self.grid.get_index(p['lat'], p['long'])
            if not grid_index in index_list:
                index_list.append(grid_index)

            distance = util.haversine_distance(lp['lat'], lp['long'], p['lat'], p['long'])
            segment_length += distance

            if segment_length >= max_segment_length or index == len(way_points) - 1:
                area.extend(1000)

                segment = Segment(
                    trip_id,
                    trip_name,
                    area,
                    way_points[segment_start]['time'],
                    way_points[index]['time'],
                    way_points[segment_start]['file_offset'],
                    way_points[index]['file_offset']
                )

                for i in index_list:
                    self.grid.add_segment(segment, i)

                segment_length = 0
                area = Area()
                index_list = []

                index += 1
                last_index = index
                segment_start = index
                continue

            last_index = index
            index += 1

    # NOTE: brute force approach that returns the *first*
    # stop within max_distance feet from lat/lon
    def get_stop_for_position(self, lat, lon, max_distance):
        for stop_id in self.stops:
            stop = self.stops[stop_id]
            if util.haversine_distance(lat, lon, stop['lat'], stop['long']) < max_distance:
                return stop_id
        return None

    def check_for_trip_start(stop_id):
        if not stop_id in self.stops:
            return

        stop = self.stops[stop_id]
        delta = time.time() - self.last_candidate_flush

        if 'first_stop' in stop and delta >= MIN_FLUSH_TIME_DELTA:
            util.debug('+++ flushing trip candidates! +++')
            self.trip_candidates = {}
            self.last_candidate_flush = time.time()

    ###
    ### FIXME: trip candidate list needs to be flushed when new trip begins
    ### How can we tell that a new trip begins? current location gets a non-
    ### zero score, matches lat/long of first stop of a trip, and there hasn't
    ### been another first stop trip match in a while
    ###
    def get_trip_id(self, lat, lon, seconds):
        segment_list = self.grid.get_segment_list(lat, lon)

        if segment_list is None:
            return None

        util.debug(f'- len(segment_list): {len(segment_list)}')

        multiplier = 1
        stop_id = self.get_stop_for_position(lat, lon, STOP_PROXIMITY)
        if not stop_id is None:
            multiplier = 10

        max_segment_score = 0

        for segment in segment_list:
            score = multiplier * segment.get_score(lat, lon, seconds, self.path)
            if score > max_segment_score:
                max_segment_score = score

            trip_id = segment.get_trip_id()

            if trip_id in self.trip_candidates:
                candidate = self.trip_candidates[trip_id]
            else:
                candidate = {'score': 0}
                self.trip_candidates[trip_id] = candidate

            candidate['score'] += score

        if max_segment_score > 0 and not stop_id is None:
            self.check_for_trip_start(stop_id)

        max_score = 0
        max_trip_id = None

        for trip_id in self.trip_candidates:
            score = self.trip_candidates[trip_id]['score']

            if score > max_score:
                max_score = score
                max_trip_id = trip_id

        util.debug(f'- max_score: {max_score}')

        if max_score >= SCORE_THRESHOLD:
            return max_trip_id
        else:
            return None
