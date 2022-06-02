/*
A roundtrip test of server vehicle position feed functionality.

First, we post a batch of vehicle position updates. Count and vehicle IDs
are determinded by the contents of `VEHICLE_IDS`. The wrinkle here is that
some vehicle IDs are duplicate and the server will only keep the most recent
position update for a vehicle for the purposes of the vehicle position feed.

Then, we download the current vehicle position feed and iterate over the
contained vehicle entries, checking that timestamps are current and that
vehicle IDs start with `pr-test-vehicle-id-` (this is useful to e.g. weed
out updates sent as part of different tests).

Finally, we compare a sorted list of unique entries in `VEHICLE_IDS` with
the sorted list of current vehicle IDs received in the feed. If the lists
aren't identical, the test fails.
*/

const GtfsRealtimeBindings = require('gtfs-realtime-bindings');
const util = require('../app-engine/static/gtfs-rt-util');
const testutil = require('./test-util');

const VEHICLE_IDS = [
    'pr-test-vehicle-id-1', 'pr-test-vehicle-id-2',
    'pr-test-vehicle-id-2', 'pr-test-vehicle-id-3',
    'pr-test-vehicle-id-4', 'pr-test-vehicle-id-5',
    'pr-test-vehicle-id-5', 'pr-test-vehicle-id-5',
    'pr-test-vehicle-id-6', 'pr-test-vehicle-id-7'
];

async function post(signatureKey, agencyID, vehicleID, url) {
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

async function postUpdates(signatureKey, agencyID, url) {
    for (let vid of VEHICLE_IDS) {
        await post(signatureKey, agencyID, vid, url);
        await testutil.sleep(1000);
    }
}

async function test(url, agencyID, ecdsaVarName) {
    util.log('starting vehicle position test...');
    util.log(`- url: ${url}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await testutil.getSignatureKey(ecdsaVarName);
    util.log(`- signatureKey: ${JSON.stringify(signatureKey)}`);

    await postUpdates(signatureKey, agencyID, url);

    await testutil.verboseSleep(15);
    await post(signatureKey, agencyID, 'flush', url);
    await testutil.verboseSleep(15);

    console.log('done');

    const now = testutil.getEpochSeconds();
    const body = await testutil.getResponseBody(url + '/vehicle-positions.pb?agency=' + agencyID);
    const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(body);

    let expectedVIDS = [];

    for (let vid of VEHICLE_IDS) {
        if (!expectedVIDS.includes(vid)) {
            expectedVIDS.push(vid);
        }
    }

    expectedVIDS.sort();
    const expected = JSON.stringify(expectedVIDS);
    util.log(`- expected: ${expected}`);

    let currentVIDS = [];

    feed.entity.forEach(function(entity) {
        if (entity.vehicle) {
            let current = false;

            if (entity.vehicle.timestamp && entity.vehicle.timestamp.low) {
                //util.log(`++ timestamp: ${entity.vehicle.timestamp.low}`);

                const timestamp = entity.vehicle.timestamp.low;
                const delta = Math.abs(now - timestamp);
                //util.log(`++ delta: ${delta}`);

                if (delta < 60) {
                    current = true;
                } else {
                    util.log(`** excessive delta: ${delta}, discarding entity ${entity.vehicle}`);
                }
            }

            if (entity.vehicle.vehicle && entity.vehicle.vehicle.label) {
                //util.log(`++ vehicle ID: ${entity.vehicle.vehicle.label}`);

                const vid = entity.vehicle.vehicle.label;

                if (current && vid.indexOf('pr-test-vehicle-id-') === 0) {
                    currentVIDS.push(vid);
                }
            }
        }
    });

    currentVIDS.sort();

    const current = JSON.stringify(currentVIDS);
    util.log(`- current : ${current}`);

    if (expected === current) {
        util.log('---> test succeeded');
    } else {
        throw `expected '${expected}' but got '${current}'`;
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
