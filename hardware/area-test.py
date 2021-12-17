from parse import parse
from gtfu import ShapePoint, Area, Util

f = open('lat-lon-pairs.txt', 'r')
lines = f.readlines()

area = Area()
list = []

for line in lines:
    line = line.strip()
    #print(f'- line: {line}')
    lat, lon = parse('({}, {})', line)
    lat = float(lat)
    lon = float(lon)
    print(f'- lat: {lat}')
    print(f'- lon: {lon}')

    list.append(ShapePoint(lat, lon))
    area.update(lat=lat, lon=lon)

    for i in range(len(list)):
        p = list[i]
        x, y = Util.lat_long_to_x_y(1080, 1080, area, p)
        print(f'-- {x} {y}')

    print(f'')
