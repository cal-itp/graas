const fs = require('fs');
const util = require('../static/gtfs-rt-util');

(function(exports) {
    const EXPECTED_KEY = 'expected: ';
    const TRIP_ID_KEY = '- trip_id: ';
    const NAME_KEY = 'i: ';

    exports.main = async function(filename){
        //util.log('inferencestats.main()');
        //util.log('- filename: ' + filename);

        const content = fs.readFileSync(filename, 'utf8');
        //util.log('- content: ' + content);
        let lines = content.split('\n');
        lines.push('i: sentinel');
        //util.log('- lines: ' + JSON.stringify(lines, null, 4));

        let index = 0;
        let correct = 0;
        let incorrect = 0;
        let expected_trip_id = null;
        let name = null;
        let sum = 0;
        let items = 0;

        while (index < lines.length) {
            let line = lines[index].trim();
            //util.log('-- line: ' + line);
            index++;

            if (line.startsWith(NAME_KEY)) {
                total = correct + incorrect;

                if (total > 0) {
                    correct_pct = Math.floor(correct * 100 / total);
                    console.log(`${name}: ${correct_pct}%`);
                    sum += correct_pct;
                    items++;
                }

                correct = 0;
                incorrect = 0;
                name = line.substring(NAME_KEY.length);
                //util.log('-- name: ' + name);
            }

            if (line.startsWith(EXPECTED_KEY)) {
                expected_trip_id = line.substring(EXPECTED_KEY.length);
                //util.log('- expected_trip_id: ' + expected_trip_id);
            }

            let i = line.indexOf(TRIP_ID_KEY);

            if (i > 0) {
                const tok = line.split(' ');
                const trip_id = tok[tok.length - 1];
                //util.log('- trip_id: ' + trip_id);
                const count = parseInt(tok[0]);
                //util.log('- count: ' + count);

                if (trip_id === expected_trip_id) {
                    correct += count;
                } else {
                    incorrect += count;
                }
            }
        }

        console.log('-----------------------------');
        console.log(`                     avg: ${Math.floor(sum / items)}%`)
    }
}(typeof exports === 'undefined' ? this.inference_stats = {} : exports));
