/*
Get block assignments for the current day for a specified agency.
*/

const util = require('../app-engine/static/gtfs-rt-util');
const testutil = require('./test-util');

async function test(agencyID, url, ecdsaVarName) {
    util.log(`test()`);
    util.log(`- agencyID: ${agencyID}`);
    util.log(`- url: ${url}`);
    util.log(`- ecdsaVarName: ${ecdsaVarName}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await testutil.getSignatureKey(ecdsaVarName);
    util.log(`- signatureKey: ${JSON.stringify(signatureKey)}`);

    let agencyDate = {
        agency_id: agencyID,
        date: util.getYYYYMMDD(util.getMidnightDate())
    };

    let json = await util.getJSONResponse(url + '/get-assignments', agencyDate, signatureKey);
    //util.log('- json: ' + json);
    let assignments = json.assignments;

    util.log(JSON.stringify(assignments));
}

const args = process.argv.slice(2);
// util.log(`- args: ${args}`);

let agencyID = null;
let url = null;
let ecdsaVarName = null;

for(let j = 0; j < args.length; j++){
    if((args[j] === '-a' || args[j] === '--agency-id') && j + 1 < args.length){
        agencyID = args[j+1];
        j++;
        continue;
    }

    if((args[j] === '-u' || args[j] === '--url') && j + 1 < args.length){
        url = args[j+1];
        j++;
        continue;
    }

    if((args[j] === '-e' || args[j] === '--ecdsa-var-name') && j + 1 < args.length){
        ecdsaVarName = args[j+1];
        j++;
        continue;
    }
}

if (agencyID === null || url === null || ecdsaVarName === null) {
    util.log(`usage: ${testutil.getBaseName(args[0])} -a <agency-id> -e <ecdsa-var-name> -u <url>`);
    util.log(`- note: key contained in <ecdsa-var-name> must be agency key for <agency-id>`);
    process.exit(1);
}


test(agencyID, url, ecdsaVarName);
