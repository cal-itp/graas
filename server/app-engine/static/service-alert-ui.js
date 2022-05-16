const PEM_HEADER = "-----BEGIN TOKEN-----";
const PEM_FOOTER = "-----END TOKEN-----";
var signatureKey = null;
var currentModal = null;
const fileMap = new Map();
const files = ["trips", "routes", "stops"];
const causes = ["Unknown cause",
                "Other cause",
                "Technical problem",
                "Strike",
                "Demonstration",
                "Accident",
                "Holiday",
                "Weather",
                "Maintenance",
                "Construction",
                "Police activity",
                "Medical emergency"];
const effects = ["No service",
                "Reduced service",
                "Significant delays",
                "Detour",
                "Additional service",
                "Modified service",
                "Stop moved",
                "Other effect",
                "Unknown effect"];

var agencyID = 'pr-test';
const baseURL = `https://storage.googleapis.com/graas-resources/gtfs-archive/${agencyID}/`;

initialize();

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

function initialize(){
  let str = localStorage.getItem("app-data") || "";

  if (!str) {
    util.handleModal("keyEntryModal");
  }
  else {
    completeInitialization(util.parseAgencyData(str));
  }
}

async function completeInitialization(agencyData) {
    agencyID = agencyData.id;
    util.log("- agencyID: " + agencyID);

    let pem = agencyData.pem;

    let i1 = pem.indexOf(PEM_HEADER);
    util.log("- i1: " + i1);

    let i2 = pem.indexOf(PEM_FOOTER);
    util.log("- i2: " + i2);

    let b64 = pem.substring(i1 + PEM_HEADER.length, i2);
    // util.log("- b64: " + b64);

    keyType = "ECDSA";
    keyLength = 256;

    let key = atob(b64);
    // util.log("- key.length: " + key.length);

    const binaryDer = util.str2ab(key);

    signatureKey = await crypto.subtle.importKey(
        "pkcs8",
        binaryDer,
        {
            name: "ECDSA",
            namedCurve: "P-256"
        },
        false,
        ["sign"]
    );

    util.log("- signatureKey.type: " + signatureKey.type);
}

function handleKey(id) {
  util.log('handleKey()');
  util.log('- id: ' + id);

  let dateStr;

  let p = document.getElementById('keyTextArea');
  let value = p.value.replace(/\n/g, "");
  util.log("- value: " + value);

  let i1 = value.indexOf(PEM_HEADER);
  util.log("- i1: " + i1);

  let i2 = value.indexOf(PEM_FOOTER);
  util.log("- i2: " + i2);

  if (i1 === 0) {
      alert('missing agency id');
      return;
  } else if (i1 < 0 || i2 < 0) {
      alert('not a valid key');
      return;
  }

  util.dismissModal();
  p.value = "";

  localStorage.setItem('app-data', value);
  completeInitialization(util.parseAgencyData(value));
}

// todo: rename
function start(){
  util.log(fileMap);
  util.populateList("entity-select", "Select an alert entity", files);
  util.populateList("cause-select", "Select an alert cause", causes);
  util.populateList("effect-select", "Select an alert effect", effects);
}

function handleEntityChoice(){
  let entitySelect = document.getElementById("entity-select");
  let entity = entitySelect.value;
  let entitySingular = entity.slice(0,-1);
  let id = entitySingular + "_id";
  util.log(fileMap.get(entity));
  var idArray = fileMap.get(entity).map(function (el) { return el[id]; });
  util.clearSelectOptions(document.getElementById("item-select"));
  util.populateList("item-select", "Select a " + entitySingular, idArray);
}

function postServiceAlert() {
    util.log('handleGPSUpdate()');
    let timestamp = Math.floor(Date.now() / 1000);
    let cause = document.getElementById("cause-select").value;
    let effect = document.getElementById("effect-select").value;
    let description = document.getElementById("description").value;
    let header = document.getElementById("header").value;
    // TODO: add URL
    let url = document.getElementById("url").value;

    let data = {
        agency_key: agencyID,
        cause: cause,
        description: description,
        effect: effect,
        header: header,
        time_stamp: timestamp,
        // time_start: time_start,
        // time_stop: time_stop
    };

    util.log("data.cause: " + data.cause);
    util.log("data.effect: " + data.effect);
    util.log("data.header: " + data.header);
    util.log("data.time_stamp: " + data.time_stamp);
    util.signAndPost(data, signatureKey, '/post-alert', document);
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