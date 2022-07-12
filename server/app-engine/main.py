"""

Implement functionality for a GTFS-rt server. GTFS-rt spec is here: https://developers.google.com/transit/gtfs-realtime

The server currently supports two of the three feed types from the spec: Vehicle Positions and Service Alerts.

An implementation for Trip Updates is underway. For authentication, updates have to be signed with the private key
for an agency. Public agency keys need to be added to the system database. The server will read public keys from
the database at startup.


Clients can submit alert updates for individual feeds through the following endpoint:

    /post-alert:
    {
        "data": {
            "agency_key": ...,  # id of submitting agency
            "cause": ...,       # enumerated cause value
            "description": ..., # alert description
            "effect": ...,      # enumerated effect value
            "header": ...,      # summary
            "time_stamp": ...,  # when the message was created
            "time_start": ...,  # start of valid time period
            "time_stop": ...,   # end of valid time period
            "url": ...,         # optional url containing more info on alert
        # At least one of the following 4 must be included, to specify which "entities" the alert applies to:
        # In order to omit, pass a null value or don't include at all
        # Note that route_type is not currently offerred as an entity option
            "agency_id": ...,   # specific sub-agency the alert applies to
            "trip_id": ...,     # which trip the alert applies to
            "stop_id": ...,     # which stop the alert applies to
            "route_id": ...,    # which route the alert applies to
        },
        "sig": ...              # base-64 encoded ECDSA signature of "data" object
    }

Clients can submit position updates for individual feeds through the following endpoint:

    /new-pos-sig:
    {
        "data": {
            "accuracy": ...,      # accuracy of update
            "agency-id": ...,     # id of submitting agency
            "agent": ...,         # user agent of client
            "driver-name": ...,   # name of driver
            "heading": ...,       # vhicle heading in degrees
            "lat": ...,           # lattitide of vehicle
            "long": ...,          # longitude of vehicle
            "speed": ...,         # vehicle speed
            "timestamp": ...,     # when update was generated
            "trip-id": ...,       # trip id
            "uuid": ...,          # unique user id
            "vehicle-id": ...,    # vehicle id
            "version": ...        # client version
        },
        "sig": ...                # base-64 encoded ECDSA signature of "data" object
    }

Consumers can retrieve current service alerts for an agency through the following endpoints:

    /service-alerts.pb?agency=MY-AGENCY-ID

Authenticated agencies can retrieve current service alerts, without caching, through the following endpoint:
    /service-alert-ui.pb

Consumers can retrieve current vehicle positions for an agency through the following endpoints:

    /vehicle-positions.pb?agency=MY-AGENCY-ID

Consumers can retrieve current trip updates for an agency through the following endpoints:

    /trip-updates.pb?agency=MY-AGENCY-ID

A web-based client for collecting and submitting vehicle position updates is available at this endpoint:

    /

*Note that at first ever startup, client needs to be initialized with QR code that contains agency ID and private key.*

A web-based client for bulk-assigning GTFS blocks to vehicles is available at this endpoint:

    /dispatch-ui

*Note that at first ever startup, client needs to be initialized by entering the agency ID and private key.*

A web-based client for viewing, creating and deleting service alerts is available at this endpoint:

    /service-alert-ui

*Note that at first ever startup, client needs to be initialized by entering the agency ID and private key.*

See how-to-run.txt in this folder for details on how to run the server both locally and in the cloud.

"""

# system imports
import datetime
import json
import sys
import time
import warnings
from flask import abort, Flask, Response, request
import threading
import socket

# local imports
import gtfsrt
import util

MAX_IP_CACHE_MILLIS = 20 * 60 * 1000 # 20 minutes in milliseconds
INVALID_GPS = 9999
warnings.filterwarnings("ignore", "Your application has authenticated using end user credentials")

app = Flask(__name__)

verified_map = {}
verified_map_millis = 0

@app.route('/service-alerts.pb')
def service_alerts():
    print('/service-alerts.pb')

    agency = request.args.get('agency')
    print('- agency: ' + agency)
    if agency is None:
        return 'No agency given', 400

    use_cache = True;
    include_future_alerts = False;

    feed = gtfsrt.get_alert_feed(
        util.datastore_client,
        agency,
        use_cache,
        include_future_alerts
    )

    return Response(feed, mimetype='application/octet-stream')

@app.route('/service-alert-ui.pb', methods=['POST'])
def service_alerts_no_cache():
    print('//service-alert-ui.pb')

    data = request.json['data'];
    sig = request.json['sig'];

    data_str = json.dumps(data,separators=(',',':'))
    print('- data_str: ' + data_str)

    agency = data['agency_key'];
    print('- agency: ' + agency)

    verified = util.verify_signature(agency, data_str, sig)
    print('- verified: ' + str(verified))

    if not verified:
        print('*** could not verify signature for new alert, discarding')
        return Response('{"command": "/service-alert-ui.pb", "status": "unverified"}', mimetype='application/json')

    use_cache = False;
    include_future_alerts = True;

    feed = gtfsrt.get_alert_feed(
        util.datastore_client,
        agency,
        use_cache,
        include_future_alerts
    )

    return Response(feed, mimetype='application/octet-stream')

@app.route('/vehicle-positions.pb')
def vehicle_positions():
    print('/vehicle-positions.pb')
    agency = request.args.get('agency', None)
    print('- agency: ' + str(agency))

    if agency is None:
        return 'No agency given', 400

    feed = gtfsrt.get_position_feed(
        util.datastore_client,
        agency
    )

    return Response(feed, mimetype='application/octet-stream')

@app.route('/trip-updates.pb')
def trip_updates():
    print('/trip-updates.pb')
    agency = request.args.get('agency', None)
    print('- agency: ' + str(agency))

    if agency is None:
        return 'No agency given', 400

    feed = gtfsrt.get_trip_updates_feed(
        util.datastore_client,
        agency
    )

    return Response(feed, mimetype='application/octet-stream')

@app.route('/')
@app.route('/web-app-staging') # for historic reasons
def staging():
    print('/')

    fn = 'static/index.html'
    content = util.get_file(fn, 'r')
    resp = Response(content, mimetype='text/html')
    resp.headers['Last-Modified'] = util.get_mtime(fn);
    return resp

@app.route('/dispatch-ui')
@app.route('/show-assignments-only')
def dispatch_ui():
    print(f'{request.path}')

    fn = 'static/vehicle-assignment-index.html'
    content = util.get_file(fn, 'r')
    resp = Response(content, mimetype='text/html')
    resp.headers['Last-Modified'] = util.get_mtime(fn);
    return resp

@app.route('/service-alert-ui')
def service_alert_ui():
    print(f'{request.path}')

    fn = 'static/service-alert-ui.html'
    content = util.get_file(fn, 'r')
    resp = Response(content, mimetype='text/html')
    resp.headers['Last-Modified'] = util.get_mtime(fn);
    return resp

@app.route('/test')
def test():
    fn = 'static/device-test.html'
    content = util.get_file(fn, 'rb')
    return Response(content, mimetype='text/html')

# for cloudless evaluation testing
@app.route('/test/<filename>')
def test_files(filename):
    print(f'test_files()')
    print(f'- filename: {filename}')

    if filename == 'vehicle-ids.json':
        content = util.get_file('static/ad-hoc-vehicle-ids.json', 'r')
        return Response(content, mimetype='application/json')
    elif filename == 'agency-params.json':
        content = util.get_file('static/ad-hoc-agency-params.json', 'r')
        return Response(content, mimetype='application/json')
    elif filename.startswith('blocks-'):
        content = util.get_file('static/ad-hoc-blocks.json', 'r')
        return Response(content, mimetype='application/json')
    else:
        abort(404)

@app.route('/vibrate.mp3')
def vibrate():
    fn = 'static/vibrate.mp3'
    content = util.get_file(fn, 'rb')
    return Response(content, mimetype='audio/mpeg')

@app.route('/bus.png')
def bus():
    fn = 'static/bus.png'
    content = util.get_file(fn, 'rb')
    return Response(content, mimetype='image/png')

@app.route('/hello', methods=['POST'])
def hello():
    print('/hello')
    sig = request.json['sig']
    print('- sig: ' + sig)
    msg = json.dumps(request.json['msg'])
    print('- msg: ' + msg)
    agency_id = request.json.get('id', 'not found');

    if agency_id == 'not found':
        print('*** no agency name found in request json')
        # 12/13/21 removing logic that tries verifying private key with every public key in util.key_map
        # This was expensive and resolved by passing the agency_id into every request.
        # This removal would only impact one agency: CAE, if they tried running on tablets. There's no reason they would do so.
    return '{"agencyID": "' + agency_id + '"}'

@app.route('/post-alert', methods=['POST'])
def post_alert():
    print('/post-alert')
    #print('- request.data: ' + str(request.data))
    #print('- request.json: ' + json.dumps(request.json))

    data = request.json['data'];
    sig = request.json['sig'];

    data_str = json.dumps(data,separators=(',',':'))
    print('- data_str: ' + data_str)

    agency = data['agency_key'];
    print('- agency: ' + agency)

    verified = util.verify_signature(agency, data_str, sig)
    print('- verified: ' + str(verified))

    if not verified:
        print('*** could not verify signature for new alert, discarding')
        return Response('{"command": "post-alert", "status": "unverified"}', mimetype='application/json')

    gtfsrt.add_alert(util.datastore_client, data)

    return Response('{"command": "post-alert", "status": "ok"}', mimetype='application/json')

@app.route('/delete-alert', methods=['POST'])
def delete_alert():
    print('/delete-alert')
    #print('- request.data: ' + str(request.data))
    #print('- request.json: ' + json.dumps(request.json))

    data = request.json['data'];
    sig = request.json['sig'];

    data_str = json.dumps(data,separators=(',',':'))
    print('- data_str: ' + data_str)

    agency = data['agency_key'];
    print('- agency: ' + agency)

    verified = util.verify_signature(agency, data_str, sig)
    print('- verified: ' + str(verified))

    if not verified:
        print('*** could not verify signature for delete request, discarding')
        return Response('{"command": "delete-alert", "status": "unverified"}', mimetype='application/json')

    gtfsrt.delete_alert(util.datastore_client, data)

    return Response('{"command": "delete-alert", "status": "ok"}', mimetype='application/json')

"""
Determine whether or not to accept an incoming user request.
Requests can be accepted one of two ways:
- if no previous requests from requestor IP have been verified, confirm
  signature for request data matches public key on file for 'agency_id'
- if previous request from same IP has been verified inside a set amount of time
"""
def verify_request(request, cmd):
    global verified_map, verified_map_millis

    # print('- request.data: ' + str(request.data))
    # print('- request.json: ' + json.dumps(request.json))

    data = request.json['data']
    sig = request.json['sig']

    agency = data.get('agency-id', None)
    if agency is None:
        agency = data.get('agency_id', None)
    if agency is None:
        print(f'*** can\'t verify signature without agency')
        return {
            'verified': False,
            'response': Response(f'{{"command": {cmd}, "status": "no agency"}}', mimetype='application/json')
        }

    print('- agency: ' + agency)

    data_str = json.dumps(data,separators=(',',':'))
    print('- data_str: ' + str(data_str))

    remote_ip = request.access_route[0]
    print("- remote_ip: " + str(remote_ip))

    now = util.get_current_time_millis()
    if now - verified_map_millis >= MAX_IP_CACHE_MILLIS:
        print('+ pruning verified map, size: ' + str(len(verified_map)))
        count = 0
        for ip in list(verified_map):
            count += 1
            if count <= 10:
                print('-- ip: ' + str(ip))
            millis = verified_map[ip];
            # print('++ delta ms: ' + str(now - millis))

            if now - millis >= MAX_IP_CACHE_MILLIS:
                del verified_map[ip]

        print('+ pruned size: ' + str(len(verified_map)))
        verified_map_millis = now

    millis = verified_map.get(remote_ip, None)

    if not millis:
        verified = util.verify_signature(agency, data_str, sig)
        print('- verified: ' + str(verified))

        if not verified:
            print(f'*** could not verify signature for command {cmd}, discarding')
            return {
                'verified': False,
                'response': Response(f'{{"command": {cmd}, "status": "unverified"}}', mimetype='application/json')
            }

    if str(remote_ip) != '127.0.0.1' and str(remote_ip) != 'localhost':
        verified_map[remote_ip] = util.get_current_time_millis()

    return {
        'verified': True
    }

@app.route('/block-collection', methods=['POST'])
def block_collection():
    print('/block-collection')

    result = verify_request(request, 'block-collection')
    if not result['verified']:
        return result['response']

    data = request.json['data']

    status = gtfsrt.handle_block_collection(
        util.datastore_client,
        data
    )

    return Response(f'{{"command": "block-collection", "status": "{status}"}}', mimetype='application/json')

@app.route('/get-assignments', methods=['POST'])
def get_assignments():
    print('/get-assignments')

    result = verify_request(request, 'get-assignments')
    if not result['verified']:
        return result['response']

    data = request.json['data']

    assignments = gtfsrt.handle_get_assignments(
        util.datastore_client,
        data
    )

    return Response(f'{{"command": "get-assignments", "assignments": {assignments}, "status": "ok"}}', mimetype='application/json')

@app.route('/new-stop-entities', methods=['POST'])
def new_stop_entities():
    print('/new-stop-entities')

    result = verify_request(request, 'new-stop-entities')
    if not result['verified']:
        return result['response']

    data = request.json['data']

    result = gtfsrt.handle_stop_entities(
        util.datastore_client,
        data
    )

    return Response(f'{{"command": "new-stop-entities", "status": "{result}"}}', mimetype='application/json')

@app.route('/new-pos-sig', methods=['POST'])
def new_pos_sig():
    print('/new-pos-sig')

    result = verify_request(request, 'new-pos')
    if not result['verified']:
        return result['response']

    data = request.json['data']
    lat = data.get('lat', None)
    ret = {}

    # lat/long values of 9999 indicate client inability to
    # retrieve actual position. Keep such updates out of
    # DB and and Vehicle Position streams
    if lat == INVALID_GPS:
        print('+ ignoring missing GPS update')
    else:
        #then = time.time()
        ret = gtfsrt.handle_pos_update(
            util.datastore_client,
            data
        )
        #print(f'- profile handle_pos_update(): {time.time() - then:.3f} seconds')

    obj = {
        'command': 'new-pos',
        'status': ret.get('status', 'ok')
    }

    if 'backfilled_trip_id' in ret:
        obj['backfilled_trip_id'] = ret['backfilled_trip_id']

    return Response(json.dumps(obj), mimetype='application/json')

if __name__ == '__main__':
    argc = len(sys.argv)
    certfile = None
    keyfile = None

    for i in range(argc):
        arg = sys.argv[i]

        if arg == '-c' and i < argc - 1:
            i += 1
            certfile = sys.argv[i]

        if arg == '-k' and i < argc - 1:
            i += 1
            keyfile = sys.argv[i]

    if keyfile == None or certfile == None:
        print(f'* usage: {sys.argv[0]} -c <cert-file> -k <key-file>')
        exit(1)

    # run https server locally with supplied credentials
    app.run(ssl_context=(certfile, keyfile), host = '127.0.0.1', port = 8080, debug = True, threaded = False)

    # run http server locally
    # app.run(host = '127.0.0.1', port = 8080, debug = True)
