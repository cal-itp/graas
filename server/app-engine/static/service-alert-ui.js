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
        if(fileMap.size === files.length) start();
      })
  })
  .catch((error) => {
      util.log('*** fetch() error: ' + error);
  });
}

function start(){
  util.log(fileMap);
  util.populateList("category-select", "Select an alert category", files);
}

function handleCategoryChoice(){
  let categorySelect = document.getElementById("category-select");
  let category = categorySelect.value;
  let categorySingular = category.slice(0,-1);
  let id = categorySingular + "_id";
  util.log(fileMap.get(category));
  var idArray = fileMap.get(category).map(function (el) { return el[id]; });
  util.clearSelectOptions(document.getElementById("item-select"));
  util.populateList("item-select", "Select a " + categorySingular, idArray);
}

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