import inference
import sys
import time
import util
from area import Area
from shapepoint import ShapePoint

def main(args):
    #area = Area(ShapePoint(34.902, -120.45993), ShapePoint(34.41789, -119.6852))

    start = util.get_current_time_millis()
    inf = inference.TripInference(args[0], args[1], 15)
    print(f'- inference.TripInference.VERSION: {inference.TripInference.VERSION}')
    elapsed = util.get_current_time_millis() - start

    print()
    print(f'- elapsed: {elapsed}')

    table = inf.grid.table
    for key in table:
        print(f'- key: {key}')
        segment_list = table[key]
        #print(f'- segment_list: {segment_list}')
        for segment in segment_list:
            #print(f'-- segment.id: {segment.id}')
            p = util.get_random_point(segment.bounding_box)
            seconds = util.get_random_int(segment.start_time, segment.end_time)
            score = segment.get_score(p.lat, p.lon, seconds, args[0])
            print(f'-- segment.id: {segment.id}, score: {score}')


            trip_id = inf.get_trip_id(p.lat, p.lon, seconds)
            print(f'- trip_id: {trip_id}')

    while True:
        seconds = util.get_seconds_since_midnight()
        p = util.get_random_point(inf.area)
        grid_index = inf.grid.get_index(p.lat, p.lon)
        util.debug(f'current location: lat={p.lat} long={p.lon} grid_index={grid_index}')
        trip_id = inf.get_trip_id(p.lat, p.lon, seconds)
        print(f'- trip_id: {trip_id}')
        time.sleep(1)

if __name__ == '__main__':
    main(sys.argv[1:])