/*
A roundtrip test to ensure that vehicle position updates are properly sequenced,
including 1) discarding updates that are received with timestamps earlier than the
last recorded update, and 2) discarding updates with stale timestamps (>= 15 mins
as per https://support.google.com/transitpartners/answer/10104663?hl=en.)

First, generate a list of updates:
- for one vehicle, send updates that contain monotonically increasing timestamps,
  but all timestamps are stale
- for a second vehicle, send a list of updates where some timestamps are out of
  sequence

Then, we download the current vehicle position feed and iterate over the
contained vehicle entries, checking that vehicle IDs start with
`pr-seq-test-vehicle-id-` (this is useful to e.g. weed out updates sent
as part of different tests).

Finally, assert that the feed has no updates for 'pr-seq-test-vehicle-id-1',
and that the timestamp for the 'pr-seq-test-vehicle-id-2' entry matches the
highest value sent earlier.
*/

const GtfsRealtimeBindings = require('gtfs-realtime-bindings');
const util = require('../app-engine/static/gtfs-rt-util');
const testutil = require('./test-util');

const VEHICLE_IDS = [
    'pr-seq-test-vehicle-id-1', 'pr-seq-test-vehicle-id-2'
];

// this value needs to match 'POS_MAX_LIFE' in ../app-engine/gtfsrt.py
// in order to provide the expected results
const POS_MAX_LIFE = 15 * 60

let maxTimestamp = 0;
let maxIndex = -1;

async function post(signatureKey, agencyID, vehicleID, url, timestamp) {
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
    data['pos-timestamp'] = timestamp;

    data['vehicle-id'] = vehicleID;

    data['timestamp'] = timestamp;

    util.log('- data: ' + JSON.stringify(data));

    await util.signAndPost(data, signatureKey, url + '/new-pos-sig');
}

async function test(url, agencyID, ecdsaVarName) {
    util.log('starting vehicle position sequence test...');
    util.log(`- url: ${url}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await testutil.getSignatureKey(ecdsaVarName);
    util.log(`- signatureKey: ${JSON.stringify(signatureKey)}`);

    // send a number of updates for 'pr-seq-test-vehicle-id-1',
    // with timestamps increasing monotonically but stale

    const COUNT_1 = 5;
    util.log(`sending ${COUNT_1} stale position updates...`);

    for (let i = 0; i < COUNT_1; i++) {
        const staleTimestamp = testutil.getEpochSeconds() - POS_MAX_LIFE - 3;
        await post(signatureKey, agencyID, 'pr-seq-test-vehicle-id-1', url, staleTimestamp);
        await testutil.sleep(1000);
    }

    // send a number of updates for 'pr-seq-test-vehicle-id-2',
    // with some timestamps out of sequence but *not* stale

    const COUNT_2 = 10;
    util.log(`sending ${COUNT_2} position updates, some likely out of sequence...`);

    for (let i = 0; i < COUNT_2; i++) {
        let timestamp = testutil.getEpochSeconds();

        if (Math.random() < .67) {
            timestamp -= 5;
            util.log(`- update #${i} is out of sequence, server should discard`);
        }

        if (timestamp > maxTimestamp) {
            maxTimestamp = timestamp;
            maxIndex = i;
        }

        util.log(`- timestamp   : ${timestamp}`);
        util.log(`- maxTimestamp: ${maxTimestamp}`);
        util.log(`- maxIndex: ${maxIndex}`);

        await post(signatureKey, agencyID, 'pr-seq-test-vehicle-id-2', url, timestamp);
        await testutil.sleep(1000);
    }

    util.log(`max timestamp is ${maxTimestamp} from update #${maxIndex}`);

    await testutil.verboseSleep(15);
    await post(signatureKey, agencyID, 'flush', url);
    await testutil.verboseSleep(15);

    console.log('done');

    const now = testutil.getEpochSeconds();
    const body = await testutil.getResponseBody(url + '/vehicle-positions.pb?agency=' + agencyID);
    const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(body);

    let vehicle2Timestamp = 0;
    let vehicle1Count = 0;

    feed.entity.forEach(function(entity) {
        if (entity.vehicle) {
            if (entity.vehicle.vehicle && entity.vehicle.vehicle.label) {
                //util.log(`++ vehicle ID: ${entity.vehicle.vehicle.label}`);
                const vid = entity.vehicle.vehicle.label;

                if (vid === 'pr-seq-test-vehicle-id-1') {
                    vehicle1Count++;
                }

                if (vid === 'pr-seq-test-vehicle-id-2' && entity.vehicle.timestamp) {
                    vehicle2Timestamp = entity.vehicle.timestamp.low;
                }
            }
        }
    });

    if (vehicle1Count > 0 || vehicle2Timestamp != maxTimestamp) {
        throw `*** test failed:\n- expected 'vehicle1Count' to be '0' but got '${vehicle1Count}'\n- expected 'vehicle2Timestamp' to be '${maxTimestamp}' but got '${vehicle2Timestamp}'`;
    } else {
        util.log('---> test succeeded');
    }
}

const args = process.argv.slice(2);
// util.log(`- args: ${args}`);

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
