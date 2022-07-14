var util = require('../static/gtfs-rt-util');
var Tee = require('../static/tee');
var inference = require('../static/inference');
var {readFileSync, promises: fsPromises} = require('fs');
const { Console } = require('node:console');

function getAgencyIdFromPath(path){
    let ir = path.lastIndexOf('-');
    if (ir < 0){
        return null;
    }
    let il = path.substring(0,ir).lastIndexOf('-');

    if (il < 0){
        return null;
    }
    return path.substring(il + 1,ir);
}

(function(exports) {
    exports.main = async function(dataFiles, gtfsCacheDir, outputFolder, staticGtfsUrl, simulateBlockAssignment){
        util.log(`main()`);
        util.log(`- dataFiles: ${dataFiles}`);
        util.log(`- gtfsCacheDir: ${gtfsCacheDir}`);
        util.log(`- outputFolder: ${outputFolder}`);
        util.log(`- staticGtfsUrl: ${staticGtfsUrl}`);

        // util.log(`- inference.TripInference.VERSION: ${inference.TripInference.VERSION}`);

        const tee = new Tee();
        // const console = new Console(tee, process.stderr);
        // let stdout_save = sys.stdout;
        // let sys.stdout = tee;
        let lastDow = -1;
        var inf = null

        for (let df of dataFiles){
            let pattern1 = new RegExp(".*/([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]-.*)/.*","g"); // yyyy-mm-dd-hh-mm-<agency>
            let pattern2 =  new RegExp(".*([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]).*","g");     // yyyy-mm-dd-hh-mm
            util.log(`df: ${df}`);
            let expected_trip_id = null;
            let block_id = null;

            if (simulateBlockAssignment){
                util.log(`- df: ${df}`);
                let i = df.lastIndexOf('/');

                if (i > 0){
                    let metaf = df.slice(0,i + 1) + 'metadata.txt';
                    util.log(`- metaf: ${metaf}`);
                    let expected_trip_id = getProperty(metaf, 'trip-id');
                    tee.stream.write(`- expected_trip_id: >${expected_trip_id}<\n`);
                }
            }
            let m1 = pattern1.exec(df);
            // util.log(`m1: ${m1}`);
            let name = m1[1];
            // util.log(`name: ${name}`);

            let m2 = pattern2.exec(df);
            let date = m2[1];
            //util.log(`date: ${date}`);
            let dow = getDow(date);
            // util.log(`dow: ${dow}`);
            let epochSeconds = util.getEpochSeconds(date);
            //util.log(`epochSeconds: ${epochSeconds}`);

            if (dow != lastDow){
                // tee.redirect()
                let agency_id = getAgencyIdFromPath(df);
                tee.stream.write(`++ inferred agency ID: ${agency_id} \n`);

                inf = new inference.TripInference(
                    gtfsCacheDir,
                    staticGtfsUrl,
                    agency_id,
                    'test-vehicle-id',
                    15,
                    dow,
                    epochSeconds
                );
                await inf.init();
            }

            lastDow = dow;
            inf.resetScoring();

            let fn = outputFolder + '/' + name + '-log.txt';
            tee.stream.write(`-- fn: ${fn} \n`);
            tee.redirect(fn)

            let lines = fileToArray(df);

            for (let line of lines){
                line = line.trim();
                // util.log(`- line: ${line}`);
                if(line === '') continue;
                let tok = line.split(',');
                let seconds = parseInt(tok[0]);
                let daySeconds = util.getSecondsSinceMidnight(util.secondsToDate(seconds));
                let lat = parseFloat(tok[1]);
                let lon = parseFloat(tok[2]);
                let gridIndex = inf.grid.getIndex(lat, lon);
                // util.log(`current location: lat=${lat} long=${lon} seconds=${daySeconds} grid_index=${gridIndex}`);
                tee.stream.write(`current location: lat=${lat} long=${lon} seconds=${daySeconds} grid_index=${gridIndex} \n`);
                let result = await inf.getTripId(lat, lon, daySeconds, expected_trip_id);
                util.log(`- result: ${JSON.stringify(result)}`);

                tripID = null;

                if (result !== null){
                    tripID = result['trip_id'];
                }
                tee.stream.write(`- tripID: ${tripID} \n`);
                // util.log(`- tripID: ${tripID}`);
            }
        }
        tee.redirect();
        // sys.stdout = stdout_save
    }
}(typeof exports === 'undefined' ? this.run_archived_trip = {} : exports));

// # assumes that filename contains a string of format yyyy-mm-dd
// # returns day of week: 0-6 for Monday through Sunday if date string present, -1 otherwise
function getDow(yyyymmdd){
    if (yyyymmdd){
        let yyyy = yyyymmdd.substring(0,4);
        let mm = parseInt(yyyymmdd.substring(5,7)) - 1;
        let dd = yyyymmdd.substring(8,10);
        let d = new Date(yyyy, mm, dd);
        return d.getDay() - 1;
    }
    else return -1;
}

function getProperty(filename, name){
    let key = name + ': ';
    let lines = fileToArray(filename);

    for (let line of lines){
        line = line.trim();
        let i = line.indexOf(key);
        if (i === 0){
            return line.substring(key.length);
        }
    }

    return null;
}

function fileToArray(filename) {
    const contents = readFileSync(filename, 'utf-8');
    const arr = contents.split(/\r?\n/);
    return arr;
}

function usage(){
    util.log(`usage: run-archived-trip.js -o|--output-folder <output-folder> data-file [<data-files>]`);
    process.exit(1);
}



// uncomment to run from command line:

// let dataFiles = [];
// let outputFolder = '~/tmp';
// let simulateBlockAssignment = false;

// const args = process.argv.slice(2);

// for(let j = 0; j < args.length; j++){

//     if (args[j] == '-b' || args[j] == '--simulate-block-assignment'){
//         simulateBlockAssignment = true;
//         continue;
//     }

//     if ((args[j] == '-o' || args[j] == '--output-folder') && j < args.length -1){
//         outputFolder = args[j + 1];
//         j++;
//         continue;
//     }

//      // assume arg is a data file
//     dataFiles.push(args[j])
// }

// if (dataFiles.length === 0){
//     usage();
// }

// this.main(dataFiles, outputFolder, simulateBlockAssignment)