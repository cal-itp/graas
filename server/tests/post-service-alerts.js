// prod url: https://lat-long-prototype.wl.r.appspot.com/post-alert
// local url: https://127.0.0.1:8080/post-alert

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

async function test(url) {
    util.log('starting test...');
    util.log(`- url: ${url}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await testutil.getSignatureKey();

    /*const contents = fs.readFileSync('alerts.csv', 'utf8');
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
    }*/

    const SER_FIELDS = ['agency_id', 'route_id', 'trip_id', 'stop_id', 'header', 'description', 'url', 'cause', 'effect'];
    const reader = new CSVReader('alerts.csv', true);
    let updates = [];

    for (;;) {
        let data = reader.getNextLine();
        if (data == null) break;

        util.log(`- obj: ${JSON.stringify(data)}`);

        const now = Math.round(Date.now() / 1000);

        data['agency_key'] = 'test';
        data['time_start'] = now;
        data['time_stop'] = now + 60;

        updates.push(serialize(data, SER_FIELDS));

        util.signAndPost(data, signatureKey, url + '/post-alert');
        await testutil.sleep(1000);
    }

    util.log(`-- updates: ${JSON.stringify(updates)}`);

    const body = await testutil.getResponseBody(url + '/service-alerts.pb?agency=test');
    const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(body);

    feed.entity.forEach(function(entity) {
        util.log(entity);

        if (entity.alert && entity.alert.activePeriod) {
            util.log(entity.alert.activePeriod);
        }

        if (entity.alert && entity.alert.headerText && entity.alert.headerText.translation) {
            util.log(entity.alert.headerText.translation);
        }

        if (entity.alert && entity.alert.descriptionText && entity.alert.descriptionText.translation) {
            util.log(entity.alert.descriptionText.translation);
        }

        /*if (entity.vehicle) {
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
        }*/
    });
}

const args = process.argv.slice(2);

if (args.length == 0) {
    util.log('usage: post-alert-updates <alert-update-endpoint>');
    process.exit(1);
}

test(args[0]);


