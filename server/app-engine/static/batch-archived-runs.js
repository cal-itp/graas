var util = require('../static/gtfs-rt-util');
var run_archived_trip = require('../static/run-archived-trip');
var fs = require( 'fs' );
var path = require( 'path' );
// import inference_stats


async function batch(dataDir, outputDir, simulateBlockAssignment){
    let timestamp = new Date().toISOString();
    util.log(timestamp);

    let resultFile = `${outputDir}/results-${timestamp}.txt`;
    let statsFile = `${outputDir}/ti-scores-${timestamp}.txt`;
    let dataFiles = [];

    try {
        let dirs = await fs.promises.readdir(dataDir);

        for (let dir of dirs) {
            util.log("dir: " + dir);
            try {
                let files = await fs.promises.readdir(`${dataDir}/${dir}`);
                for (let file of files) {
                    util.log(" - file: " + file);
                    if(file === "updates.txt")
                    {
                        dataFiles.push(`${dataDir}/${dir}/${file}`);
                    }
                }
            } catch (e) {
                console.error(e);
            }
        }
    } catch (e) {
        console.error(e);
    }
    dataFiles = dataFiles.sort();
    util.log(`- dataFiles: ${JSON.stringify(dataFiles)}`);
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
    await run_archived_trip.main(dataFiles, outputDir, simulateBlockAssignment);
    // sys.stdout.close()
    // sys.stdout = stdout_save
    util.log(`+ elapsed time: ${Date.now() - then} milliseconds`);
    util.log(` - resultFile: ${resultFile}`);

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

batch(dataDir, outputDir, simulateBlockAssignment);
