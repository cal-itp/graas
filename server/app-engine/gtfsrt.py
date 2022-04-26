"""GTFS-rt server business logic and some utility functions regarding DB access and protobuf structures.

"""
from google.cloud import datastore
from google.transit import gtfs_realtime_pb2
import json
import time
import traceback
from cache import Cache
from entity_key_cache import EntityKeyCache
import util

MAX_LIFE = 30 * 60 # 30 minutes in seconds
MAX_BLOCK_STALE_MILLIS = 60 * 1000
WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000
DAY_SECONDS = 24 * 60 * 60

last_alert_purge = 0
block_map = {}
cache = Cache()
entity_key_cache = EntityKeyCache()

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
        alert.cause = cause_map.get(item['cause'], None)

    if 'effect' in item:
        alert.effect = effect_map.get(item['effect'], None)

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
    print('alert_is_current()')
    if not('time_start' in alert and 'time_stop' in alert):
        return False
    now = int(time.time())
    return now >= int(alert['time_start']) and now < int(alert['time_stop'])

def purge_old_alerts(datastore_client):
    global last_alert_purge
    print('purge_old_messages')

    day = int(time.time() / DAY_SECONDS)
    print('- day: ' + str(day))
    print('- last_alert_purge: ' + str(last_alert_purge))

    if day == last_alert_purge:
        return

    seconds = int(time.time())
    print('- seconds: ' + str(seconds))

    query = datastore_client.query(kind='alert')
    query.add_filter('time_stop', '<', seconds)
    results = list(query.fetch())
    key_list = []

    for alert in results:
        print('-- alert to purge: ' + str(alert))
        key_list.append(alert.key)

    print('- key_list: ' + str(key_list))

    datastore_client.delete_multi(key_list)
    last_alert_purge = day

def get_alert_feed(datastore_client, agency):
    """Assemble alert feed for an agency in the [protobuf format](https://developers.google.com/protocol-buffers).

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        agency (str): an agency ID.

    Returns:
        obj: alert feed in protobuf format

    """
    print('get_alert_feed()')
    print('- agency: ' + agency)

    name = agency + '-alert-feed'
    alert_feed = cache.get(name)

    if alert_feed is None:
        purge_old_alerts(datastore_client)
        now = int(time.time())

        query = datastore_client.query(kind='alert')
        query.add_filter('agency_key', '=', agency)
        query.order = ['-time_stamp']

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

        alert_feed = feed.SerializeToString()
        cache.add(name, alert_feed, 60)
    return alert_feed

def get_position_feed(datastore_client, agency):
    """Assemble position feed for an agency in the [protobuf format](https://developers.google.com/protocol-buffers), or return a recent cached instance.

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        agency (str): an agency ID.

    Returns:
        obj: position feed in protobuf format

    """
    name = agency + '-pos-feed'
    pos_feed = cache.get(name)

    if pos_feed is None:
        header = gtfs_realtime_pb2.FeedHeader()
        header.gtfs_realtime_version = '2.0'
        header.timestamp = int(time.time())

        feed = gtfs_realtime_pb2.FeedMessage()
        feed.header.CopyFrom(header)

        query = datastore_client.query(kind='current-position')
        query.add_filter('agency-id', '=', agency)

        results = list(query.fetch())
        print(f'- results: {results}')

        for pos in results:
            vts = int(pos['timestamp'])
            now = int(time.time())
            delta = now - vts

            if delta >= MAX_LIFE:
                datastore_client.delete(pos.key)
            else:
                feed.entity.append(make_position(
                    pos['vehicle-id'],
                    float(pos['lat']),
                    float(pos['long']),
                    float(pos['heading']),
                    float(pos['speed']),
                    pos['trip-id'],
                    int(pos['timestamp'])
                ));

        pos_feed = feed.SerializeToString()
        cache.add(name, pos_feed, 3)

    return pos_feed

def add_alert(datastore_client, alert):
    print('add_alert()')
    print('- alert: ' + str(alert))

    if not 'agency_key' in alert:
        print('alert doesn\'t have associated agency, discarding')
        return

    if not('time_start' in alert and 'time_stop' in alert):
        print('alert doesn\'t have valid time range, discarding')
        return

    if not('agency_id' in alert or 'route_id' in alert or 'trip_id' in alert or 'stop_id' in alert):
        print('alert doesn\'t have an affected entity, discarding')
        return

    entity = datastore.Entity(key=datastore_client.key('alert'))
    alert['time_stamp'] = int(time.time())

    entity.update(alert)
    datastore_client.put(entity)
    print('+ wrote alert')

def add_position(datastore_client, pos):
    entity = datastore.Entity(key=datastore_client.key('position'))
    entity.update(pos)
    datastore_client.put(entity)

def load_block_collection(datastore_client, block_metadata):
    global block_map

    print(f'load_block_collection()')
    print(f'- block_metadata: {block_metadata}')

    if block_metadata is None:
        print(f'* block meta data is \'None\'')
        return

    block_list = datastore_client.get(block_metadata['block_list_key'])
    print(f'- block_list: {block_list}')
    agency_id = block_metadata['agency_id']
    print(f'- agency_id: {agency_id}')

    if block_list is None:
        print(f'* no block list for entity key \'{block_metadata["block_list_key"]}\'')
        return

    map = {}

    block_collection = {
        'agency_id': agency_id,
        'valid_date': block_metadata['valid_date'],
        'last_refresh': util.get_current_time_millis(),
        'blocks': map
    }

    blocks = json.loads(block_list['data'])
    print(f'- blocks: {blocks}')

    for b in blocks:
        print(f'-- b: {b}')
        map[b['vehicle_id']] = b

    print(f'- block_collection: {block_collection}')

    block_map[agency_id] = block_collection
    print(f'- block_map: {block_map}')

def load_block_metadata(datastore_client, agency_id, date_string = None):
    """Attempt to find and return block metadata for a specific agency ID and date.

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        agency_id (str): an agency ID.
        date_string (str): a date. Current date if omitted.

    Returns:
        obj: metadata instance if available, `None` otherwise

    """
    print(f'load_block_metadata()')
    print(f'- agency_id: {agency_id}')

    if date_string is None:
        date_string = util.get_yyyymmdd()

    query = datastore_client.query(kind='block-metadata')
    query.add_filter('agency_id', '=', agency_id)
    query.add_filter('valid_date', '=', date_string)
    query.order = ['-created']

    results = list(query.fetch(limit=1))
    print(f'- results: {results}')

    if len(results) == 0:
        print(f'* no matching block metadata for agency \'{agency_id}\' valid on {date_string}')
        return None

    return results[0]

def get_current_block_collection(datastore_client, agency_id):
    """Attempt to find the current block collection for a specific agency ID. Return from cache if present and fresh, consult DB otherwise.

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        agency_id (str): an agency ID.

    Returns:
        obj: the current block collection for `agency_id` if available, `None` otherwise

    """
    block_collection = block_map.get(agency_id, None)
    print(f'- block_collection: {block_collection}')
    now = util.get_current_time_millis()
    yyymmdd = util.get_yyyymmdd()

    if block_collection is None or yyymmdd != block_collection['valid_date']:
        print(f'+ block collection not found or mismatched validity period, reloading from DB')
        block_metadata = load_block_metadata(datastore_client, agency_id)
        load_block_collection(datastore_client, block_metadata)
        block_collection = block_map.get(agency_id, None)
    elif block_collection is not None and now - block_collection['last_refresh'] >= MAX_BLOCK_STALE_MILLIS:
        print(f'+ block collection may be stale, reloading block metadata from DB')
        block_metadata = load_block_metadata(datastore_client, agency_id)
        print(f'- block_metadata["created"]       : {block_metadata["created"]}')
        print(f'- block_collection["last_refresh"]: {block_collection["last_refresh"]}')
        if block_metadata['created'] > block_collection['last_refresh']:
            print(f'+ block collection IS stale, reloading from DB')
            load_block_collection(datastore_client, block_metadata)
            block_collection = block_map.get(agency_id, None)

    return block_collection

def get_trip_id(datastore_client, agency_id, vehicle_id):
    """Attempt to find a trip ID for a specific agency ID and vehicle ID and the current time.

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        agency_id (str): an agency ID.
        vehicle_id (str): a vehicle ID.

    Returns:
        str: a trip ID if one could be determined using block assignment data, `None` otherwise

    """
    print(f'get_trip_id()')
    print(f'- agency_id: {agency_id}')
    print(f'- vehicle_id: {vehicle_id}')

    block_collection = get_current_block_collection(datastore_client, agency_id)

    if block_collection is None:
        print(f'* no block collection found for agency {agency_id}')
        return None

    block = block_collection['blocks'].get(vehicle_id, None)
    if block is None:
        print(f'* no block data found for vehicle {vehicle_id}@{agency_id}')
        return None

    day_seconds = util.get_seconds_since_midnight()
    for trip in block['trips']:
        print(f'++++++++++++++++++++++++++++')
        print(f'++ secs : {day_seconds}')
        print(f'++ start: {trip["start_time"]}')
        print(f'++ end  : {trip["end_time"]}')
        if day_seconds >= trip['start_time'] and day_seconds < trip['end_time']:
            return trip['id']

    return None

def handle_get_assignments(datastore_client, data):
    """Get a list of assigned block IDs and vehicle IDs for a specific agency and date.

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        data (obj): JSON data containing `agency_id` and `date` fields.

    Returns:
        array: list of assignments if everything is in order, an empty array otherwise

    """
    print(f'handle_get_assignments()')
    print(f'- data: {data}')

    agency_id = data.get('agency_id', None)
    date = data.get('date', None)

    if agency_id is None or date is None:
        print(f'* no agency or date given for assignments request')
        return []

    metadata = load_block_metadata(datastore_client, agency_id, date)

    if metadata is None:
        print(f'* no metadata found for assignments request')
        return []

    assignment_summary = datastore_client.get(metadata['assignment_summary_key'])

    if assignment_summary is None:
        print(f'* no record found for key {metadata["assignment_summary_key"]}')
        return []

    return assignment_summary['data']

def handle_block_collection(datastore_client, data):
    """Handle an incoming block collection. A collection has metadata like the associated agency ID, the date it is valid for, and the actual list of blocks, each of which has an ID and a list of contained trip IDs. The function checks if the collection contains the required data and discards it with an error message if it doesn't. Otherwise the data is structured for efficient access through caching and database and written to said database:
    - assignment summary: a shorthand list of which block ID is matched with which vehicle ID
    - block collection: a detailed list where each entry has a block id and a list of associated trip IDs
    - metadata: an agency ID, a valid date and DB keys to and `assignment-summary` record and a `block-list` record

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        data (obj): JSON data containing the agency ID, valid date and actual block list.

    Returns:
        str: `ok` if everything went well, otherwise an error message

    """
    print(f'handle_block_collection()')
    print(f'- data: {data}')

    now = util.get_current_time_millis()
    agency_id = data.get('agency_id', None)
    valid_date = data.get('valid_date', None)
    blocks = data.get('blocks', None)

    if agency_id is None:
        print(f'* block collection is missing \'agency_id\' field')
        return 'error: missing agency ID'

    if valid_date is None:
        print(f'* block collection is missing \'valid_date\' field')
        return 'error: missing \'valid_date\' field'

    if blocks is None:
        print(f'* block collection is missing \'blocks\' field')
        return 'error: no blocks'

    try:
        assignments = []

        for block in blocks:
            assignments.append({
                'block_id': block['id'],
                'vehicle_id': block['vehicle_id']
            })

        assignment_summary = {
            'data': json.dumps(assignments)
        }
        print(f'- assignment_summary: {assignment_summary}')

        entity1 = datastore.Entity(key=datastore_client.key('assignment-summary'), exclude_from_indexes = ['data'])
        entity1.update(assignment_summary)
        datastore_client.put(entity1)

        block_list = {
            'data': json.dumps(blocks)
        }
        print(f'- block_list: {block_list}')

        entity2 = datastore.Entity(key=datastore_client.key('block-list'), exclude_from_indexes = ['data'])
        entity2.update(block_list)
        datastore_client.put(entity2)

        block_metadata = {
            'created': now,
            'agency_id': agency_id,
            'valid_date': valid_date,
            'assignment_summary_key': entity1.key,
            'block_list_key': entity2.key,
        }
        print(f'- block_metadata: {block_metadata}')

        entity = datastore.Entity(key=datastore_client.key('block-metadata'))
        entity.update(block_metadata)
        datastore_client.put(entity)

        query = datastore_client.query(kind='block-metadata')
        query.add_filter('created', '<', now - WEEK_MILLIS)

        results = list(query.fetch())
        print(f'- results: {results}')

        keys = []

        for r in results:
            keys.append(r.key)

            if 'block_list_key' in r:
                keys.append(r['block_list_key'])

            if 'assignment_summary_key' in r:
                keys.append(r['assignment_summary_key'])

        print(f'- keys: {keys}')
        datastore_client.delete_multi(keys)
    except:
        print(traceback.format_exc())
        return 'error: exception'

    return 'ok'

def handle_pos_update(datastore_client, data):
    """Handle an incoming vehicle postion update. If the update is missing a trip ID, check if we have block assignment data to let us backfill the ID. Write update to DB and update internal data structures with the received data.

    Args:
        datastore_client (obj): reference to google cloud datastore instance.
        data (dict): details of the position update (lat, long, speed, heading, etc).
    """

    agencyID = data.get('agency-id', None)
    vehicleID = data.get('vehicle-id', None)
    timestamp = data.get('timestamp', None)

    if agencyID is None or vehicleID is None or timestamp is None:
        print(f'* position update has no agency ID or vehicle ID or timestamp, discarding: {data}')
        return

    data['rcv-timestamp'] = int(time.time())

    if data.get('trip-id', None) is None:
        trip_id = get_trip_id(
            datastore_client,
            agencyID,
            vehicleID
        )
        print(f'- trip_id: {trip_id}')

        if trip_id is None:
            print(f'* discarding position update without trip ID: {data}')
            return

        data['trip_id'] = trip_id

    if data['uuid'] != 'replay':
        add_position(datastore_client, data)

    name = agencyID + '-' + vehicleID + '-cur-pos'
    entity_key = entity_key_cache.get(name)

    if entity_key is None:
        query = datastore_client.query(kind='current-position')
        query.add_filter('agency-id', '=', agencyID)
        query.add_filter('vehicle-id', '=', vehicleID)
        query.order = ['-timestamp']

        results = list(query.fetch())

        if len(results) == 0:
            entity = datastore.Entity(key=datastore_client.key('current-position'))
            entity['timestamp'] = 0
            datastore_client.put(entity)
            entity_key_cache.add(name, entity.key)
            entity_key = entity.key
        else:
            entity_key_cache.add(name, results[0].key)
            entity_key = results[0].key

    entity = datastore_client.get(entity_key)

    if entity is None:
            print(f'* invalid entity key: {entity_key}, discarding pos update and key')
            entity_key_cache.remove(name)
            return

    if timestamp <= entity['timestamp']:
            print(f'* pos update not newer than last update, discarding')
            return

    entity.update(data)
    datastore_client.put(entity)
