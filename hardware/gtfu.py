import math
import sys
import time

class ShapePoint:
    def __init__(self, lat = None, lon = None):
        self.lat = lat
        self.lon = lon

    def set_lat_if_less(self, lat):
        if (self.lat is None or lat < self.lat):
            self.lat = lat

    def set_lat_if_greater(self, lat):
        if (self.lat is None or lat > self.lat):
            self.lat = lat

    def set_long_if_less(self, lon):
        if (self.lon is None or lon < self.lon):
            self.lon = lon

    def set_long_if_greater(self, lon):
        if (self.lon is None or lon > self.lon):
            self.lon = lon

    def get_distance(self, p):
        return Util.get_haversine_distance(self.lat, self.lon, p.lat, p.lon)

    def get_distance_from_lat_long(self, plat, plon):
        return Util.get_haversine_distance(self.lat, self.lon, plat, plon)

    def __str__(self):
        return '(' + str(self.lat) + ', ' + str(self.lon) + ')'

class Area:
    def __init__(self, tl = None, br = None):
        self.top_left = ShapePoint() if tl is None else ShapePoint(tl.lat, tl.lon)
        self.bottom_right = ShapePoint() if br is None else ShapePoint(br.lat, br.lon)

    def copy(a):
        return Area(a.top_left, a.bottom_right)

    def pad(self, scale):
        lat_adjust = (self.get_lat_delta() * (scale - 1) / 2);
        long_adjust = (self.get_long_delta() * (scale - 1) / 2);
        adjust = min(lat_adjust, long_adjust);
        a = self.copy(self);

        a.bottom_right.lat -= adjust;
        a.top_left.lat += adjust;

        a.top_left.lon -= adjust;
        a.bottom_right.lon += adjust;

        return a

    def update_from_shapepoint(self, p):
        self.update(p.lat, p.lon)

    def update(self, lat, lon):
        self.top_left.set_long_if_less(lon)
        self.top_left.set_lat_if_greater(lat)

        self.bottom_right.set_long_if_greater(lon)
        self.bottom_right.set_lat_if_less(lat)

    def __str__(self):
        return '{top_left: ' + str(self.top_left) + ', bottom_right: ' + str(self.bottom_right) + '}'

    def get_lat_delta(self):
        return abs(abs(self.top_left.lat) - abs(self.bottom_right.lat))

    def get_long_delta(self):
        return abs(abs(self.top_left.lon) - abs(self.bottom_right.lon))

    def get_width_in_feet(self):
        if self.top_left.lon is None or self.bottom_right.lon is None:
            return 0
        return int(Util.get_haversine_distance(0, self.top_left.lon, 0, self.bottom_right.lon))

    def get_height_in_feet(self):
        if self.top_left.lat is None or self.bottom_right.lat is None:
            return 0
        return int(Util.get_haversine_distance(self.top_left.lat, 0, self.bottom_right.lat, 0))

    def get_aspect_ratio(self):
        if self.get_lat_delta() == 0:
            return 0
        return self.get_long_delta() / self.get_lat_delta()

    def get_diagonal_in_feet(self):
        a = self.get_width_in_feet()
        b = self.get_height_in_feet()

        return int(math.sqrt(a * a + b * b))

    def get_lat_fraction(self, lat):
        if lat < self.bottom_right.lat or lat > self.top_left.lat:
            raise ValueError('bottom_right.lat: {}, lat: {}, top_left.lat: {}'.format(self.bottom_right.lat, lat, self.top_left.lat))

        delta = self.get_lat_delta()
        if delta == 0:
            return 0
        return 1 - ((lat - self.bottom_right.lat) / delta)

    def get_long_fraction(self, lon):
        if lon > self.bottom_right.lon or lon < self.top_left.lon:
            raise ValueError('bottom_right.lat: {}, lat: {}, top_left.lat: {}'.format(self.bottom_right.lat, lat, self.top_left.lat))

        delta = self.get_long_delta()
        if delta == 0:
            return 0
        return 1 - ((abs(lon) - abs(self.bottom_right.lon)) / delta)

    def contains_point(self, p):
        return self.contains(p.lat, p.lon)

    def contains(self, lat, lon):
        return (lat >= self.bottom_right.lat and lat <= self.top_left.lat
            and lon <= self.bottom_right.lon and lon >= self.top_left.lon)

class Util:
    EARTH_RADIUS_IN_FEET = 20902231;
    FEET_PER_MILE = 5280

    def now():
        return time.time()

    def get_display_distance(feet):
        if feet < Util.FEET_PER_MILE:
            return f'{feet} FEET'
        elif feet < 10 * Util.FEET_PER_MILE:
            v = feet / Util.FEET_PER_MILE
            return f'{v:.1f} MILES'
        else:
            return f'{int(feet / Util.FEET_PER_MILE)} MILES'

    # converts coordinates from lat/long to x/y given
    # display width and height, an area instance
    # and a shapepoint instance
    def lat_long_to_x_y(display_width, display_height, a, p):
        fraction_lat = a.get_lat_fraction(p.lat)
        fraction_long = a.get_long_fraction(p.lon);

        ratio = a.get_aspect_ratio();
        #return (int(display_height * ratio * fraction_long), int(display_height * fraction_lat))
        return (int(display_width * fraction_long), int(display_height * fraction_lat))

    # haversine distance from lat1/lon1 to lat2/lon2  in feet
    def get_haversine_distance(lat1, lon1, lat2, lon2):
        phi1 = math.radians(lat1)
        phi2 = math.radians(lat2)
        delta_phi = math.radians(lat2 - lat1)
        delta_lam = math.radians(lon2 - lon1)

        a = (math.sin(delta_phi / 2) * math.sin(delta_phi / 2)
            + math.cos(phi1) * math.cos(phi2)
            * math.sin(delta_lam / 2) * math.sin(delta_lam / 2))
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

        return Util.EARTH_RADIUS_IN_FEET * c

    def dft(x, K):
        X = []
        N = len(x)
        #print('- N: ' + str(N))

        for k in range(K):
            re = 0
            im = 0

            for n in range(N):
                phi = 2 * math.pi * k * n / N
                re += x[n] * math.cos(phi)
                im -= x[n] * math.sin(phi)

            amp = math.sqrt(re * re + im * im)
            phase = math.atan2(im, re)
            X.append({"re": re, "im": im, "freq": k, "amp": amp, "phase": phase})

        return X

    def idft(X, n, N):
        x = 0

        for k in range(N):
            phi = 2 * math.pi * k * n / N
            x += X[n]['re'] * math.cos(phi) + X[n]['im'] * math.sin(phi)

        return x / N

    @classmethod
    def get_token(cls, s, start):
        if start >= len(s):
            return None

        if s[start] == '"':
            i = start + 1

            while i < len(s):
                if s[i] == '"':
                    break
                if s[i] == '\\' and i + 1 < len(s) and s[i + 1] == '"':
                    i += 2
                else :
                    i += 1

            return {'text': s[start: i + 1], 'type': 'string', 'length': i - start + 1}
        else:
            return {'text': s[start], 'type': 'symbol', 'length': 1}

    @classmethod
    def get_sub_object(cls, s, name):
        header = f'"{name}":{{'
        start = s.find(header)
        if start < 0:
            return None
        start = start + len(header)
        length = 0;
        brace_count = 1

        while brace_count > 0:
            token = util.get_token(s, start + length)
            if not token:
                raise Exception('malformed input string')

            length += token['length']

            if token["text"] == '{':
                brace_count += 1
            if token["text"] == '}':
                brace_count -= 1
                if brace_count == 0:
                    return s[start - 1: start + length]


