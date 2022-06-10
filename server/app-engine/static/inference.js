
const agencyID = "test-scott-express"
const vehicleID = "123"
const BASE_URL = 'https://storage.googleapis.com/graas-resources/gtfs-archive'
const agencyURL = `${BASE_URL}/${agencyID}/`;
var dow = -1;
var epochSeconds = -1;
var tripCandidates;
var lastCandidateFlush;
var stops;
var lastCandidateFlush;
var calendarMap;
var stopTimeMap;
var shapeMap;
var routeMap;
var shapeLengthMap;

async function initialize() {
    util.log("initialize()")
    calendarMap = await getCalendarMap();
    routeMap = await getRouteMap();

    tripCandidates = [];
    lastCandidateFlush = Date.now();

    if (dow < 0) {
        dow = (new Date()).getDay();
        util.log(`- dow: ${dow}`)
    }
    if (epochSeconds < 0) {
        epochSeconds = Date.now();
        util.log(`- epochSeconds: ${epochSeconds}`)
    }

    stops = getStops();
    util.log(`-- stops: ${stops}`);
    stopTimeMap = await preloadStopTimes();
    shapeMap = await preloadShapes();

    shapeLengthMap = await computeShapeLengths();
    //     self.block_map = {}
    const tripSet = new Set();


    // set up area and grid
    // self.area = Area()
    // self.populateBoundingBox(self.area)
    // util.debug(f'- self.area: {self.area}')
    // self.grid = Grid(self.area, subdivisions)
}

async function getFile(fileName){
    let url = agencyURL + fileName;
    util.log('- fetching from ' + url);
    let response = await util.timedFetch(url, {method: 'GET'});
    let text = await response.text();
    return csvToArray(text);
}

async function getCalendarMap(){
    util.log('get_calendar_map()');
    rows = await getFile("calendar.txt");
    let tempCalendarMap = {};
    let dow = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

    for (let r of rows){
        let service_id = r['service_id'];
        util.log(`-- service id: ${service_id}`)
        let cal = [];
        for (let d of dow){
            cal.push(r[d]);
            // util.log(`-- cal: ${cal}`);
        }
        tempCalendarMap[service_id] = {'cal': cal, 'start_date': r['start_date'], 'end_date': r['end_date']};
    }
    return tempCalendarMap
}

async function getRouteMap(){
    util.log('getRouteMap()');
    rows = await getFile("routes.txt");
    let tempRouteMap = {};
    for (let r of rows){
        let route_id = r['route_id'];
        util.log(`-- route_id: ${route_id}`)
        short_name = r['route_short_name'];
        long_name = r['route_long_name']
        name = (short_name.length > 0 ? short_name : long_name)

        tempRouteMap[route_id] = {'name': name}
    }
    return tempRouteMap;
}

async function getStops(){
    util.log('getStops()');
    rows = await getFile("stops.txt");
    let stopList = {};
    for (let r of rows){
        let id = r['stop_id'];
        let lat = r['stop_lat'];
        let lon = r['stop_lon'];
        stopList[id] = {'lat': lat, 'long': lon}
    }
    return stopList
}

async function preloadStopTimes(){
    util.log('preloadStopTimes()');
    rows = await getFile("stops.txt");
    let tempStopTimeMap = {};

    for (let r of rows){
        // util.log("JSON.stringify(r): " + JSON.stringify(r));

        let trip_id = r['trip_id'];
        let arrival_time = r['arrival_time'];
        if (util.isNullOrUndefined(arrival_time)) continue;
        let stop_id = r['stop_id'];
        let stop_sequence = r['stop_sequence'];

        let slist = tempStopTimeMap[trip_id];

        if (util.isNullOrUndefined(slist)){
            slist = [];
            tempStopTimeMap[trip_id] = slist;
        }

        let entry = {'arrival_time': util.hhmmssToSeconds(arrival_time), 'stop_id': stop_id, 'stop_sequence': stop_sequence}

        let sdt = r['shape_dist_traveled'];

        if (!util.isNullOrUndefined(sdt) && sdt.length > 0){
            entry['traveled'] = sdt;
        }
        util.log(`- entry: ${entry}`)
        sxlist.push(entry)
    }
    return tempStopTimeMap;
}

async function preloadShapes(){
    util.log('preloadShapes()');
    rows = await getFile("shapes.txt");
    let tempShapeMap = {};

    for (let r of rows){
        // util.log("JSON.stringify(r): " + JSON.stringify(r));

        let shape_id = r['shape_id'];
        let lat = r['shape_pt_lat'];
        let lon = r['shape_pt_lon'];

        let plist = tempShapeMap[shape_id];

        if (util.isNullOrUndefined(plist)){
            plist = [];
            tempShapeMap[shape_id] = plist;
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
    return tempShapeMap;
}

function computeShapeLengths() {
    tempShapeLengthMap = {};
    util.log("shapeMap: " + JSON.stringify(shapeMap));
    for (let [key, value] of Object.entries(shapeMap)){
        util.log("shapeID: " + key);
        let pointList = value;
        let length = 0;

        for (let i = 0; i < pointList.length - 1; i++){
            util.log("pointList[i]: " + pointList[i]);
            let p1 = pointList[i];
            let p2 = pointList[i+1];
            length += util.haversineDistance(p1.lat, p1.long, p2.lat, p2.long);
        }
        tempShapeLengthMap[key] = length;
        util.long(`++ length for shape ${key}: ${length}`);
    }
}

//     def compute_shape_lengths(self):
//         self.shape_length_map = {}

//         for shape_id in self.shape_map:
//             #util.debug(f'-- shape_id: {shape_id}')
//             point_list = self.shape_map[shape_id]
//             #util.debug(f'-- point_list: {point_list}')
//             length = 0

//             for i in range(len(point_list) - 1):
//                 p1 = point_list[i];
//                 #util.debug(f'--- p1: {p1}')
//                 p2 = point_list[i + 1]
//                 #util.debug(f'--- p2: {p2}')

//                 length += util.haversine_distance(p1['lat'], p1['long'], p2['lat'], p2['long'])

//             self.shape_length_map[shape_id] = length
//             util.debug(f'++ length for shape {shape_id}: {util.get_display_distance(length)}')
// }

//     self.compute_shape_lengths()
//     self.block_map = {}


//     with platform.get_text_file_contents(path + '/trips.txt') as f:
//         reader = csv.DictReader(f)
//         rows = list(reader)
//         count = 1

//         load_timer = Timer('load')

//         for r in rows:
//             loop_timer = Timer('loop')
//             trip_id = r['trip_id']
//             service_id = r['service_id']
//             shape_id = r['shape_id']

//             trip_set.add(trip_id)

//             if not service_id in calendar_map:
//                 util.debug(f'* service id \'{service_id}\' not found in calendar map, skipping trip \'{trip_id}\'')
//                 continue

//             cal = calendar_map[service_id].get('cal', None)
//             if cal is not None and cal[dow] != 1:
//                 util.debug(f'* dow \'{dow}\' not set, skipping trip \'{trip_id}\'')
//                 continue

//             start_date = calendar_map[service_id].get('start_date', None)
//             end_date = calendar_map[service_id].get('end_date', None)

//             if start_date is not None and end_date is not None:
//                 start_seconds = util.get_epoch_seconds(start_date)
//                 end_seconds = util.get_epoch_seconds(end_date)
//                 if epoch_seconds < start_seconds or epoch_seconds > end_seconds:
//                     util.debug(f'* trip date outside service period (start: {start_date}, end: {end_date}), skipping trip \'{trip_id}\'')
//                     continue

//             util.debug(f'')
//             util.debug(f'-- trip_id: {trip_id} ({count}/{len(rows)})')
//             count += 1

//             route_id = r['route_id']
//             shape_id = r['shape_id']
//             #util.debug(f'-- shape_id: {shape_id}')
//             timer = Timer('way points')
//             way_points = self.get_shape_points(shape_id)
//             #util.debug(timer)
//             #util.debug(f'-- way_points: {way_points}')
//             util.debug(f'-- len(way_points): {len(way_points)}')

//             if len(way_points) == 0:
//                 util.debug(f'* no way points for trip_id \'{trip_id}\', shape_id \'{shape_id}\'')
//                 continue

//             timer = Timer('stop times')
//             stop_times = self.get_stop_times(trip_id)

//             block_id = r.get('block_id', None)
//             #util.debug(f'-- block_id: {block_id}')

//             if block_id is not None and len(block_id) > 0 and stop_times is not None and len(stop_times) > 0:
//                 trip_list = self.block_map.get(block_id, None)

//                 if trip_list is None:
//                     trip_list = []
//                     self.block_map[block_id] = trip_list

//                 start_time = stop_times[0].get('arrival_time', None)
//                 end_time = stop_times[-1].get('arrival_time', None)

//                 if start_time is not None and end_time is not None:
//                     trip_list.append({
//                         'trip_id': trip_id,
//                         'start_time': start_time,
//                         'end_time': end_time
//                     })

//             #util.debug(timer)
//             #util.debug(f'-- stop_times: {stop_times}')
//             util.debug(f'-- len(stop_times): {len(stop_times)}')
//             timer = Timer('interpolate')
//             self.interpolate_way_point_times(way_points, stop_times, self.stops)
//             #util.debug(timer)

//             #trip_name = route_map[route_id]['name'] + ' @ ' + util.seconds_to_ampm_hhmm(stop_times[0]['arrival_time'])
//             trip_name = trip_id + ' @ ' + util.seconds_to_ampm_hhmm(stop_times[0]['arrival_time'])
//             util.debug(f'-- trip_name: {trip_name}')
//             shape_length = self.shape_length_map[shape_id]

//             if shape_length is None:
//                 segment_length = 2 * util.FEET_PER_MILE
//             else:
//                 segment_length = int(shape_length / 30)

//             #util.debug(f'-- segment_length: {segment_length}')
//             timer = Timer('segments')
//             self.make_trip_segments(trip_id, trip_name, stop_times[0], way_points, segment_length)
//             #util.debug(timer)
//             #util.debug(loop_timer)

//     util.debug(f'-- self.block_map: {json.dumps(self.block_map, indent = 4)}')

//     util.debug(load_timer)
//     util.debug(f'-- self.grid: {self.grid}')

//     for tid in self.stop_time_map:
//         if not tid in trip_set:
//             self.stop_time_map.pop(tid)

//     self.shape_map = {}


// Thanks to: https://www.bennadel.com/blog/1504-ask-ben-parsing-csv-strings-with-javascript-exec-regular-expression-command.htm
function csvToArray(str, delimiter = ",") {
  // slice from start of text to the first \n index
  // use split to create an array from string by delimiter
  const headers = str.slice(0, str.indexOf("\n")).split(delimiter);

  // slice from \n index + 1 to the end of the text
  // use split to create an array of each csv value row
  const rows = str.slice(str.indexOf("\n") + 1).split("\n");
  // Map the rows
  // split values from each row into an array
  // use headers.reduce to create an object
  // object properties derived from headers:values
  // the object passed as an element of the array
  const arr = rows.map(function (row) {
    const values = row.split(delimiter);
    const el = headers.reduce(function (object, header, index) {
      object[header] = values[index];
      return object;
    }, {});
    return el;
  });

  // return the array
  // (hacky scott fix for null last row added)
  return arr.slice(0,-1);
}