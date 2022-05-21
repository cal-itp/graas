
const serverURL = "https://127.0.0.1:8080/";
const PEM_HEADER = "-----BEGIN TOKEN-----";
const PEM_FOOTER = "-----END TOKEN-----";
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');
ctx.canvas.width = window.innerWidth;
ctx.canvas.height = window.innerHeight;
var alerts = [];
var signatureKey = null;
var currentModal = null;
var agencyID = null;
// Var not const since it needs to be set when the feed view modal is visible
var feedViewHeaderHeight;
const fileMap = new Map();
const files = ["trips", "routes", "stops", "agency"];
const dropdownsIDs = [
  "route-select",
  "trip-select",
  "stop-select",
  "route-type-select",
  "agency-select"
]
const textFieldIDs = [
  "header",
  "description",
  "url",
  "start-time",
  "stop-time"
]
const causes = new Map([
  [1,'Unknown Cause'],
  [2,'Other Cause'],
  [3,'Technical Problem'],
  [4,'Strike'],
  [5,'Demonstration'],
  [6,'Accident'],
  [7,'Holiday'],
  [8,'Weather'],
  [9,'Maintenance'],
  [10,'Construction'],
  [11,'Police Activity'],
  [12,'Medical Emergency']
]);
const effects = new Map([
  [1,'Unknown Effect'],
  [2,'No Service'],
  [3,'Reduced Service'],
  [4,'Significant Delays'],
  [5,'Detour'],
  [6,'Additional Service'],
  [7,'Modified Service'],
  [8,'Other Effect'],
  [9,'Stop Moved']
]);

// May end up removing this array:
const optionalFields = [
  "url",
  "agency_id",
  "trip_id",
  "route_id",
  "route_type",
  "stop_id"
]
const FONT_SIZE = 12;
const BOX_HEIGHT = FONT_SIZE * 6;
const VERT_GAP = 10;
const BOX_WIDTH = 150;
ctx.textBaseline = "top";


initialize();

function loadFiles(){
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
          if(fileName === "routes"){
            // Manually create route-types category from routes
            let routeTypes = getItems("routes","route_type");
            fileMap.set("route_types", [...new Set(routeTypes)]);
          }
          if(fileMap.size === files.length + 1) populateDropdowns();
        })
    })
    .catch((error) => {
        util.log('*** fetch() error: ' + error);
    });
  }
  loadAlerts();
}

function initialize(){
  util.log("initialize()");
  let str = localStorage.getItem("app-data") || "";

  if (!str) {
    util.showElement("keyEntryModal");
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
    loadFiles();
    util.handleModal("menuModal");
}

// Thanks to https://gavinr.com/protocol-buffers-protobuf-browser/
async function getPB(url){
  let response = await fetch(url);
  if (response.ok) {
    const bufferRes = await response.arrayBuffer();
    const pbf = new Pbf(new Uint8Array(bufferRes));
    const obj = await FeedMessage.read(pbf);
    return obj.entity;
  } else {
    console.error("error:", response.status);
  }
}

async function loadAlerts(){
  let rtFeed = serverURL + "service-alerts.pb?agency=" + agencyID;
  feed = await getPB(rtFeed);
  util.log("JSON.stringify(feed): " + JSON.stringify(feed));
    // Add all the locations to the map:
  alerts = await feed.map(feedObject => {
    let alert = feedObject.alert;

    return new Object({
      id: feedObject.id,
      time_start: alert.active_period[0].start,
      time_stop: alert.active_period[0].end,
      agency_id: alert.informed_entity[0].agency_id,
      trip_id: alert.informed_entity[0].trip_id,
      stop_id: alert.informed_entity[0].stop_id,
      route_id: alert.informed_entity[0].route_id,
      route_type: alert.informed_entity[0].route_type,
      cause: causes.get(alert.cause),
      effect: effects.get(alert.effect),
      header: alert.header_text.translation[0].text,
      description: alert.description_text.translation[0].text,
      url: (alert.url !== null ? alert.url.translation[0].text : null)
    });
  });
}

function menu(){
  util.log("menu()");
  util.dismissModal();
  util.handleModal("menuModal");
}

function createAlert(){
  util.log("createAlert()");
  util.dismissModal();
  util.handleModal("alertCreateModal");
}

function viewAlerts(){
  util.log("viewAlerts()");
  util.dismissModal();
  util.handleModal("viewFeedModal");
  feedView();
}

function deleteAlert(){
  util.log("deleteAlert()");
  viewAlerts();
}

function alertDetailView(){
  util.log("alertDetailView()");
  util.dismissModal();
  util.handleModal("alertDetailModal");
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

function populateDropdowns(){
  util.log(fileMap);
  if(fileMap.get("route_types").length === 1){
    util.hideElement("route-type");
  } else {
    util.populateList("route-type-select", "Select a route type", fileMap.get("route_types"));
  }
  if(fileMap.get("agency").length === 1){
    util.hideElement("agency");
  } else {
    util.populateList("agency-select", "Select an agency", getItems("agency", "agency_id"));
  }
  util.populateList("cause-select", "Select an alert cause", Array.from(causes.values()));
  util.populateList("effect-select", "Select an alert effect", Array.from(effects.values()));
  util.populateList("route-select", "Select a route", getItems("routes", "route_id"));
  util.populateList("trip-select", "Select a trip", getItems("trips", "trip_id"));
  util.populateList("stop-select", "Select a stop", getItems("stops", "stop_id"));
}

function getItems(type, columnName){
  return fileMap.get(type).map(function (el) { return el[columnName]; })
}

function handleEntitySelection(checkbox, entityID){
  let dropdownName = entityID.slice(0,-8) + "select";
  if(checkbox.checked){
    util.showElement(dropdownName);
  } else {
    util.hideElement(dropdownName);
    util.resetDropdownSelection(dropdownName);
  }
}

function getValue(id){
  let value = document.getElementById(id).value;
  if(value === "disabled" || value === '') return null;
  else return value;
}

function postServiceAlert() {
    util.log('handleGPSUpdate()');
    let timestamp = Math.floor(Date.now() / 1000);
    let cause = getValue("cause-select");
    let effect = getValue("effect-select");
    let description = getValue("description");
    let header = getValue("header");
    let startTime = getValue("start-time");
    let stopTime = getValue("stop-time");
    let startTimeSecs = new Date(startTime).getTime() / 1000;
    let stopTimeSecs = new Date(stopTime).getTime() / 1000;
    let agency_id = getValue("agency-select");
    let route_id = getValue("route-select");
    let trip_id = getValue("trip-select");
    let route_type = getValue("route-type-select");
    let stop_id = getValue("stop-select");
    let url = getValue("url");

    if(agency_id === null && route_id === null && stop_id === null && trip_id === null && route_type === null){
      alert("Please select at least one entity for your alert");
      return;
    }

    if(cause === null || effect === null){
      alert("Please select both a cause and an effect for your alert");
      return;
    }

    if(cause === null || effect === null){
      alert("Please assign both a cause and an effect");
      return;
    }

    if(header === null || description === null){
      alert("Please write both a header and a description");
      return;
    }

    let data = {
      agency_key: agencyID,
      timestamp: timestamp,
      time_start: startTimeSecs,
      time_stop: stopTimeSecs,
      agency_id: agency_id,
      trip_id: trip_id,
      stop_id: stop_id,
      route_id: route_id,
      route_type: route_type,
      cause: cause,
      effect: effect,
      header: header,
      description: description
    };

    if(url !== null){
      urlData = {url: url}
      Object.assign(data, urlData)
    }

    util.signAndPost(data, signatureKey, '/post-alert', document);

    // Consider actually confirming send status
    alert("Alert posted successfully");
    util.handleModal("menuModal");
    loadAlerts();
    resetFields();
}

function resetFields(){
  for (let dropdown of dropdownsIDs){
    util.resetDropdownSelection(dropdown);
  }
  for (let textfield of textFieldIDs){
    util.resetFieldValue(textfield);
  }
}

function createLayout(){
  var yy = VERT_GAP;
  for (alert of alerts){
    alert.y = yy;
    alert.x = VERT_GAP;
    yy += (BOX_HEIGHT + VERT_GAP);
  }
}

async function feedView(){
  feedViewHeaderHeight = document.getElementById('feed-view-header').offsetHeight;
  if(alerts.length > 0){
    if(util.isNullOrUndefined(alerts[0].y)){
      createLayout();
    }
    drawAlerts();
  } else{
    // There are no alerts - communicate this somehow
  }
}

function drawAlerts(){
  for (alert of alerts){
    ctx.beginPath();
    ctx.strokeStyle = "black";
    ctx.fillStyle = "white";
    ctx.fillRect(alert.x, alert.y, BOX_WIDTH, BOX_HEIGHT);
    ctx.rect(alert.x, alert.y, BOX_WIDTH, BOX_HEIGHT);
    ctx.stroke();

    ctx.fillStyle = "black";
    ctx.font = `${FONT_SIZE}px ARIAL`;

    ctx.fillText(`Header: ${alert.header}`, alert.x, alert.y + FONT_SIZE * 0)
    ctx.fillText(`Description: ${alert.description}`, alert.x, alert.y + FONT_SIZE * 1)
    ctx.fillText(`time_start: ${alert.time_start}`, alert.x, alert.y + FONT_SIZE * 2)
    ctx.fillText(`time_stop: ${alert.time_stop}`, alert.x, alert.y + FONT_SIZE * 3)
    ctx.fillText(`cause: ${alert.cause}`, alert.x, alert.y + FONT_SIZE * 4)
    ctx.fillText(`effect: ${alert.effect}`, alert.x, alert.y + FONT_SIZE * 5)
    // add entities
  }
}

document.body.addEventListener('click', function(event) {
  // console.log(event);
  event.stopPropagation();
  event.preventDefault();

  if (currentModal.id === "viewFeedModal"){
    util.log("current modal is viewFeedModal");
    let searchX = event.pageX;
    let searchY = event.pageY - feedViewHeaderHeight;

    for (alert of alerts){
      util.log("searching...");
      if(objectContainsPoint(alert, searchX, searchY)){
        alertDetailView();
      }
    }
  }
});

function objectContainsPoint(object, x, y){
    return (x > object.x
          && x < object.x + BOX_WIDTH
          && y > object.y
          && y < object.y + BOX_HEIGHT)
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