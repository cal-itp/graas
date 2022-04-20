const crypto = require('crypto').webcrypto
const GtfsRealtimeBindings = require('gtfs-realtime-bindings');
const util = require('../app-engine/static/gtfs-rt-util');
const fetch = require('node-fetch');

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
    util.log(`- blob: ${blob}`);

    const arrayBuf = await blob.arrayBuffer();
    util.log(`- arrayBuf.byteLength: ${arrayBuf.byteLength}`);

    const body = Buffer.from(arrayBuf);
    //util.log(`- body: ${JSON.stringify(body)}`);

    return body;
}

async function getSignatureKey() {
    const base64 = process.env.PR_TEST_ID_ECDSA;

    if (!base64) throw 'no private key found in $PR_TEST_ID_ECDSA';

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

async function test(url) {
    util.log(`test()`);
    util.log(`- url: ${url}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await getSignatureKey();
    util.log(`- signatureKey: ${JSON.stringify(signatureKey)}`);

    const body = await getResponseBody(args[1]);
    util.log(`- body: ${JSON.stringify(body)}`);

    const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(body);

    feed.entity.forEach(function(entity) {
        //if (entity.trip_update) {
            util.log(entity);
        //}
    });
}

const args = process.argv.slice(1);
//util.log(`- args: ${args}`);

if (args.length < 2) {
    util.log(`usage: ${getBaseName(args[0])} <position-update-endpoint>`);
    process.exit(1);
}

test(args[1]);
