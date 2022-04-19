// prod url: https://lat-long-prototype.wl.r.appspot.com/new-pos-sig
// local url: https://127.0.0.1:8080/new-pos-sig

const crypto = require('crypto').webcrypto
const util = require('../app-engine/static/gtfs-rt-util')

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function test(url) {
    util.log('starting test...');
    util.log(`- url: ${url}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const base64 = process.env.PR_TEST_ID_ECDSA;

    if (!base64) {
        util.log('* no private key found in $PR_TEST_ID_ECDSA, exiting...');
        process.exit(1);
    }

    const key = atob(base64);
    util.log("- key.length: " + key.length);

    const binaryDer = util.str2ab(key);

    const signatureKey = await crypto.subtle.importKey(
        "pkcs8",
        binaryDer,
        {
            name: "ECDSA",
            namedCurve: "P-256"
        },
        false,
        ["sign"]
    );

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

    const vids = [
        'pr-test-vehicle-id-1', 'pr-test-vehicle-id-2',
        'pr-test-vehicle-id-2', 'pr-test-vehicle-id-3',
        'pr-test-vehicle-id-4', 'pr-test-vehicle-id-5',
        'pr-test-vehicle-id-5', 'pr-test-vehicle-id-5',
        'pr-test-vehicle-id-6', 'pr-test-vehicle-id-7'
    ];

    for (let vid of vids) {
        data['vehicle-id'] = vid;
        util.log(`-- vid: ${vid}`);

        data['timestamp'] = Math.floor(Date.now() / 1000);
        util.log(`++ timestamp: ${data.timestamp}`);

        util.signAndPost(data, signatureKey, url);
        await sleep(1000);
    }
}

const args = process.argv.slice(2);

if (args.length == 0) {
    util.log('usage: post-position-update <position-update-endpoint>');
    process.exit(1);
}

test(args[0]);


