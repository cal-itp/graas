from google.transit import gtfs_realtime_pb2
import sys
import time
import random

def make_entity(id, lat, lon):
    trip = gtfs_realtime_pb2.TripDescriptor()
    trip.trip_id = "6045503";

    #print('trip: ', trip)

    vehicle = gtfs_realtime_pb2.VehicleDescriptor()
    vehicle.label = id;

    #print('vehicle: ', vehicle)

    pos = gtfs_realtime_pb2.Position()
    pos.latitude = lat
    pos.longitude = lon
    pos.bearing = random.random() * 360
    pos.speed = (int(round(random.random() * 30)))

    #print('pos: ', pos)

    vp = gtfs_realtime_pb2.VehiclePosition();
    vp.timestamp = int(round(time.time()))
    vp.trip.CopyFrom(trip)
    vp.vehicle.CopyFrom(vehicle)
    vp.position.CopyFrom(pos)

    # print('vehicle: ', vp)

    entity = gtfs_realtime_pb2.FeedEntity()
    entity.id = id;
    entity.vehicle.CopyFrom(vp);

    return entity;

def main(argv):
    # print('main()')

    header = gtfs_realtime_pb2.FeedHeader()
    header.gtfs_realtime_version = '2.0'
    header.timestamp = int(round(time.time()))

    # print('header: ', header)

    feed = gtfs_realtime_pb2.FeedMessage()
    feed.header.CopyFrom(header)

    for x in range(5):
        id = str(1024 + x)
        lat = 1.1 * (x + 1)
        lon = 2.2 * (x + 1)
        feed.entity.append(make_entity(id, lat, lon));

    # print('feed: ', feed)

    file = open("test.pb", "wb")
    file.write(feed.SerializeToString())
    file.close()

    # print()
    # print('dir(feed): ', dir(feed))
    # print()
    # print('feed.entity: ', type(feed.entity))
    # print()
    # print('feed.entity: ', dir(feed.entity))

if __name__ == '__main__':
   main(sys.argv[1:])
