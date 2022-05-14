const fileMap = new Map();
const files = ["trips", "routes", "stops"];

const agencyID = 'santa-ynez-valley-transit';
const baseURL = `https://storage.googleapis.com/graas-resources/gtfs-archive/${agencyID}/`;

for (let i = 0; i < files.length; i++) {
  let fileName = files[i];
  let url = baseURL + fileName + ".txt";
  util.log('- fetching from ' + url);
  util.timedFetch(url, {
      method: 'GET'
  })
  .then(function(response) {
      response.text().then(function(text){
        fileMap.set(fileName,csvToArray(text));
      })
  })
  .catch((error) => {
      util.log('*** fetch() error: ' + error);
  });
}

util.log(fileMap.get("trips"));

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
  return arr;
}