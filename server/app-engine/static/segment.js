if(typeof util === 'undefined'){
    var util = require('../static/gtfs-rt-util');
    var geo_util = require('../static/geo_util');
}

const MAX_LOCATION_DISTANCE = 30000 //feet
const MAX_TIME_DISTANCE = 900       //seconds


// ### TODO add cache for way points with predicted arrival times:
//     - key: hash of min_file_offset, max_file_offset, start_time and end_time
//     - value: grid index, list of way points with predicted arrival times
//     - add class method set_current_grid_index(), which will flush all
//       cache entries with non-matching grid index
//     - in get_score(), either get way points from cache or create and
//       put into cache

var id_base = 0;
(function(exports) {
    exports.Segment = class {
        constructor(segment_index, trip_id, trip_name, trip_start_seconds, stop_id, boundingBox, start_time, end_time, min_file_offset, max_file_offset, shapes) {
            //util.log(`new segment! id_base: ${id_base}`);
            this.id = id_base;
            id_base += 1;

            util.log(`segment: id=${this.id} trip_id=${trip_id} top_left=${boundingBox.topLeft} bottom_right=${boundingBox.bottomRight} start_time=${util.secondsToHhmmss(start_time)} end_time=${util.secondsToHhmmss(end_time)}`);

            this.segment_index = segment_index;
            //util.log(`segment_index: ${segment_index}`);
            this.trip_id = trip_id;
            this.trip_name = trip_name;
            this.trip_start_seconds = trip_start_seconds;
            this.stop_id = stop_id;
            // util.log(`- trip_start_seconds: ${trip_start_seconds}`);
            this.boundingBox = boundingBox;
            this.start_time = start_time;
            this.end_time = end_time;
            this.min_file_offset = min_file_offset;
            this.max_file_offset = max_file_offset;
            this.waypointList = null;
            // util.log(`segment: id=${this.id} trip_id=${trip_id} top_left=${JSON.stringify(boundingBox.topLeft)} bottom_right=${JSON.stringify(boundingBox.bottomRight)} start_time=${util.secondsToHhmmss(start_time)} end_time=${util.secondsToHhmmss(end_time)}`);

            this.waypointList = [];
            //util.log(`this.min_file_offset: ${this.min_file_offset}`)
            //util.log(`this.max_file_offset: ${this.max_file_offset}`)
            for(let r = this.min_file_offset; r < this.max_file_offset+1; r++){
                let llat = shapes[r]['shape_pt_lat'];
                let llon = shapes[r]['shape_pt_lon'];
                this.waypointList.push({'lat': llat, 'lon': llon});
            }
            let delta_time = this.end_time - this.start_time

            for (let i=0; i< this.waypointList.length; i++){
                let fraction = i / this.waypointList.length;
                let time = this.start_time + fraction * delta_time;
                this.waypointList[i]['time'] = time;
            }
            // util.log("this.waypointList: " + JSON.stringify(this.waypointList));
        }

        setSegmentsPerTrip(count){
            this.segments_per_trip = count;
        }

        getTripFraction(){
            return this.segment_index / this.segments_per_trip;
        }

        async getScore(lat, lon, seconds, shapes){
            if (!(this.boundingBox.contains(lat, lon))){
                // util.log("Bounding box does not contain lat & lon");
                return {'score': -1, 'time_offset': 0};
            }

            // util.log(`seconds: ${seconds}`);
            // util.log(`trip_start_seconds: ${this.trip_start_seconds}`);
            // util.log(`start_time: ${this.start_time}`);
            // util.log(`end_time: ${this.end_time}`);
            if (seconds < this.trip_start_seconds || seconds < this.start_time - MAX_TIME_DISTANCE || seconds > this.end_time + MAX_TIME_DISTANCE){
                return { 'score': -1,'time_offset': 0};;
            }

            let min_distance = 1000000000;
            let min_index = -1;
            let closestLat = 0;
            let closestLon = 0;

            for (let i=0; i<this.waypointList.length-1; i++){
                let sp1 = this.waypointList[i];
                let sp2 = this.waypointList[i + 1];

                let distance = await geo_util.getMinDistance(sp1, sp2, lat, lon, seconds);
                // util.log(`distance: ${distance}`);
                if (distance < min_distance){
                    min_distance = distance;
                    min_index = i;
                    closestLat = this.waypointList[i]['lat'];
                    closestLon = this.waypointList[i]['lon'];
                }
            }
            // util.log(`- min_distance: ${min_distance}`);

            if (min_distance > MAX_LOCATION_DISTANCE){
                return {'score': -1, 'time_offset': 0};
            }

            // util.log(`+ update time : ${util.secondsToHhmm(seconds)}`);
            // util.log(`+ segment time: ${util.secondsToHhmm(this.waypointList[min_index]["time"])}`);
            let time_distance = Math.abs(seconds - this.waypointList[min_index]['time']);
            //util.log(`- time_distance: ${time_distance}`);
            if (time_distance > MAX_TIME_DISTANCE){
                return {'score': -1, 'time_offset': 0};
            }

            let locationScore = .5 * (MAX_LOCATION_DISTANCE - min_distance) / MAX_LOCATION_DISTANCE;
            let timeScore = .5 * (MAX_TIME_DISTANCE - time_distance) / MAX_TIME_DISTANCE;

            util.log(`segment update: id=${this.id} trip-name=${this.trip_name} score=${locationScore + timeScore} trip_pos=(${this.segment_index}/${this.segments_per_trip}) closest-lat=${closestLat} closest-lon=${closestLon}`);
            return {
                'score': locationScore + timeScore,
                'time_offset': seconds - this.waypointList[min_index]['time']
            };
        }
        getTripId(){
            return this.trip_id;
        }
    }
}(typeof exports === 'undefined' ? this.segment = {} : exports));
