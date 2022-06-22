
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
        await this.populateBoundingBox();
        util.log(`- JSON.stringify(this.area): ${JSON.stringify(this.area)}`);
        this.grid = new Grid(this.area, this.subdivisions);

        if (this.dow < 0) {
            this.dow = (new Date()).getDay();
            util.log(`- dow: ${this.dow}`)
        }

        if (this.epochSeconds < 0) {
            this.epochSeconds = Date.now();
            util.log(`- epochSeconds: ${this.epochSeconds}`)
        }

        this.stops = await this.getStops();
        util.log(`-- JSON.stringify(this.stops): ${JSON.stringify(this.stops)}`);

        await this.preloadStopTimes();

        util.log(`-- JSON.stringify(this.stopTimeMap): ${JSON.stringify(this.stopTimeMap)}`);
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
                util.log(`* no way points for trip_id \'${trip_id}\', shape_id \'${shape_id}\'`);
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
            util.log(`-- stopTimes: ${stopTimes}`);
            util.log(`-- len(stopTimes): ${stopTimes.length}`);
            timer = Timer('interpolate');
            this.interpolateWayPointTimes(wayPoints, stopTimes, this.stops);
            util.log(timer);

            // // trip_name = route_map[route_id]['name'] + ' @ ' + util.seconds_to_ampm_hhmm(stopTimes[0]['arrival_time'])
            let tripName = trip_id + ' @ ' + util.getHMForSeconds(stopTimes[0]['arrival_time'], true);
            util.log(`-- tripName: ${tripName}`);
            let shapeLength = this.shapeLengthMap[shape_id];

            if (shape_length === null){
                let segmentLength = 2 * util.FEET_PER_MILE;
            }
            else{
                let segmentLength = shape_length;
            }

            // util.log(f'-- segmentLength: {segmentLength}')
            timer = Timer('segments');
            this.makeTripSegments(trip_id, tripName, stopTimes[0], wayPoints, segmentLength);
            // util.log(timer)
            // util.log(loop_timer)
        }
        util.log(`-- this.blockMap: ${JSON.stringify(this.blockMap)}`);

        util.log(load_timer);
        util.log(`-- JSON.stringify(this.grid): ${JSON.stringify(this.grid)}`);

        for (let tid in this.stopTimeMap){
            if (!(tid in tripSet)){
                delete this.stopTimeMap.tid;
            }
        }

        self.shape_map = {}
    }

    async getFile(fileName){
            let url = `${BASE_URL}/${this.agencyID}/${fileName}`;
            util.log('- fetching from ' + url);
            let response = await util.timedFetch(url, {method: 'GET'});
            let text = await response.text();
            return util.csvToArray(text);
    }

    async getCalendarMap(){
        util.log('getCalendarMap()');
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
            // util.log(`-- route_id: ${route_id}`)
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
        let rows = await this.getFile("stop_times.txt");
        this.stopTimeMap = {};

        for (let r of rows){
            // util.log("JSON.stringify(r): " + JSON.stringify(r));

            let trip_id = r['trip_id'];
            let arrival_time = r['arrival_time'];
            if (util.isNullUndefinedOrBlank(arrival_time)) continue;
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
            slist.push(entry)
        }
    }

    async preloadShapes(){
        util.log('preloadShapes()');
        let rows = await this.getFile("shapes.txt");
        this.shapeMap = {};

        for (let i=0; i<rows.length; i++){
            // util.log("JSON.stringify(r): " + JSON.stringify(r));

            let shape_id = rows[i]['shape_id'];
            let lat = rows[i]['shape_pt_lat'];
            let lon = rows[i]['shape_pt_lon'];

            let plist = this.shapeMap[shape_id];

            if (util.isNullOrUndefined(plist)){
                plist = [];
                this.shapeMap[shape_id] = plist;
            }
            let file_offset = i;
            let entry = {'lat': lat, 'lon': lon, 'file_offset': file_offset}
            let sdt = rows[i]['shape_dist_traveled'];

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
                // util.log("JSON.stringify(pointList[i]): " + JSON.stringify(pointList[i]));
                let p1 = pointList[i];
                let p2 = pointList[i+1];
                // For some reason p2 values are being passed as null
                length += util.haversineDistance(p1.lat, p1.long, p2.lat, p2.long);
            }

            this.shapeLengthMap[key] = length;
            util.log(`++ length for shape ${key}: ${length}`);
        }
    }

    async populateBoundingBox(){
        util.log("populateBoundingBox()");
        let rows = await this.getFile("shapes.txt");
        this.stopTimeMap = {};

        for (let r of rows){
            // util.log("JSON.stringify(r): " + JSON.stringify(r));

            let lat = r['shape_pt_lat'];
            let lon = r['shape_pt_lon'];
            this.area.update(lat, lon);
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

    interpolateWayPointTimes(wayPoints, stopTimes, stops) {
        let anchorList = this.createAnchorList(wayPoints, stopTimes, stops);
        let firstStop = true;

        // util.log(`interpolateWayPointTimes()``);
        // util.log(`- wayPoints.length: ${wayPoints.length}`);
        // util.log(`- stopTimes.length: ${stopTimes.length}`);

        for(let st of stopTimes){
            let sp = stops[st['stop_id']];

            if (firstStop){
                sp['first_stop'] = true;
                 firstStop = false;
            }
        }
        util.log(`- anchorList: ${anchorList}`);
        // util.log(`- anchorList.length: ${anchorList.length}`)

        for (let i=0; i<anchorList.length - 1; i++){
            let start = anchorList[i];
            let end = anchorList[i + 1];
            let tdelta = end['time'] - start['time'];
            let idelta = end['index'] - start['index'];

            // util.log('--------------------------')

            for (let j=0; j<idelta; j++){
                let fraction = j / idelta;
                let time = start['time'] + int(fraction * tdelta);
                wayPoints[start['index'] + j]['time'] = time;
                let hhmmss = util.seconds_to_hhmmss(time);
                // util.log(`--- ${hhmmss}`);
            }
        }
        // util.log('--------------------------')

        let index = anchorList[0]['index']
        let time = anchorList[0]['time']

        while (index >= 0){
            index -= 1;
            wayPoints[index]['time'] = time;
        }
        index = anchorList[-1]['index'];
        time = anchorList[-1]['time'];

        while (index < wayPoints.length){
            wayPoints[index]['time'] = time;
            index += 1;
        }
        // // //  REMOVE ME: for testing only
        for (let i=0; i<wayPoints.length; i++){
            if (!('time' in wayPoints[i])){
                util.log(`.. ${i}`);
            }
        }
    }

    // For simpler trips `shape_dist_traveled` is optional. If the attribute is missing, we can take the following approach:
    // - for each stop, assign a fraction of arrival time at that stop to total trip time -> fractions list
    // - for i in len(fractions)
    // -   compute index into shape list as int(fractions[i] * len(wayPoints) -> anchorList
    // - explore neighboring points in wayPoints for each match_list entry, going up to half the points until previous or next anchor point
    // - adjust anchor points if closer match found
    // - repeat until stable (current anchor list equals pevious anchor list) of max tries reached

    createAnchorListIteratively(wayPoints, stopTimes, stops){
        let startSeconds = stopTimes[0]['arrival_time'];
        let stopSeconds = stopTimes[-1]['arrival_time'];
        let totalSeconds = stopSeconds - startSeconds;
        // print(f'- totalSeconds: {totalSeconds}')
        let anchorList = [];

        for (let i=0; i<stopTimes.length; i++){
            // print(`-- i: ${i}`);
            let seconds = stopTimes[i]['arrival_time'] - startSeconds;
            // print(`-- seconds: ${seconds}`);
            let frac = seconds / totalSeconds;
            // print(`-- frac: ${frac}`);
            let j = frac * (wayPoints.length - 1);
            // print(`-- j: ${j}`);
            anchorList.append({'index': j, 'time': stopTimes[i]['arrival_time']});
        }
        for (let i=0; i<20; i++){
            let lastAnchorList = JSON.parse(JSON.stringify(anchorList));

            // print(`-- lastAnchorList: ${lastAnchorList}`)

            // explore neighbors in anchorList, potentially changing index fields
            for (j=0; j<lastAnchorList.length; j++){
                // print(`-- j: ${j}`)
                let c = lastAnchorList[j];
                // print(`-- c: ${c}`)

                let p = c;
                if (j > 0){
                    p = lastAnchorList[j - 1];
                }
                let n = c;
                if (j < lastAnchorList.length - 1){
                    let n = lastAnchorList[j + 1];
                }

                let p1 = stops[stopTimes[j]['stop_id']];
                let p2 = wayPoints[c['index']];
                let min_diff = util.haversine_distance(p1['lat'], p1['long'], p2['lat'], p2['long']);
                let min_index = c['index'];
                // print(`-- min_index: ${min_index}`)

                // print(`-- c["index"]: ${c["index"]}`)
                // print(`-- p["index"]: ${p["index"]}`)
                // print(`-- n["index"]: ${n["index"]}`)

                let kf = int(p['index'] + math.ceil((c['index'] - p['index']) / 2));
                let kt = int(c['index'] + (n['index'] - c['index']) / 2);

                // print(`-- kf: ${kf}, kt: ${kt}`)

                for (let k=kf; k<kt; k++){
                    p2 = wayPoints[k];
                    diff = util.haversineDistance(p1['lat'], p1['long'], p2['lat'], p2['long']);

                    if (diff < min_diff){
                        min_diff = diff;
                        min_index = k;
                    }
                }
                // print(`++ min_index: ${min_index}``)
                anchorList[j]['index'] = min_index;
            }
            // print(`-- anchorList     : ${anchorList}``)

            let stable = true;

            for (let j=0; j<anchorList.length; j++){
                i1 = anchorList[j]['index'];
                i2 = lastAnchorList[j]['index'];

                if (i1 != i2){
                    // print(`* ${i1} != ${i2}``);
                    stable = False;
                    break;
                }
            }
            if (stable) break;
        }
        // print(`++ anchorList     : ${anchorList}``)

        return anchorList;
    }

    // anchorList establishes a relationship between the list of stops for a trip and that trip's way points as described in shapes.txt.
    // The list has one entry per stop. Each entry has an index into wayPoints to map to the closest shape point and the time when
    // a vehicle is scheduled to arrive at that point (as given in stopTimes.txt). anchorList is an intermediate point in assigning
    // timestamps to each point of the trip shape

    // Complex trips with self-intersecting or self-overlapping segments are supposed to have `shape_dist_traveled` attributes for their
    // shape.txt and stopTimes.txt entries. This allows for straight-forward finding of shape points close to stops.

    createAnchorList(wayPoints, stopTimes, stops){
        // print(` - wayPoints.length: ${wayPoints.length}`);
        // print(` - stopTimes.length: ${stopTimes.length}`);
        // print(` - stops.length: ${stops.length}`);

        let annotated = true;

        for (let i=0; i<stopTimes.length; i++){
            if (stopTimes[i]['traveled'] === null){
                annotated = false;
                break;
            }
        }

        if (annotated){
            for (let i=0; i<wayPoints.length; i++){
                if (wayPoints[i]['traveled'] === null){
                    annotated = false;
                    break;
                }
            }
        }

        if (!annotated){
            return this.createAnchorListIteratively(wayPoints, stopTimes, stops);
        }
        anchorList = [];

        for (i=0; i<stopTimes.length; i++){
            let traveled = stopTimes[i]['traveled'];
            let time = stopTimes[i]['arrival_time'];
            // what does this mean?
            let min_difference = Number.MAX_VALUE;
            let min_index = -1;

            for (j=0; i< wayPoints.length; j++){
                let t = wayPoints[j]['traveled'];
                let diff = Math.abs(traveled - t);

                if (diff < min_difference){
                    let min_difference = diff;
                    let min_index = j;
                }
            }
            anchorList.append({'index': min_index, 'time': time})
        }
        return anchorList;
    }

    makeTripSegments(trip_id, tripName, firstStop, wayPoints, maxSegmentLength){
        util.log(`- make_trip_segments()`);
        util.log(`- maxSegmentLength: ${maxSegmentLength}`);
        util.log(`- wayPoints: ${wayPoints}`);

        let segmentStart = 0;
        let index = segmentStart;
        lastIndex = index;
        segmentLength = 0;
        firstSegment = True;
        segmentCount = 1;

        // let area = Area()
        indexList = [];
        segmentList = [];

        skirtSize = max(int(maxSegmentLength / 10), 500)
        util.log(`- skirtSize: ${skirtSize}`);

        while (index < wayPoints.length){
            let lp = wayPoints[lastIndex];
            let p = wayPoints[index];

            this.area.update(p['lat'], p['long']);

            let gridIndex = this.grid.get_index(p['lat'], p['long']);
            if  (!(gridIndex in indexList)){
                indexList.append(gridIndex);
            }

            let distance = util.haversineDistance(lp['lat'], lp['long'], p['lat'], p['long']);
            segmentLength += distance;

            if (segmentLength >= maxSegmentLength || index === wayPoints.length - 1){
                this.area.extend(skirtSize);

                if (segmentStart === 0 && wayPoints[segmentStart]['time'] === wayPoints[index]['time']){
                    util.log(`0 duration first segment for trip ${trip_id}`);
                }

                let stop_id = null;
                if (firstSegment){
                    firstSegment = false;
                    stop_id = firstStop['stop_id'];
                }
                let segment = Segment(
                    segmentCount,
                    trip_id,
                    trip_name,
                    firstStop['arrival_time'],
                    stop_id,
                    this.area,
                    wayPoints[segmentStart]['time'],
                    wayPoints[index]['time'],
                    wayPoints[segmentStart]['file_offset'],
                    wayPoints[index]['file_offset']
                );

                segmentList.append(segment);
                segmentCount += 1;

                for (let i of indexList){
                    self.grid.add_segment(segment, i);
                }

                segmentLength = 0;
                // area = Area()
                indexList = [];

                index += 1;
                lastIndex = index;
                segmentStart = index;

                p = wayPoints[max(segmentStart - 1, 0)];
                this.area.update(p['lat'], p['long']);

                continue;
            }
            lastIndex = index;
            index += 1;
        }
        for (let s of segmentList){
            s.setSegmentsPerTrip(segmentCount - 1);
        }
    }
}