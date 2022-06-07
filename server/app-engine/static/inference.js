
const agencyID = "test-scott-express"
const vehicleID = "123"
const BASE_URL = 'https://storage.googleapis.com/graas-resources/gtfs-archive'
const agencyURL = `${BASE_URL}/${agencyID}/`;
var dow = -1;
var epochSeconds = -1;
var tripCandidates;
var lastCandidateFlush;
var stops;
var calendarMap;
var lastCandidateFlush;
var stopTimeMap;


function initialize() {
    util.log("initialize()")
    calendarMap = getCalendarMap();
    routeMap = getRouteMap();

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
    preloadStopTimes();

    //     self.preload_shapes()

    //     self.compute_shape_lengths()
    //     self.block_map = {}

    //     trip_set = set()
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
    calendarMap = {}

    let dow = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']

    for (let r of rows){
        let service_id = r['service_id'];
        util.log(`-- service id: ${service_id}`)
        let cal = [];
        for (let d of dow){
            util.log(`row[d]: ${r[d]}`);
            cal.push(r[d]);
            util.log(`-- cal: ${cal}`);
        }
        calendarMap[service_id] = {'cal': cal, 'start_date': r['start_date'], 'end_date': r['end_date']};
    }
    return calendarMap
}

async function getRouteMap(){
    util.log('getRouteMap()');
    rows = await getFile("routes.txt");
    routeMap = {}

    for (let r of rows){
        let route_id = r['route_id'];
        util.log(`-- route_id id: ${route_id}`)
        short_name = r['route_short_name'];
        long_name = r['route_long_name']
        name = (short_name.length > 0 ? short_name : long_name)

        routeMap[route_id] = {'name': name}
    }
    return routeMap
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
    let stopTimeMap = {};
    rows = await getFile("stops.txt");

    for (let r of rows){
        // util.log("JSON.stringify(r): " + JSON.stringify(r));

        let trip_id = r['trip_id'];
        let arrival_time = r['arrival_time'];
        if (util.isNullOrUndefined(arrival_time)) continue;
        let stop_id = r['stop_id'];
        let stop_sequence = r['stop_sequence'];

        let slist = stopTimeMap[trip_id];

        if (slist === null){
            slist = [];
            stopTimeMap[trip_id] = slist;
        }

        let entry = {'arrival_time': util.hhmmssToSeconds(arrival_time), 'stop_id': stop_id, 'stop_sequence': stop_sequence}
        sdt = r.get('shape_dist_traveled')

        if (sdt !== null && sdt.length > 0){
            entry['traveled'] = sdt;
        }
        print(`- entry: ${entry}`)
        slist.push(entry)
    }
}
// def __init__(self, path, url, subdivisions, dow = -1, epoch_seconds = -1):
//     if path[-1] != '/':
//         path += '/'

//     self.trip_candidates = {}
//     self.last_candidate_flush = time.time()




//     if dow < 0:
//         dow = datetime.datetime.today().weekday()
//     util.debug(f'- dow: {dow}')

//     if epoch_seconds < 0:
//         epoch_seconds = util.get_epoch_seconds()
//     util.debug(f'+ epoch_seconds: {datetime.datetime.fromtimestamp(epoch_seconds)}')

//     self.stops = self.get_stops()
//     #util.debug(f'-- stops: {stops}')

//     self.preload_stop_times()
//     self.preload_shapes()

//     self.compute_shape_lengths()
//     self.block_map = {}

//     trip_set = set()

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