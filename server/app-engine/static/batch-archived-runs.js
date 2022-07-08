var util = require('../static/gtfs-rt-util');
var run_archived_trip = require('../static/run-archived-trip');
var fs = require( 'fs' );
var path = require( 'path' );
var Tee = require('../static/tee');
const { Console } = require('node:console');
// import inference_stats


async function main(dataDir, outputDir, simulateBlockAssignment){
    let timestamp = new Date().toISOString();
    let resultFile = `${outputDir}/results-${timestamp}.txt`;
    let statsFile = `${outputDir}/ti-scores-${timestamp}.txt`;
    let vehiclePositionFiles = [];
    let metadataFiles = [];
    const tee = new Tee();
    const console = new Console(tee, process.stderr);
    try {
        util.log(`dataDir: ${dataDir}`);
        let dirs = await fs.promises.readdir(dataDir);

        for (let dir of dirs) {
            try {
                let files = await fs.promises.readdir(`${dataDir}/${dir}`);
                for (let file of files) {
                    // util.log(" - file: " + file);
                    if(file === "updates.txt")
                    {
                        vehiclePositionFiles.push(`${dataDir}/${dir}/${file}`);
                    }
                    if(file === "metadata.txt")
                    {
                        metadataFiles.push(`${dataDir}/${dir}/${file}`);
                    }
                }
            } catch (e) {
                console.log("hello!");
                console.error(e);
            }
        }
    } catch (e) {
        console.error(e);
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
    tee.redirect(logFile);

    // sys.stdout = open(log_file, 'w')
    let then = Date.now();
    // await run_archived_trip.main(vehiclePositionFiles, outputDir, simulateBlockAssignment);
    // sys.stdout.close()
    // sys.stdout = stdout_save
    console.log(`+ elapsed time: ${Date.now() - then} milliseconds\n`);
    console.log(` - resultFile: ${resultFile}\n`);

    for (let file of metadataFiles) {
        let agencyDate = getAgencyDateFromPath(file);
        let logFileName = `${outputDir}/${agencyDate}-log.txt`
        let expectedTripID = null;
        try{
            let contents = fs.readFileSync(file, 'utf-8').trim();
            let index = contents.search(": ");
            expectedTripID = contents.substring(index + 2);
            console.log(`expected: ${expectedTripID}\n`);
            let tripIDs = {};
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
                console.log(`${value} - ${key}\n`);
            }
        } catch(e){
            console.log("skip, files incomplete for now");
            // console.log(e);
        }

        tee.redirect();
    }

    // with open(resultFile, 'w') as f:
    //     files = glob.glob(f'{dataDir}/trip-inference-training/included/202*')
    //     for i in files:
    //         #print(f'-- i: {i}')
    //         si = i.rfind('/')
    //         rel_i = i[si + 1:]
    //         #print(f'-- rel_i: {rel_i}')
    //         f.write(f'i: {rel_i}\n')
    //         log = f'{outputDir}/{rel_i}-log.txt'
    //         metadata = f'{i}/metadata.txt'
    //         expected_trip_id = None
    //         with open(metadata) as mf:
    //             s = mf.readline().strip()
    //             n = s.find(': ')
    //             expected_trip_id = s[n + 2:]
    //         f.write(f'expected: {expected_trip_id}\n')
    //         trip_ids = {}
    //         with open(log) as lf:
    //             for line in lf:
    //                 s = line.strip()
    //                 if len(s) == 0:
    //                     continue
    //                 if s.startswith('- trip_id:'):
    //                     if s in trip_ids:
    //                         trip_ids[s] = trip_ids[s] + 1
    //                     else:
    //                         trip_ids[s] = 1
    //         for k in trip_ids.keys():
    //             f.write(f'{trip_ids[k]} - {k}\n')

    // sys.stdout = open(statsFile, 'w')
    // inference_stats.main(resultFile)
    // sys.stdout.close()
    // sys.stdout = stdout_save

    // line = ''
    // with open(statsFile) as f:
    //     for line in f:
    //         line = line.strip()
    // score = line.split(' ')[1]
    // print(f'score: {score}')
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
        console.log(e);
        return null;
    }
}

let dataDir = null;
let outputDir = null;
let simulateBlockAssignment = false;

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

    if (args[j] == '-b'){
        simulateBlockAssignment = true;
        continue;
    }
}
if (dataDir === null || outputDir === null){
    util.log(`* usage: ${sys.argv[0]} -d <data-dir> -o <output-dir>'`);
    util.log(`  -b: simulate block assignment'`);
    util.log(`  <data-dir>: where to find training data, e.g. $GRASS_REPO/data/trip-inference-training/included'`);
    util.log(`  <output-dir>: where to put output data, e.g. ~/tmp'`);
    process.exit(1);
}

main(dataDir, outputDir, simulateBlockAssignment);
