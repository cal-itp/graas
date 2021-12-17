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
        return util.get_haversine_distance(self.lat, self.lon, p.lat, p.lon)

    def get_distance_from_lat_long(self, plat, plon):
        return util.get_haversine_distance(self.lat, self.lon, plat, plon)

    def __str__(self):
        return '(' + str(self.lat) + ', ' + str(self.lon) + ')'
