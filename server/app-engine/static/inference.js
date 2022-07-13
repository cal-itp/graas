if (typeof util === 'undefined' || util === null) {
    var platform = require('../static/platform');
    var util = require('../static/gtfs-rt-util');
    var grid = require('../static/grid');
    var timer = require('../static/timer');
    var area = require('../static/area');
    var segment = require('../static/segment');
}

const BASE_URL = 'https://storage.googleapis.com/graas-resources/gtfs-archive'
const STOP_PROXIMITY = 150;
const SCORE_THRESHOLD = 7;
const MIN_FLUSH_TIME_DELTA = 30 * 60;
const STOP_CAP = 10;

(function(exports) {
    exports.TripInference = class {

        constructor(path, url, agencyID, vehicleID, subdivisions, dow = -1, epochSeconds = -1) {
            util.log("TripInference.TripInference()");

            if (!path.endsWith('/')) {
                path += '/';
            }

            this.path = path;
            util.log(`- this.path: ${this.path}`);
            this.url = url;
            util.log(`- this.url: ${this.url}`);
            this.agencyID = agencyID;
            this.vehicleID = vehicleID;
            this.subdivisions = subdivisions;
            this.dow = dow;
            this.epochSeconds = epochSeconds;
        }

        async init(){
            util.updateCacheIfNeeded(this.path, this.url);

            this.tripCandidates = {};
            this.lastCandidateFlush = Date.now();

            const calendarMap = await this.getCalendarMap();
            util.log("- calendarMap: " + JSON.stringify(calendarMap, null, 2));
            const routeMap = await this.getRouteMap();
            util.log("- routeMap: " + JSON.stringify(routeMap, null, 2));

            this.area = new area.Area();
            await this.populateBoundingBox();
            util.log(`- this.area: ${this.area}`);
            this.grid = new grid.Grid(this.area, this.subdivisions);

            if (this.dow < 0) {
                this.dow = (new Date()).getDay();
            }
            util.log(`- dow: ${this.dow}`);

            if (this.epochSeconds < 0) {
                this.epochSeconds = Math.floor(Date.now() / 1000);
            }
            util.log(`- this.epochSeconds: ${new Date(this.epochSeconds * 1000)}`);

            this.stops = await this.getStops();
            // util.log(`-- JSON.stringify(this.stops): ${JSON.stringify(this.stops)}`);

            await this.preloadStopTimes();
            // util.log(`-- JSON.stringify(this.stopTimeMap): ${JSON.stringify(this.stopTimeMap)}`);

            await this.preloadShapes();

            this.computeShapeLengths();
            this.blockMap = {}
            let tripSet = new Set();

            let rows = await this.getFile("trips.txt");
            let count = 1;
            let load_timer = new timer.Timer('load');
            for (let r of rows){
                let loopTimer = new timer.Timer('loop');
                let trip_id = r['trip_id'];
                // util.log(`trip_id: ${trip_id}`);
                let service_id = r['service_id'];
                let shape_id = r['shape_id'];

                tripSet.add(trip_id);

                if (!(service_id in calendarMap)){
                    // util.log(`* service id \'${service_id}\' not found in calendar map, skipping trip \'${trip_id}\'`);
                    continue;
                }
                let cal = calendarMap[service_id]['cal'];
                // util.log("cal: " + cal);
                // util.log("JSON.stringify(cal): " + JSON.stringify(cal));
                // util.log("cal[this.dow]: " + cal[this.dow]);
                if (cal !== null && cal[this.dow] !== "1"){
                    // util.log(`* dow \'${this.dow}\' not set, skipping trip \'${trip_id}\'`);
                    continue;
                }
                let startDate = calendarMap[service_id]['start_date'];
                let endDate = calendarMap[service_id]['end_date'];

                if (startDate !== null && endDate !== null){
                    let startSeconds = util.getEpochSeconds(startDate);
                    // util.getEpochSeconds returns 0AM on that date, needs to be 24hr later, so that end date is included
                    let endSeconds = util.getEpochSeconds(endDate) + util.SECONDS_PER_DAY;
                    // util.log("startSeconds: " + startSeconds);
                    // util.log("endSeconds: " + endSeconds);
                    if (this.epochSeconds < startSeconds || this.epochSeconds > endSeconds){
                        // util.log(`* trip date outside service period (start: ${startDate}, end: ${endDate}), skipping trip \'${trip_id}\'`);
                        continue;
                    }
                }
                // util.log('');
                // util.log(`-- trip_id: ${trip_id} (${count}/${rows.length})`);
                count += 1;

                let route_id = r['route_id'];
                // util.log(`-- shape_id: ${shape_id}`);
                let mainTimer = new timer.Timer('way points');
                let wayPoints = this.getShapePoints(shape_id);
                // util.log(timer);
                // util.log(`-- wayPoints: ${wayPoints}`)
                // util.log(`-- wayPoints.length: ${wayPoints.length}`)

                if (wayPoints.length === 0){
                    // util.log(`* no way points for trip_id \'${trip_id}\', shape_id \'${shape_id}\'`);
                    continue;
                }

                mainTimer = new timer.Timer('stop times');
                let stopTimes = this.getStopTimes(trip_id);

                let block_id = ['block_id'];
                // util.log(`-- block_id: ${block_id}`);

                if (block_id !== null && block_id.length > 0 && stopTimes !== null && stopTimes.length > 0){
                    let tripList = [];
                    // let tripList = this.blockMap[block_id];
                    // if (util.isNullOrUndefined(tripList)){
                    //     tripList = [];
                    //     this.blockMap[block_id] = tripList;
                    // }
                    let startTime = stopTimes[0]['arrival_time'];
                    let endTime = stopTimes[ stopTimes.length - 1]['arrival_time'];
                    // util.log("startTime: " + startTime);
                    // util.log("endTime: " + endTime);
                    // util.log("trip_id: " + trip_id);
                    if (startTime !== null && endTime !== null){
                        tripList.push({
                            'trip_id': trip_id,
                            'start_time': startTime,
                            'end_time': endTime
                        });
                    }
                }
                // util.log(timer);
                // util.log(`-- stopTimes: ${stopTimes}`);
                // util.log(`-- len(stopTimes): ${stopTimes.length}`);
                mainTimer = new timer.Timer('interpolate');
                this.interpolateWayPointTimes(wayPoints, stopTimes, this.stops);
                // util.log(timer);

                // // trip_name = route_map[route_id]['name'] + ' @ ' + util.seconds_to_ampm_hhmm(stopTimes[0]['arrival_time'])
                let tripName = trip_id + ' @ ' + util.getHMForSeconds(stopTimes[0]['arrival_time'], true);
                // util.log(`-- tripName: ${tripName}`);
                let shapeLength = this.shapeLengthMap[shape_id];
                let segmentLength = null;
                if (shapeLength === null){
                    segmentLength = 2 * util.FEET_PER_MILE;
                }
                else{
                    segmentLength = shapeLength;
                }

                // util.log(f'-- segmentLength: {segmentLength}')
                mainTimer = new timer.Timer('segments');
                await this.makeTripSegments(trip_id, tripName, stopTimes[0], wayPoints, segmentLength);
                // util.log(timer)
                // util.log(loopTimer)
            }
            // util.log(`-- this.blockMap: ${JSON.stringify(this.blockMap)}`);

            // util.log(load_timer);
            // util.log(`-- JSON.stringify(this.grid): ${JSON.stringify(this.grid)}`);

            for (let tid in this.stopTimeMap){
                if (!(tid in tripSet)){
                    delete this.stopTimeMap.tid;
                }
            }
            // Why?
            this.shapeMap = {}

            util.log('exiting early...');
            process.exit();
        }

        async getFile(fileName){
            //util.log('inference.getFile()');
            //util.log('- fileName: ' + fileName);

            /*
            let url = `${BASE_URL}/${this.agencyID}/${fileName}`;
            // util.log('- fetching from ' + url);
            let response = await util.timedFetch(url, {method: 'GET'});
            let text = await response.text();*/

            // ### TODO: port csv DictReader and use instead
            const text = platform.readFile(fileName);
            //util.log('- text: ' + text);
            return util.csvToArray(text);
        }

        async getCalendarMap(){
            // util.log('getCalendarMap()');
            let rows = await this.getFile("calendar.txt");
            const calendarMap = {};
            let dow = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

            for (let r of rows){
                let service_id = r['service_id'];
                util.log(`-- service id: ${service_id}`)
                let cal = [];
                for (let d of dow){
                    cal.push(r[d]);
                    // util.log(`-- cal: ${cal}`);
                }
                calendarMap[service_id] = {'cal': cal, 'start_date': r['start_date'], 'end_date': r['end_date']};
            }

            return calendarMap;
        }

        async getRouteMap(){
            // util.log('getRouteMap()');
            let rows = await this.getFile("routes.txt");
            const routeMap = {};

            for (let r of rows){
                let route_id = r['route_id'];
                // util.log(`-- route_id: ${route_id}`)
                let shortName = r['route_short_name'];
                let longName = r['route_long_name'];
                let name = (!util.isNullUndefinedOrBlank(shortName) ? shortName : longName);

                routeMap[route_id] = {'name': name};
            }

            return routeMap;
        }

        async getStops(){
            // util.log('getStops()');
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
            // util.log('preloadStopTimes()');
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
                // util.log(`- entry: ${entry}`)
                slist.push(entry)
            }
        }

        async preloadShapes(){
            // util.log('preloadShapes()');
            let rows = await this.getFile("shapes.txt");
            this.shapeMap = {};

            for (let i=0; i<rows.length; i++){
                // util.log("JSON.stringify(r): " + JSON.stringify(r));

                let shape_id = rows[i]['shape_id'];
                let lat = parseFloat(rows[i]['shape_pt_lat']);
                let lon = parseFloat(rows[i]['shape_pt_lon']);

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
            // util.log("shapeMap: " + JSON.stringify(this.shapeMap));
            for (let [key, value] of Object.entries(this.shapeMap)){
                // util.log("shapeID: " + key);
                let pointList = value;
                let length = 0;

                for (let i = 0; i < pointList.length - 1; i++){
                    // util.log("JSON.stringify(pointList[i]): " + JSON.stringify(pointList[i]));
                    let p1 = pointList[i];
                    let p2 = pointList[i+1];
                    length += util.haversineDistance(p1.lat, p1.lon, p2.lat, p2.lon);
                }

                this.shapeLengthMap[key] = length;
                util.log(`++ length for shape ${key}: ${util.getDisplayDistance(length)}`);
            }
        }

        async populateBoundingBox(){
            // util.log("populateBoundingBox()");
            let rows = await this.getFile("shapes.txt");
            this.stopTimeMap = {};

            for (let r of rows){
                // util.log("JSON.stringify(r): " + JSON.stringify(r));

                let lat = parseFloat(r['shape_pt_lat']);
                let lon = parseFloat(r['shape_pt_lon']);
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
            // util.log(`- JSON.stringify(anchorList): ${JSON.stringify(anchorList)}`);
            // util.log(`- anchorList.length: ${anchorList.length}`)

            for (let i=0; i<anchorList.length - 1; i++){
                let start = anchorList[i];
                let end = anchorList[i + 1];
                let tdelta = end['time'] - start['time'];
                let idelta = end['index'] - start['index'];

                // util.log('--------------------------')

                for (let j=0; j<idelta; j++){
                    let fraction = j / idelta;
                    let time = start['time'] + parseInt(fraction * tdelta);
                    wayPoints[start['index'] + j]['time'] = time;
                    let hhmmss = util.secondsToHhmmss(time);
                    // util.log(`--- ${hhmmss}`);
                }
            }
            // util.log('--------------------------')

            let index = anchorList[0]['index']
            let time = anchorList[0]['time']
            while (index >= 0){
                index -= 1;
                // Since index is 0, first thing it hits is wayPoints[-1], which works in python but not java.
                // Need to doublecheck that this is the intent
                if(index === -1){
                    wayPoints[wayPoints.length-1]['time'] = time;
                    break;
                }

                wayPoints[index]['time'] = time;
            }
            index = anchorList[anchorList.length - 1]['index'];
            time = anchorList[anchorList.length - 1]['time'];

            while (index < wayPoints.length){
                wayPoints[index]['time'] = time;
                index += 1;
            }
            // // //  REMOVE ME: for testing only
            for (let i=0; i<wayPoints.length; i++){
                if (!('time' in wayPoints[i])){
                    // util.log(`.. ${i}`);
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
            let stopSeconds = stopTimes[stopTimes.length - 1]['arrival_time'];
            let totalSeconds = stopSeconds - startSeconds;
            // util.log(f'- totalSeconds: {totalSeconds}')
            let anchorList = [];

            for (let i=0; i<stopTimes.length; i++){
                // util.log(`-- i: ${i}`);
                let seconds = stopTimes[i]['arrival_time'] - startSeconds;
                // util.log(`-- seconds: ${seconds}`);
                let frac = seconds / totalSeconds;
                // util.log(`-- frac: ${frac}`);
                let j = parseInt(frac * (wayPoints.length - 1));
                let listItem = {'index': j,
                                'time': stopTimes[i]['arrival_time']
                            };
                anchorList.push(listItem);
                // util.log("JSON.stringify(listItem): " + JSON.stringify(listItem));
                // util.log("JSON.stringify(anchorList): " + JSON.stringify(anchorList));
            }
            for (let i=0; i<20; i++){
                // util.log("i: " + i);
                // let lastAnchorList = Object.assign({}, anchorList);
                let lastAnchorList = JSON.parse(JSON.stringify(anchorList));

                // explore neighbors in anchorList, potentially changing index fields
                for (let j=0; j<lastAnchorList.length; j++){
                    // util.log(`-- j: ${j}`)
                    let c = lastAnchorList[j];
                    // util.log("JSON.stringify(c): " + JSON.stringify(c));
                    // util.log("c['index']: " + c['index']);

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
                    let min_diff = util.haversineDistance(p1['lat'], p1['lon'], p2['lat'], p2['lon']);
                    let min_index = c['index'];
                    // util.log(`-- min_index: ${min_index}`)

                    // util.log(`-- c["index"]: ${c["index"]}`)
                    // util.log(`-- p["index"]: ${p["index"]}`)
                    // util.log(`-- n["index"]: ${n["index"]}`)

                    let kf = p['index'] + Math.ceil((c['index'] - p['index']) / 2);
                    let kt = c['index'] + (n['index'] - c['index']) / 2;

                    // util.log(`-- kf: ${kf}, kt: ${kt}`)

                    for (let k=kf; k<kt; k++){
                        let p2 = wayPoints[k];
                        let diff = util.haversineDistance(p1['lat'], p1['long'], p2['lat'], p2['long']);

                        if (diff < min_diff){
                            let min_diff = diff;
                            let min_index = k;
                        }
                    }
                    // util.log(`++ min_index: ${min_index}`)
                    anchorList[j]['index'] = min_index;
                }
                // util.log(`-- anchorList : ${anchorList}`);

                let stable = true;

                for (let j=0; j<anchorList.length; j++){
                    let i1 = anchorList[j]['index'];
                    let i2 = lastAnchorList[j]['index'];
                    if (i1 !== i2){
                        // util.log(`* ${i1} != ${i2}``);
                        stable = false;
                        break;
                    }
                }
                if (stable) break;
            }
            // util.log(`++ JSON.stringify(anchorList) : ${JSON.stringify(anchorList)}`);

            return anchorList;
        }

        // anchorList establishes a relationship between the list of stops for a trip and that trip's way points as described in shapes.txt.
        // The list has one entry per stop. Each entry has an index into wayPoints to map to the closest shape point and the time when
        // a vehicle is scheduled to arrive at that point (as given in stopTimes.txt). anchorList is an intermediate point in assigning
        // timestamps to each point of the trip shape

        // Complex trips with self-intersecting or self-overlapping segments are supposed to have `shape_dist_traveled` attributes for their
        // shape.txt and stopTimes.txt entries. This allows for straight-forward finding of shape points close to stops.

        createAnchorList(wayPoints, stopTimes, stops){
            // util.log(` - wayPoints.length: ${wayPoints.length}`);
            // util.log(` - stopTimes.length: ${stopTimes.length}`);
            // util.log(` - stops.length: ${stops.length}`);

            let annotated = true;

            for (let i=0; i<stopTimes.length; i++){
                if (util.isNullOrUndefined(stopTimes[i]['traveled'])){
                    annotated = false;
                    break;
                }
            }

            if (annotated){
                for (let i=0; i<wayPoints.length; i++){
                    if (util.isNullOrUndefined(wayPoints[i]['traveled'])){
                        annotated = false;
                        break;
                    }
                }
            }

            if (!annotated){
                return this.createAnchorListIteratively(wayPoints, stopTimes, stops);
            }
            let anchorList = [];

            for (let i=0; i<stopTimes.length; i++){
                let traveled = stopTimes[i]['traveled'];
                let time = stopTimes[i]['arrival_time'];
                let min_difference = Number.MAX_VALUE;
                let min_index = -1;

                for (let j=0; i< wayPoints.length; j++){
                    let t = wayPoints[j]['traveled'];
                    let diff = Math.abs(traveled - t);

                    if (diff < min_difference){
                        let min_difference = diff;
                        let min_index = j;
                    }
                }
                anchorList.push({'index': min_index, 'time': time})
            }
            return anchorList;
        }

        async makeTripSegments(trip_id, tripName, firstStop, wayPoints, maxSegmentLength){
            // Why are there so many segments?!?
            util.log(`- makeTripSegments()`);
            // util.log(`- maxSegmentLength: ${maxSegmentLength}`);
            util.log(`- trip_id: ${trip_id}`);
            util.log(`- tripName: ${tripName}`);
            util.log(`- wayPoints.length: ${wayPoints.length}`);

            let segmentStart = 0;
            let index = segmentStart;
            let lastIndex = index;
            let segmentLength = 0;
            let firstSegment = true;
            let segmentCount = 1;

            let segmentArea = new area.Area()
            let indexList = [];
            let segmentList = [];

            let skirtSize = Math.max(parseInt(maxSegmentLength / 10), 500)
            // util.log(`- skirtSize: ${skirtSize}`);

            while (index < wayPoints.length){
                let lp = wayPoints[lastIndex];
                let p = wayPoints[index];
                // util.log("p['lat']: " + p['lat']);
                // util.log("p['lon']: " + p['lon']);
                segmentArea.update(p['lat'], p['lon']);

                let gridIndex = this.grid.getIndex(p['lat'], p['lon']);
                if  (!(gridIndex in indexList)){
                    indexList.push(gridIndex);
                }

                let distance = util.haversineDistance(lp['lat'], lp['lon'], p['lat'], p['lon']);
                segmentLength += distance;

                if (segmentLength >= maxSegmentLength || index === wayPoints.length - 1){
                    segmentArea.extend(skirtSize);

                    if (segmentStart === 0 && wayPoints[segmentStart]['time'] === wayPoints[index]['time']){
                        // util.log(`0 duration first segment for trip ${trip_id}`);
                    }

                    let stop_id = null;
                    if (firstSegment){
                        firstSegment = false;
                        stop_id = firstStop['stop_id'];
                    }

                    let tripSegment = await new segment.Segment(
                        segmentCount,
                        trip_id,
                        tripName,
                        firstStop['arrival_time'],
                        stop_id,
                        segmentArea,
                        wayPoints[segmentStart]['time'],
                        wayPoints[index]['time'],
                        wayPoints[segmentStart]['file_offset'],
                        wayPoints[index]['file_offset']
                    );

                    segmentList.push(tripSegment);
                    segmentCount++;

                    for (let i of indexList){
                        this.grid.addSegment(tripSegment, i);
                    }

                    segmentLength = 0;
                    segmentArea = new area.Area()
                    indexList = [];

                    index++;
                    lastIndex = index;
                    segmentStart = index;

                    p = wayPoints[Math.max(segmentStart - 1, 0)];
                    segmentArea.update(p['lat'], p['lon']);

                    continue;
                }
                lastIndex = index;
                index++;
            }
            for (let s of segmentList){
                s.setSegmentsPerTrip(segmentCount - 1);
            }
        }

        async getTripId(lat, lon, seconds, tripIdFromBlock = null){
            // util.log("getTripId()");
            let segmentList = await this.grid.getSegmentList(lat, lon);
            let ret = {
                'trip_id': null,
                'stop_time_entities': null
            };

            if (segmentList === null){
                return ret;
            }

            util.log(`- segmentList.length: ${segmentList.length}`);
            // util.log(`- tripIdFromBlock: ${tripIdFromBlock}`);

            let stop_id = await this.getStopForPosition(lat, lon, STOP_PROXIMITY);

            let multiplier = 1
            // removing stop multiplier actually gives better results with training data set
            // if stop_id is not None:
            //     multiplier = 10

            let maxSegmentScore = 0;
            let timeOffset = 0;
            let shapes = await this.getFile("shapes.txt");
            // util.log(`JSON.stringify(segmentList): ${JSON.stringify(segmentList)}`);
            for (let segment of segmentList){
                if (tripIdFromBlock !== null && segment.trip_id !== tripIdFromBlock){
                    continue;
                }
                // util.log(`JSON.stringify(segment): ${JSON.stringify(segment)}`);
                util.log(`segment.trip_id: ${segment.trip_id}`);
                let result = await segment.getScore(lat, lon, seconds, shapes);
                // util.log(`JSON.stringify(segment.waypointList): ${JSON.stringify(segment.waypointList)}`);

                // util.log(`result: ${JSON.stringify(result)}`);
                let score = multiplier * result['score'];
                // util.log(`score: ${score}`);
                let time_offset = result['time_offset'];
                // util.log(`-- time_offset: ${time_offset}`);

                if (score <= 0){
                    continue;
                }

                if (score > maxSegmentScore){
                    maxSegmentScore = score;
                }

                let trip_id = segment.getTripId();

                let candidate = null;
                // util.log("JSON.stringify(this.tripCandidates: " + JSON.stringify(this.tripCandidates));
                if (trip_id in this.tripCandidates){
                    candidate = this.tripCandidates[trip_id];
                } else {
                    candidate = {'score': 0, 'name': segment.tripName};
                    this.tripCandidates[trip_id] = candidate;
                }
                // util.log("candidate['score']: " + candidate['score']);
                // util.log("score: " + score);
                candidate['score'] += score;
                // util.log("candidate['score']: " + candidate['score']);
                candidate['time_offset'] = time_offset;
                // util.log(`-- candidate["time_offset"]: ${candidate["time_offset"]}`);
            }

            if (maxSegmentScore > 0 && stop_id !== null){
                this.checkForTripStart(stop_id);
            }

            let maxScore = 0;
            let maxTripId = null;
            let candTimeOffset = null;
            for (let [key, value] of Object.entries(this.tripCandidates)){
                let trip_id = key;
                let cand = value;
                let score = cand['score'];
                let name = cand['name'];
                // util.log(`candidate update: id=${trip_id} trip-name=${util.to_b64(name)} score=${score}`)
                util.log(`candidate update: id=${trip_id} trip-name=${name} score=${score}`)

                if (score > maxScore){
                    maxScore = score;
                    maxTripId = trip_id;
                    candTimeOffset = cand['time_offset'];
                    // util.log(`-- candTimeOffset: ${candTimeOffset}`)
                }
            }

            util.log(`- maxScore: ${maxScore}`);

            if (maxScore >= SCORE_THRESHOLD){
                ret['trip_id'] = maxTripId
                ret['stop_time_entities'] = this.getStopTimeEntities(maxTripId, seconds, candTimeOffset)
            }
            return ret;
        }

        // NOTE: brute force approach that returns the *first*
        // stop within max_distance feet from lat/lon
        getStopForPosition(lat, lon, maxDistance){
            let stop_id = null;

            for (let [key, value] of Object.entries(this.stops)){
                if (util.haversineDistance(lat, lon, value['lat'], value['long']) < maxDistance){
                    let stop_id = key;
                    break;
                }
            }
            return stop_id;
        }
        checkForTripStart(stop_id){
            if (!(stop_id in this.stops)){
                return;
            }

            let stop = this.stops[stop_id];
            let delta = Date.now() - this.lastCandidateFlush;

            if ('first_stop' in stop && delta >= MIN_FLUSH_TIME_DELTA){
                this.resetScoring();
            }
        }
        resetScoring(){
            // util.log('+++ reset scoring! +++');
            this.tripCandidates = {};
            this.lastCandidateFlush = Date.now();
        }

        getStopTimeEntities(trip_id, daySeconds, offset){
            // util.log(`getStopTimeEntities()`);
            // util.log(`- trip_id: ${trip_id}`);
            // util.log(`- daySeconds: ${daySeconds}`);
            // util.log(`- offset: {offset}`);

            let index = this.getRemainingStopsIndex(trip_id, daySeconds + offset);
            // util.log(`- index: ${index}`);
            let stopList = this.stopTimeMap[trip_id];
            // util.log(`- stopList: ${stopList}`);
            let entities = [];
            let timestamp = Date.now();

            for (let i=0; i<stopList.length; i++){
                let s = stopList[i];
                // util.log(`-- s: ${s}`);

                let e = {
                    'agency_id': this.agency_id,
                    'trip_id': trip_id,
                    'stop_sequence': s['stop_sequence'],
                    'delay': offset,
                    'vehicle_id': this.vehicle_id,
                    'timestamp': timestamp
                };

                entities.push(e);
            }
            // util.log(`- entities: ${JSON.stringify(entities)}`);
            return entities;
        }
        // assumes that stopList entries are sorted by 'arrival_time'
        getRemainingStopsIndex(trip_id, daySeconds){
            // util.log(`getRemainingStopsIndex()`);
            // util.log(`- trip_id: ${trip_id}`);
            // util.log(`- daySeconds: {daySeconds}`);

            let stopList = this.stopTimeMap[trip_id];
            // util.log(`- stopList: ${stopList}`);
            let result = [];

            if (util.isNullOrUndefined(stopList)){
                return null;
            }

            for (let i=0; i<stopList.length; i++){
                if (stopList[i]['arrival_time'] >= daySeconds){
                    return i;
                }
            }

            return stopList.length;
        }
    }
}(typeof exports === 'undefined' ? this.inference = {} : exports));