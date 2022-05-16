var crypto = require('crypto').webcrypto
var util = require('../app-engine/static/gtfs-rt-util')
const testutil = require('./test-util');

async function test(url, agencyID, ecdsaVarName) {
    util.log('starting test-position-update...');

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await testutil.getSignatureKey(ecdsaVarName);
    const timestamp = Math.floor(Date.now() / 1000);

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
    data['agency-id'] = agencyID;
    data['vehicle-id'] = 'test';
    data['pos-timestamp'] = 'test';

    util.log('- timestamp: ' + timestamp);
    util.log('- signatureKey: ' + signatureKey);
    util.log('- data: ' + JSON.stringify(data));

    util.signAndPost(data, signatureKey, url + '/new-pos-sig');
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
