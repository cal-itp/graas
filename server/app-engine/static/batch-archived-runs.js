// run with NODE_PATH=../../node/node_modules node batch-archived-runs.js -d ~/src/graas/data/trip-inference-training/included -o ~/tmp

var util = require('../static/gtfs-rt-util');
var run_archived_trip = require('../static/run-archived-trip');
var fs = require( 'fs' );
var path = require( 'path' );
var Tee = require('../static/tee');
var { Console } = require('node:console');

async function main(dataDir, outputDir, gtfsCacheDir, staticGtfsUrl, simulateBlockAssignment){
    let timestamp = new Date().toISOString();
    let resultFile = `${outputDir}/results-${timestamp}.txt`;
    let statsFile = `${outputDir}/ti-scores-${timestamp}.txt`;
    let vehiclePositionFiles = [];
    let metadataFiles = [];
    const tee = new Tee();
    let dirs = listDirs(dataDir);
    for (let dir of dirs) {
        let tripDir = `${dataDir}/${dir}`
        let files = listDirs(tripDir);
        for (let file of files) {
            util.log(" - file: " + file);
            if(file === "updates.txt")
            {
                vehiclePositionFiles.push(`${dataDir}/${dir}/${file}`);
            }
            if(file === "metadata.txt")
            {
                metadataFiles.push(`${dataDir}/${dir}/${file}`);
            }
        }
    }
    vehiclePositionFiles = vehiclePositionFiles.sort();
    util.log(`- vehiclePositionFiles: ${JSON.stringify(vehiclePositionFiles)}`);
    util.log(`- statsFile: ${JSON.stringify(statsFile)}`);

    // files = glob.glob(f'{outputDir}/202*-log.txt')
    // for f in files:
    //     #print(f'-- f: {f}')
    //     os.remove(f)

    // stdout_save = sys.stdout
    let logFile = `${outputDir}/log.txt`;
    util.log(`- logFile: ${logFile}`);

    // sys.stdout = open(log_file, 'w')
    let then = Date.now();
    await run_archived_trip.main(vehiclePositionFiles, gtfsCacheDir, outputDir, staticGtfsUrl, simulateBlockAssignment);
    // sys.stdout.close()
    // sys.stdout = stdout_save
    tee.stream.write(`+ elapsed time: ${Date.now() - then} milliseconds\n`);
    tee.stream.write(`- resultFile: ${resultFile}\n`);

    for (let file of metadataFiles) {
        let agencyDate = getAgencyDateFromPath(file);
        let logFileName = `${outputDir}/${agencyDate}-log.txt`
        let expectedTripID = null;
        try{
            let contents = fs.readFileSync(file, 'utf-8').trim();
            let index = contents.search(": ");
            expectedTripID = contents.substring(index + 2);
            tee.stream.write(`expected: ${expectedTripID}\n`);
            let tripIDs = {};
            util.log(`logFileName: ${logFileName}`);
            let logs = fileToArray(logFileName);

            if(logs === null) continue;
            for(let l of logs){
                 let s = l.trim()
                 if(s.length === 0){
                    continue;
                 }
                if(s.search('- tripID:') === 0){
                    let tripID = s.substring(9);
                    if(tripID in tripIDs){
                        tripIDs[tripID] = tripIDs[tripID] + 1
                    } else {
                        tripIDs[tripID] = 1;
                    }
                }
            }
            for (let [key, value] of Object.entries(tripIDs)){
                tee.stream.write(`${value} - ${key}\n`);
            }
        } catch(e){
            util.log("skip, files incomplete for now");
            console.log(e);
        }

        // tee.redirect();
    }
}

function listDirs(path){
    console.log(`listDirs(${path})`);
    try {
      if (fs.existsSync(path)) {
        util.log("Directory exists.");
      } else {
        util.log("Directory does not exist.");
      }
    } catch(e) {
      util.log("An error occurred.");
    }

    try {
        return fs.readdirSync(path);
    } catch (e) {
        util.log('Error occured while reading directory!');
    }
}

function getAgencyDateFromPath(path){
    let ir = path.lastIndexOf('/');
    if (ir < 0){
        return null;
    }
    let il = path.substring(0,ir).lastIndexOf('/');

    if (il < 0){
        return null;
    }
    return path.substring(il + 1,ir);
}

function fileToArray(filename) {
    try{
        const contents = fs.readFileSync(filename, 'utf-8');
        const arr = contents.split(/\r?\n/);
        return arr;
    } catch(e){
        util.log("skipping error, file missing for now");
        // console.log(e);
        return null;
    }
}

let dataDir = null;
let outputDir = null;
let simulateBlockAssignment = false;
let gtfsCacheDir = null;
let staticGtfsUrl = null;

const args = process.argv.slice(2);

for(let j = 0; j < args.length; j++){

    if (args[j] == '-d' && j < args.length - 1){
        j++;
        dataDir = args[j];
        continue;
    }

    if (args[j] == '-o' && j < args.length - 1){
        j++;
        outputDir = args[j];
        continue;
    }

    if (args[j] == '-g' && j < args.length - 1){
        j++;
        gtfsCacheDir = args[j];
        continue;
    }

    if (args[j] == '-s' && j < args.length - 1){
        j++;
        staticGtfsUrl = args[j];
        continue;
    }

    if (args[j] == '-b'){
        simulateBlockAssignment = true;
        continue;
    }
}

if (dataDir === null || outputDir === null || gtfsCacheDir === null || staticGtfsUrl === null) {
    util.log(`* usage: ${sys.argv[0]} -d <data-dir> -o <output-dir> -g <gtfs-cache-dir> -s <static-gtfs-url> [-b]`);
    util.log(`  -b: simulate block assignment'`);
    util.log(`  <data-dir>: where to find training data, e.g. $GRASS_REPO/data/trip-inference-training/included`);
    util.log(`  <output-dir>: where to put output data, e.g. ~/tmp`);
    util.log(`  <gtfs-cache-dir>: where to cache GTFS data. e.g. ~/tmp/gtfs-cache`);
    util.log(`  <static-gtfs-url>: live GTFS URL or archived file, e.g. $GRASS_REPO/data/trip-inference-training/gtfs-archive/2022-02-14-tcrta-gtfs.zip`);

    process.exit(1);
}

main(dataDir, outputDir, gtfsCacheDir, staticGtfsUrl, simulateBlockAssignment);
