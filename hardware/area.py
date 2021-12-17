from shapepoint import ShapePoint
import util

class Area:
    def __init__(self, tl = None, br = None):
        self.top_left = ShapePoint(tl) if tl is None else ShapePoint(tl.lat, tl.lon)
        self.bottom_right = ShapePoint() if br is None else ShapePoint(br.lat, br.lon)

    def copy(a):
        return area(a.top_left, a.bottom_right)

    def pad(self, scale):
        lat_adjust = (self.get_lat_delta() * (scale - 1) / 2);
        long_adjust = (self.get_long_delta() * (scale - 1) / 2);
        adjust = min(lat_adjust, long_adjust);
        a = area.copy(self);

        a.bottom_right.lat -= adjust;
        a.top_left.lat += adjust;

        a.top_left.lon -= adjust;
        a.bottom_right.lon += adjust;

        return a

    def extend(self, feet):
        self.top_left.lat += util.get_feet_as_lat_degrees(feet / 2)
        self.top_left.lon -= util.get_feet_as_long_degrees(feet / 2)

        self.bottom_right.lat -= util.get_feet_as_lat_degrees(feet / 2)
        self.bottom_right.lon += util.get_feet_as_long_degrees(feet / 2)

    def update_from_shapepoint(self, p):
        self.update(p.lat, p.lon)

    def update(self, lat, lon):
        self.top_left.set_long_if_less(lon)
        self.top_left.set_lat_if_greater(lat)

        self.bottom_right.set_long_if_greater(lon)
        self.bottom_right.set_lat_if_less(lat)

    def __str__(self):
        return f'{{top_left: {self.top_left}, bottom_right: {self.bottom_right}, width: {util.get_distance_string(self.get_width_in_feet())}, height: {util.get_distance_string(self.get_height_in_feet())}}}'

    def get_lat_delta(self):
        return abs(abs(self.top_left.lat) - abs(self.bottom_right.lat))

    def get_long_delta(self):
        return abs(abs(self.top_left.lon) - abs(self.bottom_right.lon))

    def get_width_in_feet(self):
        return int(util.haversine_distance(0, self.top_left.lon, 0, self.bottom_right.lon))

    def get_height_in_feet(self):
        return int(util.haversine_distance(self.top_left.lat, 0, self.bottom_right.lat, 0))

    def get_aspect_ratio(self):
        return self.get_long_delta() / self.get_lat_delta()

    def get_diagonal_in_feet(self):
        a = self.get_width_in_feet()
        b = self.get_height_in_feet()

        return int(math.sqrt(a * a + b * b))

    def get_lat_fraction(self, lat, show_error=True):
        if lat < self.bottom_right.lat or lat > self.top_left.lat:
            if show_error:
                util.error('bottom_right.lat: {}, lat: {}, top_left.lat: {}'.format(self.bottom_right.lat, lat, self.top_left.lat))
            return None

        delta = self.get_lat_delta()
        return 1 - ((lat - self.bottom_right.lat) / delta)

    def get_long_fraction(self, lon, show_error=True):
        if lon > self.bottom_right.lon or lon < self.top_left.lon:
           if show_error:
                util.error('bottom_right.lon: {}, lon: {}, top_left.lon: {}'.format(self.bottom_right.lon, lon, self.top_left.lon))
           return None

        delta = self.get_long_delta()
        return 1 - ((abs(lon) - abs(self.bottom_right.lon)) / delta)

    def contains_point(self, p):
        return self.contains(p.lat, p.lon)

    def contains(self, lat, lon):
        return (lat >= self.bottom_right.lat and lat <= self.top_left.lat
            and lon <= self.bottom_right.lon and lon >= self.top_left.lon)


