const fs = require('fs');
const util = require('../app-engine/static/gtfs-rt-util');

class CSVReader {
    constructor(filename, omitEmptyFields) {
        util.log(`CSVReader.CSVReader()`);
        util.log(`- filename: ${filename}`);

        const contents = fs.readFileSync(filename, 'utf8');
        //util.log('- contents: ' + contents);
        const lines = contents.split('\n');

        const names = lines.shift().split(',');
        util.log('- names: ' + names);

        this.arr = [];
        this.index = 0;
        this.omitEmptyFields = omitEmptyFields;

        for (let l of lines) {
            if (l.length == 0) continue;

            this.arr.push(this.parseLine(names, l));
        }
    }

    parseLine(names, line) {
        //util.log(`CSVReader.parseLine()`);
        //util.log(`- names: ${names}`);
        //util.log(`- line: ${line}`);

        let result = {};
        let ci = 0;
        let s = '';
        let i = 0;

        while (i < line.length) {
            let c = line[i];

            if (c === ',') {
                if (s.length > 0 || !this.omitEmptyFields) {
                    result[names[ci]] = s;
                }

                ci++;
                s = '';
                i++;

                continue;
            }

            if (c === '"') {
                i++;

                while (i < line.length) {
                    c = line[i];

                    if (c === '"') {
                        if (i + 1 < line.length && line[i + 1] === '"') {
                            s += '"';
                            i += 2;
                            continue;
                        } else {
                            i++;
                            break;
                        }
                    }

                    s += c;
                    i++;
                }

                continue;
            }

            s += c;
            i++;
        }

        result[names[ci]] = s;
        //util.log(`- result: ${JSON.stringify(result)}`);

        const count = Object.keys(result).length;
        //util.log(`- count: ${count}`);

        if (!this.omitEmptyFields &&  count != names.length) {
            throw `incorrect number of fields in CSV row: ${line}`;
        }

        return result;
    }

    getNextLine() {
        //util.log(`CSVReader.getNextLine()`);

        if (this.index >= this.arr.length) {
            return null;
        }

        return this.arr[this.index++];
    }
}

module.exports = CSVReader;