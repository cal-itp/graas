from google.cloud import datastore
from google.transit import gtfs_realtime_pb2
import time

import util

MAX_LIFE = 30 * 60 # 30 minutes in seconds

last_alert_purge = 0

cause_map = {
    'Unknown Cause':     gtfs_realtime_pb2.Alert.Cause.UNKNOWN_CAUSE,
    'Other Cause':       gtfs_realtime_pb2.Alert.Cause.OTHER_CAUSE,
    'Technical Problem': gtfs_realtime_pb2.Alert.Cause.TECHNICAL_PROBLEM,
    'Strike':            gtfs_realtime_pb2.Alert.Cause.STRIKE,
    'Demonstration':     gtfs_realtime_pb2.Alert.Cause.DEMONSTRATION,
    'Accident':          gtfs_realtime_pb2.Alert.Cause.ACCIDENT,
    'Holiday':           gtfs_realtime_pb2.Alert.Cause.HOLIDAY,
    'Weather':           gtfs_realtime_pb2.Alert.Cause.WEATHER,
    'Maintenance':       gtfs_realtime_pb2.Alert.Cause.MAINTENANCE,
    'Construction':      gtfs_realtime_pb2.Alert.Cause.CONSTRUCTION,
    'Police Activity':   gtfs_realtime_pb2.Alert.Cause.POLICE_ACTIVITY,
    'Medical Emergency': gtfs_realtime_pb2.Alert.Cause.MEDICAL_EMERGENCY
}

effect_map = {
    'Unknown Effect':     gtfs_realtime_pb2.Alert.Effect.UNKNOWN_EFFECT,
    'No Service':         gtfs_realtime_pb2.Alert.Effect.NO_SERVICE,
    'Reduced Service':    gtfs_realtime_pb2.Alert.Effect.REDUCED_SERVICE,
    'Significant Delays': gtfs_realtime_pb2.Alert.Effect.SIGNIFICANT_DELAYS,
    'Detour':             gtfs_realtime_pb2.Alert.Effect.DETOUR,
    'Additional Service': gtfs_realtime_pb2.Alert.Effect.ADDITIONAL_SERVICE,
    'Modified Service':   gtfs_realtime_pb2.Alert.Effect.MODIFIED_SERVICE,
    'Other Effect':       gtfs_realtime_pb2.Alert.Effect.OTHER_EFFECT,
    'Stop Moved':         gtfs_realtime_pb2.Alert.Effect.STOP_MOVED
}

def make_translated_string(text):
    translation = gtfs_realtime_pb2.TranslatedString.Translation()
    translation.text = text

    s = gtfs_realtime_pb2.TranslatedString()
    s.translation.append(translation)

    return s

def make_entity_selector(item):
    selector = gtfs_realtime_pb2.EntitySelector()

    if 'agency_id' in item:
        selector.agency_id = item['agency_id']

    if 'route_id' in item:
        selector.route_id = item['route_id']

    if 'stop_id' in item:
        selector.stop_id = item['stop_id']

    if 'trip_id' in item:
        trip = gtfs_realtime_pb2.TripDescriptor()
        trip.trip_id = item['trip_id']
        selector.trip.CopyFrom(trip)

    return selector

def make_alert(id, item):
    alert = gtfs_realtime_pb2.Alert();

    alert.informed_entity.append(make_entity_selector(item))

    if 'time_start' in item and 'time_stop' in item:
        range = gtfs_realtime_pb2.TimeRange()
        range.start = int(item['time_start'])
        range.end = int(item['time_stop'])
        alert.active_period.append(range)

    if 'cause' in item:
        alert.cause = cause_map[item['cause']]

    if 'effect' in item:
        alert.effect = effect_map[item['effect']]

    if 'header' in item:
        alert.header_text.CopyFrom(make_translated_string(item['header']))

    if 'description' in item:
        alert.description_text.CopyFrom(make_translated_string(item['description']))

    if 'url' in item:
        alert.url.CopyFrom(make_translated_string(item['url']))

    entity = gtfs_realtime_pb2.FeedEntity()
    entity.id = str(id)
    entity.alert.CopyFrom(alert)

    return entity

def make_position(id, lat, lon, bearing, speed, trip_id, timestamp):
    trip = gtfs_realtime_pb2.TripDescriptor()
    trip.trip_id = trip_id

    vehicle = gtfs_realtime_pb2.VehicleDescriptor()
    vehicle.label = id

    pos = gtfs_realtime_pb2.Position()
    pos.latitude = lat
    pos.longitude = lon
    pos.bearing = bearing
    pos.speed = speed

    vp = gtfs_realtime_pb2.VehiclePosition();
    vp.timestamp = timestamp
    vp.trip.CopyFrom(trip)
    vp.vehicle.CopyFrom(vehicle)
    vp.position.CopyFrom(pos)

    entity = gtfs_realtime_pb2.FeedEntity()
    entity.id = id
    entity.vehicle.CopyFrom(vp)

    return entity

def alert_is_current(alert):
    if not('time_start' in alert and 'time_stop' in alert):
        return False
    now = int(round(time.time()))
    return now >= int(alert['time_start']) and now < int(alert['time_stop'])

def purge_old_alerts(datastore_client):
    global last_alert_purge
    print('purge_old_messages')

    day = int(round(time.time()) / 86400)
    print('- day: ' + str(day))
    print('- last_alert_purge: ' + str(last_alert_purge))

    if day == last_alert_purge:
        return

    seconds = int(round(time.time()))
    print('- seconds: ' + str(seconds))

    query = datastore_client.query(kind="alert")
    query.add_filter("time_stop", "<", seconds)
    results = list(query.fetch())
    key_list = []

    for alert in results:
        print('-- alert: ' + str(alert))
        key_list.append(alert.key)

    print('- key_list: ' + str(key_list))

    datastore_client.delete_multi(key_list)
    last_alert_purge = day


def get_alert_feed(datastore_client, agency):
    print('get_alert_feed')
    print('- agency: ' + agency)

    purge_old_alerts(datastore_client)
    now = int(round(time.time()))

    query = datastore_client.query(kind="alert")
    query.add_filter("agency_key", "=", agency)
    query.order = ["-time_stamp"]

    results = list(query.fetch(limit=20))

    header = gtfs_realtime_pb2.FeedHeader()
    header.gtfs_realtime_version = '2.0'
    header.timestamp = now

    feed = gtfs_realtime_pb2.FeedMessage()
    feed.header.CopyFrom(header)

    count = 1

    for item in results:
        if alert_is_current(item):
            print('-- ' + str(item))
            feed.entity.append(make_alert(count, item))
            count += 1

    return feed.SerializeToString()

def get_position_feed(saved_position_feed, saved_position_feed_millis, vehicle_map, agency):
    millis = saved_position_feed_millis.get(agency, 0)
    feed = saved_position_feed.get(agency, None)

    if millis > 0 and util.get_current_time_millis() - millis <= 1000:
        print('+ returning cached feed message')
        return feed

    header = gtfs_realtime_pb2.FeedHeader()
    header.gtfs_realtime_version = '2.0'
    header.timestamp = int(round(time.time()))

    feed = gtfs_realtime_pb2.FeedMessage()
    feed.header.CopyFrom(header)

    if vehicle_map:
        for id in list(vehicle_map):
            vehicle = vehicle_map[id];

            vts = int(vehicle['timestamp'])
            now = round(util.get_current_time_millis() / 1000)
            delta = now - vts

            if delta >= MAX_LIFE:
                del vehicle_map[id]
            else:
                feed.entity.append(make_position(
                    id,
                    float(vehicle['lat']),
                    float(vehicle['long']),
                    float(vehicle['heading']),
                    float(vehicle['speed']),
                    vehicle['trip-id'],
                    int(vehicle['timestamp'])
                ));

    saved_position_feed[agency] = feed.SerializeToString()
    saved_position_feed_millis[agency] = util.get_current_time_millis()
    print('+ created new feed message at ' + str(saved_position_feed_millis[agency]))

    return saved_position_feed[agency]

def add_alert(datastore_client, alert):
    print('add_alert()')
    print('- alert: ' + str(alert))

    if not('time_start' in alert and 'time_stop' in alert):
        print('alert doesn\'t have valid time range, discarding')
        return

    if not('agency_id' in alert or 'route_id' in alert or 'trip_id' in alert or 'stop_id' in alert):
        print('alert doesn\'t have an affected entity, discarding')
        return

    entity = datastore.Entity(key=datastore_client.key('alert'))
    obj = {}

    obj['time_stamp'] = util.get_current_time_millis()

    for field in alert:
        print('-- ' + str(field))
        obj[str(field)] = alert[str(field)]

    entity.update(obj)
    datastore_client.put(entity)
    print('+ wrote alert')

def add_position(datastore_client, pos):
    entity = datastore.Entity(key=datastore_client.key('position'))
    entity.update(pos)
    datastore_client.put(entity)

def handle_pos_update(datastore_client, timestamp_map, agency_map, position_lock, data):
    data['rcv-timestamp'] = int(round(time.time()))

    if data['uuid'] != 'replay':
        add_position(datastore_client, data)

    prev_timestamp = timestamp_map.get(data['uuid'], None)

    if prev_timestamp and int(data['timestamp']) < int(prev_timestamp):
        return '{"command": "new-pos", "status": "out-of-sequence"}'

    timestamp_map[data['uuid']] = data['timestamp']

    position_lock.acquire();
    agency = data['agency-id'];
    vehicle_map = agency_map.get(agency, None)
    if not vehicle_map:
        vehicle_map = {}
        agency_map[agency] = vehicle_map
    vehicle_id = data['vehicle-id'];
    print('- vehicle_id: ' + vehicle_id)
    vehicle_map[vehicle_id] = data
    position_lock.release()
