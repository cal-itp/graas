import util

class Grid:
    def __init__(self, bounding_box, subdivisions):
        aspect_ratio = bounding_box.get_aspect_ratio()
        util.debug(f'grid: top_left={bounding_box.top_left} bottom_right={bounding_box.bottom_right} subdivisions={subdivisions}')
        self.bounding_box = bounding_box
        self.subdivisions = subdivisions
        self.table = {}

    def add_segment(self, segment, index):
        if index in self.table:
            list = self.table[index]
        else:
            list = []
            self.table[index] = list

        list.append(segment)

    def get_index(self, lat, lon):
        ### lat == y, lon == x?
        latfrac = self.bounding_box.get_lat_fraction(lat, False)
        if latfrac is None:
            return -1
        lonfrac = self.bounding_box.get_long_fraction(lon, False)
        if lonfrac is None:
            return -1
        index = (self.subdivisions * int(round(self.subdivisions * latfrac))
            + int(round(self.subdivisions * lonfrac)))
        return index

    def get_segment_list(self, lat, lon):
        index = self.get_index(lat, lon)

        if index in self.table:
            return self.table[index]
        else:
            return None

    def __str__(self):
        return '{\n  bounding_box: ' + str(self.bounding_box) + '\n  subdivisions: ' + str(self.subdivisions) + '\n  len(table): ' + str(len(self.table)) + '\n}'
