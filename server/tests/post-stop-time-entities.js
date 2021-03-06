/*
A roundtrip test of server trip updates feed functionality.

First, we post a batch of stop time entities. Details are read
from local file stop-time-entities.csv.

Then, we download the current trip updates feed and iterate over the
contained entries, checking that timestamps are current and that
vehicle IDs start with `pr-test-vehicle-id` (this is useful to
e.g. weed out updates sent as part of different tests).

Finally, we compare a sorted list of stop time details posted with a sorted
list of stop time details received in the feed. If the lists aren't identical,
the test fails.
*/


const util = require('../app-engine/static/gtfs-rt-util')
const GtfsRealtimeBindings = require('gtfs-realtime-bindings');
const CSVReader = require('./csv')
const testutil = require('./test-util');

function serialize(obj, fields) {
    let s = '';

    for (f of fields) {
        const v = obj[f];

        if (v) {
            if (s.length > 0) s += ',';
            s += v;
        }
    }

    return s;
}

async function postVehiclePosition(signatureKey, agencyID, vehicleID, url) {
    let data = {
        uuid: 'test',
        agent: 'node',
        timestamp: 0,
        lat: 0,
        long: 0,
        speed: 0,
        heading: 0,
        accuracy: 0,
        version: 'test'
    };

    data['trip-id'] = 'test';
    data['agency-id'] = agencyID;
    data['pos-timestamp'] = 'test';

    data['vehicle-id'] = vehicleID;

    data['timestamp'] = testutil.getEpochSeconds();

    util.log('- data: ' + JSON.stringify(data));

    await util.signAndPost(data, signatureKey, url + '/new-pos-sig');
}

async function test(baseUrl, agencyID, ecdsaVarName) {
    util.log('starting trip updates test...');
    util.log(`- baseUrl: ${baseUrl}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await testutil.getSignatureKey(ecdsaVarName);

    const SER_FIELDS = ['trip_id', 'stop_sequence', 'delay', 'vehicle_id'];
    const reader = new CSVReader('stop-time-entities.csv', true);
    let map = {};
    let stlist = [];
    let now = null;

    for (;;) {
        let ste = reader.getNextLine();
        if (ste == null) break;

        ste.delay = parseInt(ste.delay);
        ste.stop_sequence = parseInt(ste.stop_sequence);

        //util.log(`-- ste: ${JSON.stringify(ste)}`);

        now = Math.round(Date.now() / 1000);

        ste['timestamp'] = now;
        ste['agency_id'] = agencyID;

        stlist.push(ste);
        //updates.push(serialize(ste, SER_FIELDS).toLowerCase());
        map[ste.trip_id + '-' + ste.stop_sequence] = ste;
    }

    //util.log(`- map: ${JSON.stringify(map, null, 4)}`);

    const data = {
        agency_id: agencyID,
        stop_time_entities: stlist
    };

    util.signAndPost(data, signatureKey, baseUrl + '/new-stop-entities');
    util.log(`+ posted stop time entities, sleeping for a few...`);
    await testutil.verboseSleep(5);
    await postVehiclePosition(signatureKey, agencyID, 'flush', baseUrl);
    await testutil.verboseSleep(2);

    const updates = Object.values(map);
    //util.log(`- updates: ${JSON.stringify(updates, null, 4)}`);

    for (let i=0; i<updates.length; i++) {
        updates[i] = serialize(updates[i], SER_FIELDS);
    }

    updates.sort();
    util.log(`- updates: ${JSON.stringify(updates, null, 4)}`);

    const body = await testutil.getResponseBody(baseUrl + '/trip-updates.pb?agency=' + agencyID);
    const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(body);
    now = Math.round(Date.now() / 1000);
    let entries = [];

    feed.entity.forEach(function(entity) {
        //util.log(`-- entity: ${JSON.stringify(entity)}`);

        let trip_id = null;
        let vehicle_id = null;
        let timestamp = null;

        if (!entity.tripUpdate) return;

        if (entity.tripUpdate.trip && entity.tripUpdate.trip.tripId) {
            trip_id = entity.tripUpdate.trip.tripId;
        }

        if (entity.tripUpdate.vehicle && entity.tripUpdate.vehicle.label) {
            vehicle_id = entity.tripUpdate.vehicle.label;

            if (vehicle_id && !vehicle_id.startsWith('pr-test-vehicle-id-')) {
                return;
            }
        }

        if (entity.tripUpdate.timestamp) {
            timestamp = entity.tripUpdate.timestamp;

            if (timestamp && Math.abs(now - timestamp) > 60) {
                return;
            }
        }

        if (entity.tripUpdate.stopTimeUpdate) {
            for (let stu of entity.tripUpdate.stopTimeUpdate) {
                let stop_sequence = null;
                let delay = null;

                if (stu.stopSequence) {
                    stop_sequence = stu.stopSequence;
                }

                if (stu.arrival && stu.arrival.delay) {
                    delay = stu.arrival.delay;
                }

                if (trip_id && stop_sequence && delay && vehicle_id && timestamp) {
                    let obj = {
                        trip_id: trip_id,
                        stop_sequence: stop_sequence,
                        delay: delay,
                        vehicle_id: vehicle_id,
                        timestamp: timestamp
                    };

                    const s = serialize(obj, SER_FIELDS);
                    //util.log(`-- s: ${s}`);
                    entries.push(s);
                }
            }
        }
    });

    entries.sort();
    util.log(`- entries: ${JSON.stringify(entries, null, 4)}`);

    if (updates.length !== entries.length) {
        throw `expected ${updates.length} feed entries but got ${entries.length}`;
    }

    for (let i=0; i<updates.length; i++) {
        if (updates[i] !== entries[i]) {
            throw `expected trip update data '${updates[i]}' but got '${entries[i]}'`;
        }
    }

    util.log('---> test succeeded');
}

const args = process.argv.slice(2);

let url = null;
let agencyID = null;
let ecdsaVarName = null;

for(let j = 0; j < args.length; j++){
    if((args[j] === '-u' || args[j] === '--url') && j + 1 < args.length){
        url = args[j+1];
        j++;
        continue;
    }
    if((args[j] === '-a' || args[j] === '--agency-id') && j + 1 < args.length){
        agencyID = args[j+1];
        j++;
        continue;
    }
    if((args[j] === '-e' || args[j] === '--ecdsa-var-name') && j + 1 < args.length){
        ecdsaVarName = args[j+1];
        j++;
        continue;
    }
}

if (url === null || agencyID === null || ecdsaVarName === null) {
    util.log(`usage: ${testutil.getBaseName(args[0])} -u <server-base-url> -a <agency-id> -e <ecdsa-var-name>`);
    process.exit(1);
}

test(url, agencyID, ecdsaVarName);
