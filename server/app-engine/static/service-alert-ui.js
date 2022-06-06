
const serverURL = document.location.origin;
const PEM_HEADER = "-----BEGIN TOKEN-----";
const PEM_FOOTER = "-----END TOKEN-----";
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');
var canvasWidth = window.innerWidth;
var canvasHeight = window.innerHeight;
var alertsPerRow;
var alerts = [];
var signatureKey = null;
var currentModal = null;
var agencyID = null;
var selectedAlert = null;
var feedViewHeaderHeight;
const fileMap = new Map();
const files = [
  "trips",
  "routes",
  "stops",
  "agency"
  ];
const dropdownsIDs = [
  "route-select",
  "trip-select",
  "stop-select",
  "route-type-select",
  "agency-select"
];
const textFieldIDs = [
  "header",
  "description",
  "url",
  "start-time",
  "stop-time"
];
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
  [1,'No Service'],
  [2,'Reduced Service'],
  [3,'Significant Delays'],
  [4,'Detour'],
  [5,'Additional Service'],
  [6,'Modified Service'],
  [7,'Other Effect'],
  [8,'Unknown Effect'],
  [9,'Stop Moved']
]);
const alertEntities = new Map([
  ['agency_id','AgencyID'],
  ['trip_id','TripID'],
  ['stop_id','StopID'],
  ['route_id','RouteID'],
  ['route_type','Route type']
]);
const alertFields = new Map([
  ['cause','Cause'],
  ['effect','Effect'],
  ['header','Header'],
  ['description','Description'],
  ['start_time','Start time'],
  ['stop_time','Stop time'],
]);

const BASE_URL = 'https://storage.googleapis.com/graas-resources/gtfs-archive'
const FONT_SIZE = 12;
const FONT_NORMAL = `${FONT_SIZE}px ARIAL`;
const FONT_BOLD = `bold ${FONT_SIZE}px ARIAL`;
const ALERT_ROWS = 8;
const SPACING = 10;
const BOX_WIDTH = 200;

initialize();

async function loadFiles(){
  const agencyURL = `${BASE_URL}/${agencyID}/`;

  for (let i = 0; i < files.length; i++) {
    let fileName = files[i];
    let url = agencyURL + fileName + ".txt";
    util.log('- fetching from ' + url);
    let response = await util.timedFetch(url, {method: 'GET'});
    let text = await response.text();
    fileMap.set(fileName,csvToArray(text));

    if(fileName === "routes"){
      // Create route-types category by getting unique route_types from routes
      let routeTypes = getItems("routes","route_type");
      fileMap.set("route_types", [...new Set(routeTypes)]);
    }
    if(fileMap.size === files.length + 1) populateDropdowns();
  }
  loadAlerts();
}

async function initialize(){
  util.log("initialize()");
  let str = localStorage.getItem("app-data") || "";

  if (!str) {
    util.handleModal("keyEntryModal");
  }
  else {
    let agencyData = await util.parseAgencyData(str);
    completeInitialization(agencyData);
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
    menu();
    loadFiles();
    resetCanvas();
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
  util.log("loadAlerts()");
  let rtFeedURL = `${serverURL}/service-alerts.pb?agency=${agencyID}&service_alert_ui=True&nocache=${(new Date()).getTime()}`;
  util.log("url: " + rtFeedURL)
  feed = await getPB(rtFeedURL);
  util.log("JSON.stringify(feed): " + JSON.stringify(feed));
  alerts = await feed.map(feedObject => {
    let a = feedObject.alert;
    let alertObject = new Object({
      id: feedObject.id,
      time_start: a.active_period[0].start,
      time_stop: a.active_period[0].end,
      agency_id: a.informed_entity[0].agency_id,
      trip_id: (a.informed_entity[0].trip !== null ? a.informed_entity[0].trip.trip_id : ""),
      stop_id: a.informed_entity[0].stop_id,
      route_id: a.informed_entity[0].route_id,
      route_type: (a.informed_entity[0].route_type !== 0 ? a.informed_entity[0].route_type !== 0 : ""),
      cause: causes.get(a.cause),
      effect: effects.get(a.effect),
      header: a.header_text.translation[0].text,
      description: a.description_text.translation[0].text,
      url: (a.url !== null ? a.url.translation[0].text : "")
    });
    if(alertObject.time_start === 0){
      alertObject.start_time = "None";
    }
    else{
      alertObject.start_time = (new Date(alertObject.time_start * 1000)).toLocaleString();
    }
    if(alertObject.time_stop === 0){
      alertObject.stop_time = "None";
    }
    else{
      alertObject.stop_time = (new Date(alertObject.time_stop * 1000)).toLocaleString();
    }
    alertObject.num_entities = (alertObject.agency_id !== "") + (alertObject.route_id !== "") + (alertObject.stop_id !== "") + (alertObject.trip_id !== "") + (alertObject.route_type !== 0)

    return alertObject;
  });
}

function menu(){
  util.log("menu()");
  util.dismissModal();
  util.handleModal("menuModal");
}

function createAlert(){
  // util.log("createAlert()");
  util.dismissModal();
  util.handleModal("alertCreateModal");
}

async function viewAlerts(){
  // util.log("viewAlerts()");
  util.dismissModal();
  util.handleModal("viewFeedModal");
  selectedAlert = null;
  await loadAlerts();
  feedView();
}

function resetCanvas(){
  ctx.canvas.height = canvasHeight;
  ctx.canvas.width = canvasWidth;
  alertsPerRow = Math.floor(canvasWidth / (BOX_WIDTH + SPACING));
  ctx.clearRect(0, 0, canvasWidth, canvasHeight);
  ctx.fillStyle = "white";
  ctx.fillRect(0, 0, canvasWidth, canvasHeight);
}

async function deleteAlert(){
  // util.log("deleteAlert()");

  let data = {
    agency_key: agencyID,
    time_start: selectedAlert.time_start,
    time_stop: selectedAlert.time_stop,
    agency_id: selectedAlert.agency_id,
    trip_id: selectedAlert.trip_id,
    stop_id: selectedAlert.stop_id,
    route_id: selectedAlert.route_id,
    route_type: selectedAlert.route_type,
    cause: selectedAlert.cause,
    effect: selectedAlert.effect,
    header: selectedAlert.header,
    description: selectedAlert.description
  };

  let response = await util.signAndPost(data, signatureKey, '/delete-alert');
  util.log("response: " + JSON.stringify(response));
    if (response.status === 'ok') {
    alert("Alert deleted");
  } else {
    alert("Alert failed to delete");
  }
  resetCanvas();
  menu();
}

function alertDetailView(){
  // util.log("alertDetailView()");
  util.dismissModal();
  util.handleModal("alertDetailModal");

  let ul = document.getElementById("affected-entities");
  util.clearUL(ul);
  alertEntities.forEach((value, key) => {
    if(selectedAlert[key] !== ""){
      let str = `${value}: ${selectedAlert[key]}`
      util.addToUL(ul, str);
    }
  });

  ul = document.getElementById("alert-detail-list");
  util.clearUL(ul);
  alertFields.forEach((value, key) => {
    if(selectedAlert[key] !== ""){
      let str = `${value}: ${selectedAlert[key]}`
      util.addToUL(ul, str);
    }
  });
}

async function handleKey(id) {
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

  // util.dismissModal();
  p.value = "";

  localStorage.setItem('app-data', value);
  let agencyData = await util.parseAgencyData(value);
  completeInitialization(agencyData);
}

function populateDropdowns(){
  // util.log(fileMap);
  if(fileMap.get("route_types").length === 1){
    util.hideElement("route-type");
  } else {
    util.populateSelectOptions("route-type-select", "Select a route type", fileMap.get("route_types"));
  }
  if(fileMap.get("agency").length === 1){
    util.hideElement("agency");
  } else {
    util.populateSelectOptions("agency-select", "Select an agency", getItems("agency", "agency_id"));
  }
  util.populateSelectOptions("cause-select", "Select an alert cause", Array.from(causes.values()));
  util.populateSelectOptions("effect-select", "Select an alert effect", Array.from(effects.values()));
  util.populateSelectOptions("route-select", "Select a Route ID", getItems("routes", "route_id"));
  util.populateSelectOptions("trip-select", "Select a Trip ID", getItems("trips", "trip_id"));
  util.populateSelectOptions("stop-select", "Select a Stop ID", getItems("stops", "stop_id"));
}

function getItems(type, columnName){
  return fileMap.get(type).map(function (el) { return el[columnName]; })
}

function handleEntitySelection(checkbox){
  util.log("handleEntitySelection()");
  let dropdownName = checkbox.id.slice(0,-8) + "select";
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

async function postServiceAlert() {
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

    if(header === null || description === null){
      alert("Please write both a header and a description");
      return;
    }

    if(startTime < 0 || stopTime < 0){
      alert("Please select start and stop dates that are in the future");
      return;
    }

    if(stopTime < startTime){
      alert("Start time must be before stop time");
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

    let response = await util.signAndPost(data, signatureKey, '/post-alert');
    util.log("response: " + JSON.stringify(response));

    if (response.status === 'ok') {
      alert("Alert posted successfully");
    } else {
      alert("Alert failed to post");
    }

    loadAlerts();
    menu();
    // resetFields();
    resetCanvas();
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
  let yy = SPACING;
  let xx = SPACING;
  let colNum = 1;
  let maxBoxHeight = 0;
  for (let i=0; i<alerts.length; i++){
    alerts[i].y = yy;
    alerts[i].x = xx;
    let textRows = ALERT_ROWS + alerts[i].num_entities;
    let box_height = textRows * FONT_SIZE;
    maxBoxHeight = Math.max(box_height, maxBoxHeight);
    if(colNum < alertsPerRow){
      xx += BOX_WIDTH + SPACING;
      colNum +=1;
      if(i === alerts.length-1){
        yy += maxBoxHeight + SPACING;
      }
    } else{
      yy += maxBoxHeight + SPACING;
      colNum = 1;
      xx = SPACING;
    }
  }

  canvasHeight = Math.max(yy, window.innerHeight);
  ctx.canvas.height = canvasHeight;
  util.log("canvasHeight: " + canvasHeight);
  util.log("window.innerHeight: " + window.innerHeight);
  resetCanvas();
}

async function feedView(){
  feedViewHeaderHeight = document.getElementById('feed-view-header').offsetHeight;
  if(alerts.length > 0){
    util.setElementText("feed-view-header", "Click an alert to see detail or delete");
    if(util.isNullOrUndefined(alerts[0].y)){
      createLayout();
    }
    drawAlerts();
  } else{
    util.setElementText("feed-view-header", "There are currently no alerts in your feed");
  }
}

function drawAlerts(){
  for (let a of alerts){
    ctx.beginPath();
    ctx.strokeStyle = "black";
    ctx.fillStyle = "white";

    let rows = ALERT_ROWS + a.num_entities;
    let box_height = rows * FONT_SIZE;
    ctx.fillRect(a.x, a.y, BOX_WIDTH, box_height);
    ctx.rect(a.x, a.y, BOX_WIDTH, box_height);
    ctx.stroke();

    ctx.fillStyle = "black";
    ctx.textBaseline = "top";
    ctx.font = FONT_BOLD;


    let entityLines = [];
    alertEntities.forEach((value, key) => {
      if(a[key] !== ""){
        let str = ` - ${value}: ${a[key]}`
        entityLines.push(str);
      }
    });

    let attributeLines = [];
    alertFields.forEach((value, key) => {
      if(a[key] !== ""){
        let str = `${value}: ${a[key]}`;
        attributeLines.push(str);
      }
    });

    let i = -1;
    ctx.fillText("This alert applies to:", a.x, a.y + FONT_SIZE * ++i);
    ctx.font = FONT_NORMAL;

    for(let line of entityLines){
      ctx.fillText(shortenFeedStringIfNeeded(line), a.x, a.y + FONT_SIZE * ++i)
    }

    ctx.font = FONT_BOLD;
    ctx.fillText("Alert details:", a.x, a.y + FONT_SIZE * ++i);
    ctx.font = FONT_NORMAL;

    for(let line of attributeLines){
      ctx.fillText(shortenFeedStringIfNeeded(line), a.x, a.y + FONT_SIZE * ++i)
    }

  }
}

document.body.addEventListener('click', function(event) {
  if (currentModal !== null && currentModal.id === "viewFeedModal"){
    util.log("current modal is viewFeedModal");
    let searchX = event.pageX;
    let searchY = event.pageY - feedViewHeaderHeight;

    for (let a of alerts){
      util.log("searching...");
      if(objectContainsPoint(a, searchX, searchY)){
        selectedAlert = a;
        alertDetailView(a);
      }
    }
  }
});

function shortenFeedStringIfNeeded(string){
  if(ctx.measureText(string).width > BOX_WIDTH){
    return shortenFeedString(string) + "...";
  } else return string;
}

function shortenFeedString(string){
  if(ctx.measureText(string + "...").width > BOX_WIDTH){
    return shortenFeedString(string.slice(0,-1))
  } else return string;
}
function objectContainsPoint(object, x, y){
    return (x > object.x
          && x < object.x + BOX_WIDTH
          && y > object.y
          && y < object.y + (ALERT_ROWS + object.num_entities) * FONT_SIZE
          )
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