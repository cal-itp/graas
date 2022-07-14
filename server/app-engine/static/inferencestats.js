const fs = require('fs');
const util = require('../static/gtfs-rt-util');

(function(exports) {
    const EXPECTED_KEY = 'expected: ';
    const TRIP_ID_KEY = '- trip_id: ';
    const NAME_KEY = 'i: ';

    exports.main = async function(filename){
        util.log(`inferencestats.main()`);
        util.log(`- filename: ${filename}`);

        const content = fs.readFileSync(url, 'utf8');
        let lines = content.split('\n');
        lines.push('i: sentinel');

        let index = 0;
        let correct = 0;
        let incorrect = 0;
        let expected_trip_id = null;
        let name = null;
        let sum = 0;
        let items = 0;

        while (index < len(lines)) {
            let line = lines[index].trim();
            index++;

            if (line.startsWith(NAME_KEY)) {
                /*total = correct + incorrect

                if total > 0:
                    correct_pct = int(correct * 100 / total)
                    print(f'{name}: {correct_pct}%')
                    sum += correct_pct
                    items += 1

                correct = 0
                incorrect = 0
                name = line[len(NAME_KEY):]*/
            }

            if (line.startsWith(EXPECTED_KEY)) {
                /*expected_trip_id = line[len(EXPECTED_KEY):]
                #print(f'- expected_trip_id: \'{expected_trip_id}\'')*/
            }

            let i = line.indexOf(TRIP_ID_KEY);
            if (i > 0) {
                /*tok = line.split(' ')
                trip_id = tok[-1]
                #print(f'- trip_id: \'{trip_id}\'')
                count = int(tok[0])
                #print(f'- count: {count}')

                if trip_id == expected_trip_id:
                    correct += count
                else:
                    incorrect += count*/
            }
        }

        console.log('-----------------------------');
        console.log(`                     avg: ${Math.floor(sum / items)}%`)
    }
}(typeof exports === 'undefined' ? this.inference_stats = {} : exports));
