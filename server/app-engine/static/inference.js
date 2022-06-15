
// const agencyID = "test-scott-express"
// const vehicleID = "123"

// const agencyURL = `${BASE_URL}/${agencyID}/`;
// var dow = -1;
// var epochSeconds = -1;
// var tripCandidates;
// var lastCandidateFlush;
// var stops;
// var lastCandidateFlush;
// var calendarMap;
// var stopTimeMap;
// var shapeMap;
// var routeMap;
// var shapeLengthMap;
const BASE_URL = 'https://storage.googleapis.com/graas-resources/gtfs-archive'
const STOP_PROXIMITY = 150;
const SCORE_THRESHOLD = 7;
const MIN_FLUSH_TIME_DELTA = 30 * 60;
const STOP_CAP = 10;

class TripInference {

    constructor(agencyID, vehicleID, subdivisions, dow = -1, epochSeconds = -1) {
        util.log("constructor()");
        this.agencyID = agencyID;
        this.vehicleID = vehicleID;
        this.subdivisions = subdivisions;
        this.dow = dow;
        this.epochSeconds = epochSeconds;
        this.init();
    }

    async init(){

        this.tripCandidates = {};
        this.lastCandidateFlush = Date.now();

        await this.getCalendarMap();
        util.log("this.calendarMap: " + JSON.stringify(this.calendarMap));
        await this.getRouteMap();
        util.log("this.routeMap: " + JSON.stringify(this.routeMap));

        this.area = new Area();
        this.populateBoundingBox(this.area);
        util.log(`- this.area: ${this.area}`);
        this.grid = new Grid(this.area, this.subdivisions);

        if (this.dow < 0) {
            this.dow = (new Date()).getDay();
            util.log(`- dow: ${this.dow}`)
        }

        if (this.epochSeconds < 0) {
            this.epochSeconds = Date.now();
            util.log(`- epochSeconds: ${this.epochSeconds}`)
        }

        this.stops = this.getStops();
        util.log(`-- stops: ${this.stops}`);

        this.preloadStopTimes();
        await this.preloadShapes();

        this.computeShapeLengths();
        this.blockMap = {}
        let tripSet = new Set();

        let rows = await this.getFile("trips.txt");
        let count = 1;
        let load_timer = new Timer('load');
        for (let r of rows){
            let loop_timer = new Timer('loop')
            let trip_id = r['trip_id']
            let service_id = r['service_id']
            let shape_id = r['shape_id']

            tripSet.add(trip_id)

            if (!(service_id in this.calendarMap)){
                util.log(`* service id \'${service_id}\' not found in calendar map, skipping trip \'${trip_id}\'`);
                continue;
            }
            let cal = this.calendarMap[service_id]['cal'];
            if (cal !== null && cal[this.dow] !== 1){
                util.log(`* dow \'${this.dow}\' not set, skipping trip \'${trip_id}\'`);
                continue;
            }
            let startDate = this.calendarMap[service_id].get('start_date', null);
            let endDate = this.calendarMap[service_id].get('end_date', null);

            if (startDate !== null && endDate !== null){
                startSeconds = util.getEpochSeconds(startDate);
                endSeconds = util.getEpochSeconds(endDate);
                util.log("startSeconds: " + startSeconds);
                util.log("endSeconds: " + endSeconds);

                if (this.epochSeconds < startSeconds || this.epochSeconds > endSeconds){
                    util.log(`* trip date outside service period (start: ${start_date}, end: ${end_date}), skipping trip \'${trip_id}\'`);
                    continue;
                }
            }
            util.log('');
            util.log(`-- trip_id: {trip_id} ({count}/{len(rows)})`);
            count += 1;

            let route_id = r['route_id'];
            util.log(`-- shape_id: ${shape_id}`);
            let timer = Timer('way points');
            let wayPoints = this.getShapePoints(shape_id);
            util.log(timer);
            util.log(`-- wayPoints: ${wayPoints}`)
            util.log(`-- wayPoints.length: ${wayPoints.length}`)

            if (wayPoints.length === 0){
                util.debug(`* no way points for trip_id \'${trip_id}\', shape_id \'${shape_id}\'`);
                continue;
            }

            timer = Timer('stop times');
            let stopTimes = self.getStopTimes(trip_id);

            let block_id = ['block_id'];
            util.log(`-- block_id: ${block_id}`);

            if (block_id !== null && block_id.length > 0 && stopTimes !== null && stopTimes.length > 0){
                let tripList = this.blockMap[block_id];

                if (tripList === null){
                    tripList = [];
                    this.blockMap[block_id] = tripList;
                }

                let startTime = stopTimes[0]['arrival_time'];
                let endTime = stopTimes[-1]['arrival_time'];

                if (startTime !== null && endTime !== null){
                    tripList.append({
                        'trip_id': trip_id,
                        'start_time': start_time,
                        'end_time': end_time
                    });
                }
            }
            util.log(timer);
            util.debug(`-- stopTimes: ${stopTimes}`);
            util.debug(`-- len(stopTimes): ${stopTimes.length}`);
            timer = Timer('interpolate');
            this.interpolateWayPointTimes(wayPoints, stopTimes, this.stops);
            util.log(timer);

            // // trip_name = route_map[route_id]['name'] + ' @ ' + util.seconds_to_ampm_hhmm(stopTimes[0]['arrival_time'])
            // trip_name = trip_id + ' @ ' + util.seconds_to_ampm_hhmm(stopTimes[0]['arrival_time'])
            // util.debug(f'-- trip_name: {trip_name}')
            // shape_length = self.shape_length_map[shape_id]

            // if shape_length is None:
            //     segment_length = 2 * util.FEET_PER_MILE
            // else:
            //     segment_length = int(shape_length / 30)

            // // util.debug(f'-- segment_length: {segment_length}')
            // timer = Timer('segments')
            // self.make_trip_segments(trip_id, trip_name, stopTimes[0], wayPoints, segment_length)
            // // util.debug(timer)
            // // util.debug(loop_timer)
        }
    }

    async getFile(fileName){
            let url = `${BASE_URL}/${this.agencyID}/${fileName}`;
            util.log('- fetching from ' + url);
            let response = await util.timedFetch(url, {method: 'GET'});
            let text = await response.text();
            return csvToArray(text);
    }

    async getCalendarMap(){
        util.log('get_calendar_map()');
        let rows = await this.getFile("calendar.txt");
        this.calendarMap = {};
        let dow = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

        for (let r of rows){
            let service_id = r['service_id'];
            util.log(`-- service id: ${service_id}`)
            let cal = [];
            for (let d of dow){
                cal.push(r[d]);
                // util.log(`-- cal: ${cal}`);
            }
            this.calendarMap[service_id] = {'cal': cal, 'start_date': r['start_date'], 'end_date': r['end_date']};
        }
    }

    async getRouteMap(){
        util.log('getRouteMap()');
        let rows = await this.getFile("routes.txt");
        this.routeMap = {};
        for (let r of rows){
            let route_id = r['route_id'];
            util.log(`-- route_id: ${route_id}`)
            let shortName = r['route_short_name'];
            let longName = r['route_long_name']
            let name = (shortName.length > 0 ? shortName : longName)

            this.routeMap[route_id] = {'name': name}
        }
    }

    async getStops(){
        util.log('getStops()');
        let rows = await this.getFile("stops.txt");
        let stopList = {};
        for (let r of rows){
            let id = r['stop_id'];
            let lat = r['stop_lat'];
            let lon = r['stop_lon'];
            stopList[id] = {'lat': lat, 'long': lon}
        }
        return stopList
    }

    async preloadStopTimes(){
        util.log('preloadStopTimes()');
        let rows = await this.getFile("stops.txt");
        this.stopTimeMap = {};

        for (let r of rows){
            // util.log("JSON.stringify(r): " + JSON.stringify(r));

            let trip_id = r['trip_id'];
            let arrival_time = r['arrival_time'];
            if (util.isNullOrUndefined(arrival_time)) continue;
            let stop_id = r['stop_id'];
            let stop_sequence = r['stop_sequence'];

            let slist = this.stopTimeMap[trip_id];

            if (util.isNullOrUndefined(slist)){
                slist = [];
                this.stopTimeMap[trip_id] = slist;
            }

            let entry = {'arrival_time': util.hhmmssToSeconds(arrival_time), 'stop_id': stop_id, 'stop_sequence': stop_sequence}

            let sdt = r['shape_dist_traveled'];

            if (!util.isNullOrUndefined(sdt) && sdt.length > 0){
                entry['traveled'] = sdt;
            }
            util.log(`- entry: ${entry}`)
            sxlist.push(entry)
        }
    }

    async preloadShapes(){
        util.log('preloadShapes()');
        let rows = await this.getFile("shapes.txt");
        this.shapeMap = {};

        for (let r of rows){
            // util.log("JSON.stringify(r): " + JSON.stringify(r));

            let shape_id = r['shape_id'];
            let lat = r['shape_pt_lat'];
            let lon = r['shape_pt_lon'];

            let plist = this.shapeMap[shape_id];

            if (util.isNullOrUndefined(plist)){
                plist = [];
                this.shapeMap[shape_id] = plist;
            }
            let file_offset = null;
            let entry = {'lat': lat, 'lon': lon, 'file_offset': file_offset}
            let sdt = r['shape_dist_traveled'];

            if (util.isNullOrUndefined(sdt)){
                entry['traveled'] = sdt;
            }
            // util.log(`- entry: ${entry}`)
            plist.push(entry)
        }
    }

    computeShapeLengths() {
        this.shapeLengthMap = {};
        util.log("shapeMap: " + JSON.stringify(this.shapeMap));
        for (let [key, value] of Object.entries(this.shapeMap)){
            util.log("shapeID: " + key);
            let pointList = value;
            let length = 0;

            for (let i = 0; i < pointList.length - 1; i++){
                util.log("pointList[i]: " + pointList[i]);
                let p1 = pointList[i];
                let p2 = pointList[i+1];
                length += util.haversineDistance(p1.lat, p1.long, p2.lat, p2.long);
            }
            this.shapeLengthMap[key] = length;
            util.log(`++ length for shape ${key}: ${length}`);
        }
    }

    async populateBoundingBox(area){

        let rows = await this.getFile("stops.txt");
        this.stopTimeMap = {};

        for (let r of rows){
            util.log("JSON.stringify(r): " + JSON.stringify(r));

            let lat = r['shape_pt_lat'];
            let lon = r['shape_pt_lon'];
            area.update(lat, lon);
        }
    }
    getShapePoints(shape_id){
        return this.shapeMap[shape_id];
    }

    getStopTimes(trip_id){
        return this.stopTimeMap[trip_id];
    }

    // This is a naive, brute force approach that may well fail for self-intersecting or
    // self-overlapping routes, or just very twisty routes. A better approach may be:
    // - assign fraction to stops based on where their arrival time falls for overall trip duration.
    //   distance along route or a combination of both
    // - set closest-stop attr for way points with corresponding fractions
    // - search n neighbors on either side of points with attr and move attr to neighbor if closer
    // - repeat until stable

    interpolateWayPointTimes(wayPoints, stopTimes, stops):
        anchorList = this.createAnchorList(wayPoints, stopTimes, stops)
        firstStop = True

        // print(f'interpolateWayPointTimes()')
        // util.debug(f'- len(wayPoints): {len(wayPoints)}')
        // util.debug(f'- len(stopTimes): {len(stopTimes)}')

        for st in stopTimes:
            sp = stops[st['stop_id']]

            if firstStop:
                sp['first_stop'] = True
                firstStop = False

        util.debug(f'- anchorList: {anchorList}')
        // util.debug(f'- len(anchorList): {len(anchorList)}')

        for i in range(len(anchorList) - 1):
            start = anchorList[i]
            end = anchorList[i + 1]
            tdelta = end['time'] - start['time']
            idelta = end['index'] - start['index']

            // util.debug('--------------------------')

            for j in range(idelta):
                fraction = j / idelta
                time = start['time'] + int(fraction * tdelta)
                wayPoints[start['index'] + j]['time'] = time
                hhmmss = util.seconds_to_hhmmss(time)
                // util.debug(f'--- {hhmmss}')

        // util.debug('--------------------------')

        index = anchorList[0]['index']
        time = anchorList[0]['time']

        while index >= 0:
            index -= 1
            wayPoints[index]['time'] = time

        index = anchorList[-1]['index']
        time = anchorList[-1]['time']

        while index < len(wayPoints):
            wayPoints[index]['time'] = time
            index += 1

        // // //  REMOVE ME: for testing only
        for i in range(len(wayPoints)):
            if not 'time' in wayPoints[i]:
                util.debug(f'.. {i}')


    // For simpler trips `shape_dist_traveled` is optional. If the attribute is missing, we can take the following approach:
    // - for each stop, assign a fraction of arrival time at that stop to total trip time -> fractions list
    // - for i in len(fractions)
    // -   compute index into shape list as int(fractions[i] * len(wayPoints) -> anchor_list
    // - explore neighboring points in wayPoints for each match_list entry, going up to half the points until previous or next anchor point
    // - adjust anchor points if closer match found
    // - repeat until stable (current anchor list equals pevious anchor list) of max tries reached

    createAnchorListIteratively(wayPoints, stopTimes, stops){
        startSeconds = stopTimes[0]['arrival_time']
        stopSeconds = stopTimes[-1]['arrival_time']
        totalSeconds = float(stopSeconds - startSeconds)
        // print(f'- totalSeconds: {totalSeconds}')
        anchor_list = []

        for i in range(stopTimes.length):
            // print(f'-- i: {i}')
            seconds = stopTimes[i]['arrival_time'] - startSeconds
            // print(f'-- seconds: {seconds}')
            frac = seconds / totalSeconds
            // print(f'-- frac: {frac}')
            j = int(frac * (len(wayPoints) - 1))
            // print(f'-- j: {j}')
            anchor_list.append({'index': j, 'time': stopTimes[i]['arrival_time']})

        for i in range(20):
            last_anchor_list = copy.deepcopy(anchor_list)
            // print(f'-- last_anchor_list: {last_anchor_list}')

            // explore neighbors in anchor_list, potentially changing index fields
            for j in range(len(last_anchor_list)):
                // print(f'-- j: {j}')
                c = last_anchor_list[j]
                // print(f'-- c: {c}')

                p = c
                if j > 0:
                    p = last_anchor_list[j - 1]

                n = c
                if j < len(last_anchor_list) - 1:
                    n = last_anchor_list[j + 1]

                p1 = stops[stopTimes[j]['stop_id']]
                p2 = wayPoints[c['index']]
                min_diff = util.haversine_distance(p1['lat'], p1['long'], p2['lat'], p2['long'])
                min_index = c['index']
                // print(f'-- min_index: {min_index}')

                // print(f'-- c["index"]: {c["index"]}')
                // print(f'-- p["index"]: {p["index"]}')
                // print(f'-- n["index"]: {n["index"]}')

                kf = int(p['index'] + math.ceil((c['index'] - p['index']) / 2))
                kt = int(c['index'] + (n['index'] - c['index']) / 2)

                // print(f'-- kf: {kf}, kt: {kt}')

                for k in range(kf, kt):
                    p2 = wayPoints[k]
                    diff = util.haversine_distance(p1['lat'], p1['long'], p2['lat'], p2['long'])

                    if diff < min_diff:
                        min_diff = diff
                        min_index = k

                // print(f'++ min_index: {min_index}')
                anchor_list[j]['index'] = min_index

            // print(f'-- anchor_list     : {anchor_list}')

            stable = True

            for j in range(len(anchor_list)):
                i1 = anchor_list[j]['index']
                i2 = last_anchor_list[j]['index']

                if i1 != i2:
                    // print(f'* {i1} != {i2}')
                    stable = False
                    break

            if stable:
                break

        // print(f'++ anchor_list     : {anchor_list}')

        return anchor_list;
    }

    // anchor_list establishes a relationship between the list of stops for a trip and that trip's way points as described in shapes.txt.
    // The list has one entry per stop. Each entry has an index into wayPoints to map to the closest shape point and the time when
    // a vehicle is scheduled to arrive at that point (as given in stopTimes.txt). anchor_list is an intermediate point in assigning
    // timestamps to each point of the trip shape

    // Complex trips with self-intersecting or self-overlapping segments are supposed to have `shape_dist_traveled` attributes for their
    // shape.txt and stopTimes.txt entries. This allows for straight-forward finding of shape points close to stops.

    createAnchorList(wayPoints, stopTimes, stops){
        // print(f' - len(wayPoints): {len(wayPoints)}')
        // print(f' - len(stopTimes): {len(stopTimes)}')
        // print(f' - len(stops): {len(stops)}')

        annotated = True

        for i in range(len(stopTimes)):
            if not stopTimes[i].get('traveled', False):
                annotated = False
                break;

        if annotated:
            for i in range(len(wayPoints)):
                if not wayPoints[i].get('traveled', False):
                    annotated = False
                    break;

        if not annotated:
            return self.createAnchorListIteratively(wayPoints, stopTimes, stops)

        anchor_list = []

        for i in range(len(stopTimes)):
            traveled = stopTimes[i]['traveled']
            time = stopTimes[i]['arrival_time']

            min_difference = float('inf')
            min_index = -1

            for j in range(len(wayPoints)):
                t = wayPoints[j]['traveled']
                diff = math.abs(traveled - t)

                if diff < min_difference:
                    min_difference = diff
                    min_index = j

            anchor_list.append({'index': min_index, 'time': time})

        return anchor_list
    }
}