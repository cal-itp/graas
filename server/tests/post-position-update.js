var crypto = require('crypto').webcrypto
var util = require('../app-engine/static/gtfs-rt-util')

async function test() {
    util.log('starting test...');

    const timestamp = Math.floor(Date.now() / 1000);
    const base64 = process.env.PR_TEST_ID_ECDSA;
    var data = {
        uuid: 'test',
        agent: 'test',
        timestamp: timestamp,
        lat: 0,
        long: 0,
        speed: 0,
        heading: 0,
        accuracy: 0,
        version: 'test'
    };

    data['trip-id'] = 'test';
    data['agency-id'] = 'pr-test';
    data['vehicle-id'] = 'test';
    data['pos-timestamp'] = 'test';

    var key = atob(base64);
    util.log("- key.length: " + key.length);

    const binaryDer = util.str2ab(key);

    var signatureKey = await crypto.subtle.importKey(
        "pkcs8",
        binaryDer,
        {
            name: "ECDSA",
            namedCurve: "P-256"
        },
        false,
        ["sign"]
    );

    util.log('- timestamp: ' + timestamp);
    util.log('- signatureKey: ' + signatureKey);
    util.log('- data: ' + JSON.stringify(data));

    util.signAndPost(data, signatureKey, 'https://lat-long-prototype.wl.r.appspot.com/new-pos-sig');
}

test();


