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

const crypto = require('crypto').webcrypto
const GtfsRealtimeBindings = require('gtfs-realtime-bindings');
const util = require('../app-engine/static/gtfs-rt-util');
const fetch = require('node-fetch');

const VEHICLE_IDS = [
    'pr-test-vehicle-id-1', 'pr-test-vehicle-id-2',
    'pr-test-vehicle-id-2', 'pr-test-vehicle-id-3',
    'pr-test-vehicle-id-4', 'pr-test-vehicle-id-5',
    'pr-test-vehicle-id-5', 'pr-test-vehicle-id-5',
    'pr-test-vehicle-id-6', 'pr-test-vehicle-id-7'
];

function getBaseName(s) {
    const index = s.lastIndexOf('/');

    if (index < 0) return s;
    else return s.substring(index + 1);
}

async function getResponseBody(url) {
    const requestSettings = {
        method: 'GET'
    };

    const response = await fetch(url, requestSettings);

    const blob = await response.blob();
    //util.log(`- blob: ${blob}`);

    const arrayBuf = await blob.arrayBuffer();
    //util.log(`- arrayBuf.byteLength: ${arrayBuf.byteLength}`);

    const body = Buffer.from(arrayBuf);
    //util.log(`- body: ${JSON.stringify(body)}`);

    return body;
}

async function getSignatureKey() {
    const base64 = process.env.PR_TEST_ID_ECDSA;

    if (!base64) throw 'unset environment variable $PR_TEST_ID_ECDSA';

    const key = atob(base64);
    util.log("- key.length: " + key.length);

    const binaryDer = util.str2ab(key);

    return await crypto.subtle.importKey(
        "pkcs8",
        binaryDer,
        {
            name: "ECDSA",
            namedCurve: "P-256"
        },
        false,
        ["sign"]
    );
}

function sleep(ms) {
    return new Promise((resolve) => {
        setTimeout(resolve, ms);
    });
}

function getEpochSeconds() {
    return Math.floor(Date.now() / 1000);
}

async function postUpdates(signatureKey, url) {
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
    data['agency-id'] = 'test';
    data['vehicle-id'] = 'test';
    data['pos-timestamp'] = 'test';

    util.log('- signatureKey: ' + signatureKey);
    util.log('- data: ' + JSON.stringify(data));

    for (let vid of VEHICLE_IDS) {
        data['vehicle-id'] = vid;
        util.log(`-- vid: ${vid}`);

        data['timestamp'] = getEpochSeconds();
        util.log(`++ timestamp: ${data.timestamp}`);

        util.signAndPost(data, signatureKey, url);
        await sleep(1000);
    }
}

async function test(url) {
    util.log(`test()`);
    util.log(`- url: ${url}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await getSignatureKey();
    util.log(`- signatureKey: ${JSON.stringify(signatureKey)}`);

    await postUpdates(signatureKey, url + '/new-pos-sig');

    console.log('sleeping for a few seconds before accessing vehicle position feed...');

    for (let i=0; i<5; i++) {
        await sleep(1000);
        console.log('.');
    }

    console.log('done');

    const now = getEpochSeconds();
    const body = await getResponseBody(url + '/vehicle-positions.pb?agency=test');
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

const args = process.argv.slice(1);
//util.log(`- args: ${args}`);

if (args.length < 2) {
    util.log(`usage: ${getBaseName(args[0])} <position-update-endpoint>`);
    process.exit(1);
}

test(args[1]);
