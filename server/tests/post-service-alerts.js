/*
A roundtrip test of server service alert feed functionality.

First, we post a batch of service alert updates. Alert details are read
from local file alerts.csv.

Then, we download the current service alert feed and iterate over the
contained alert entries, checking that timestamps are current and that
alert description start with `pr-test-alert-header` (this is useful to
e.g. weed out updates sent as part of different tests).

Finally, we compare a sorted list of alert details posted with a sorted
list of alert details received in the feed. If the lists aren't identical,
the test fails.
*/


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

function normalize(s) {
    s = s.toLowerCase();
    s = s.replace('_', ' ');

    return s;
}

async function test(baseUrl) {
    util.log('starting test...');
    util.log(`- baseUrl: ${baseUrl}`);

    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

    const signatureKey = await testutil.getSignatureKey();

    const SER_FIELDS = ['agency_id', 'route_id', 'trip_id', 'stop_id', 'header', 'description', 'url', 'cause', 'effect'];
    const reader = new CSVReader('alerts.csv', true);
    let updates = [];
    let now = null;

    for (;;) {
        let data = reader.getNextLine();
        if (data == null) break;

        //util.log(`- obj: ${JSON.stringify(data)}`);

        now = Math.round(Date.now() / 1000);

        data['agency_key'] = 'test';
        data['time_start'] = now;
        data['time_stop'] = now + 60;

        updates.push(serialize(data, SER_FIELDS).toLowerCase());

        util.signAndPost(data, signatureKey, baseUrl + '/post-alert');
        await testutil.sleep(1000);
    }

    updates.sort();
    util.log(`- updates: ${JSON.stringify(updates)}`);

    const body = await testutil.getResponseBody(baseUrl + '/service-alerts.pb?agency=test');
    const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(body);
    now = Math.round(Date.now() / 1000);
    let entries = [];

    feed.entity.forEach(function(entity) {
        //util.log(entity);

        let cause = null;
        let effect = null;
        let time_start = null;
        let time_stop = null;
        let agency_id = null;
        let route_id = null;
        let trip_id = null;
        let stop_id = null;
        let url = null;
        let header = null;
        let description = null;

        if (entity.alert && entity.alert.activePeriod) {
            const range = entity.alert.activePeriod[0];
            time_start = range.start.low;
            time_stop = range.end.low;
            //util.log(`-- time_start: ${time_start}`);
            //util.log(`-- time_stop: ${time_stop}`);
        }

        if (entity.alert && entity.alert.informedEntity) {
            const selector = entity.alert.informedEntity[0];

            if (selector.agencyId) {
                agency_id = selector.agencyId;
                //util.log(`-- agency_id: ${agency_id}`);
            }

            if (selector.routeId) {
                route_id = selector.routeId;
                //util.log(`-- route_id: ${route_id}`);
            }

            if (selector.trip && selector.trip.tripId) {
                trip_id = selector.trip.tripId;
                //util.log(`-- trip_id: ${trip_id}`);
            }

            if (selector.stopId) {
                stop_id = selector.stopId;
                //util.log(`-- stop_id: ${stop_id}`);
            }
        }

        if (entity.alert && entity.alert.cause) {
            cause = normalize(GtfsRealtimeBindings.transit_realtime.Alert.Cause[entity.alert.cause]);
            //util.log(`-- cause: ${cause}`);
        }

        if (entity.alert && entity.alert.effect) {
            effect = normalize(GtfsRealtimeBindings.transit_realtime.Alert.Effect[entity.alert.effect]);
            //util.log(`-- effect: ${effect}`);
        }

        if (entity.alert && entity.alert.url && entity.alert.url.translation) {
            const translation = entity.alert.url.translation[0];
            url = translation.text;
            //util.log(`-- url: ${url}`);
        }

        if (entity.alert && entity.alert.headerText && entity.alert.headerText.translation) {
            const translation = entity.alert.headerText.translation[0];
            header = translation.text;
            //util.log(`-- header: ${header}`);
        }

        if (entity.alert && entity.alert.descriptionText && entity.alert.descriptionText.translation) {
            const translation = entity.alert.descriptionText.translation[0];
            description = translation.text;
            //util.log(`-- description: ${description}`);
        }

        if (header && header.indexOf('pr-test-alert-header-') === 0 && time_start <= now && time_stop > now) {
            let obj = {};

            //util.log(entity.alert.informedEntity);

            obj.agency_id = agency_id;
            obj.route_id = route_id;
            obj.trip_id = trip_id;
            obj.stop_id = stop_id;
            obj.time_start = time_start;
            obj.time_stop = time_stop;
            obj.url = url;
            obj.header = header;
            obj.description = description;
            obj.cause = cause;
            obj.effect = effect;

            const s = serialize(obj, SER_FIELDS);
            //util.log(`-- s: ${s}`);
            entries.push(s);
        }
    });

    entries.sort();
    util.log(`- entries: ${JSON.stringify(entries)}`);

    if (updates.length !== entries.length) {
        throw `expected ${updates.length} feed entries but got ${entries.length}`;
    }

    for (let i=0; i<updates.length; i++) {
        if (updates[i] !== entries[i]) {
            throw `expected alert data '${updates[i]}' but got '${entries[i]}'`;
        }
    }

    util.log('---> test succeeded');
}

const args = process.argv.slice(2);

if (args.length == 0) {
    util.log('usage: post-alert-updates <server-base-url>');
    process.exit(1);
}

test(args[0]);


