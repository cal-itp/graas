// prod url: https://lat-long-prototype.wl.r.appspot.com/new-pos-sig
// local url: https://127.0.0.1:8080/new-pos-sig

const crypto = require('crypto').webcrypto
const util = require('../app-engine/static/gtfs-rt-util')
const fs = require('fs')

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

    util.log('- signatureKey: ' + signatureKey);


    const contents = fs.readFileSync('alerts.csv', 'utf8');
    //util.log('- contents: ' + contents);
    let lines = contents.split('\n');
    //util.log('- lines: ' + JSON.stringify(lines));
    lines.shift();
    //util.log('- lines: ' + JSON.stringify(lines));

    for (let line of lines) {
        if (line.length == 0) continue;

        const token = line.split(',');
        util.log(`-- line: ${line}`);
        util.log(`-- token: ${JSON.stringify(token)}`);

        if (token.length < 9) {
            util.log(`** missing fields for line: ${token}`);
            continue;
        }

        const now = Math.round(Date.now() / 1000);

        let data = {
            agency_key: 'test',
            time_start: now,
            time_stop: now + 60
        };

        // agency_id,route_id,trip_id,stop_id,header,description,url,cause,effect
        if (token[0].length > 0) data['agency_id'] = token[0]
        if (token[1].length > 0) data['route_id'] = token[1]
        if (token[2].length > 0) data['trip_id'] = token[2]
        if (token[3].length > 0) data['stop_id'] = token[3]
        if (token[4].length > 0) data['header'] = token[4]
        if (token[5].length > 0) data['description'] = token[5]
        if (token[6].length > 0) data['url'] = token[6]
        if (token[7].length > 0) data['cause'] = token[7]
        if (token[8].length > 0) data['effect'] = token[8]

        util.log(`-- data: ${JSON.stringify(data)}`);

        util.signAndPost(data, signatureKey, url);
        await sleep(1000);
    }
}

const args = process.argv.slice(2);

if (args.length == 0) {
    util.log('usage: post-alert-updates <alert-update-endpoint>');
    process.exit(1);
}

test(args[0]);


