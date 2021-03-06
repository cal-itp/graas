var hexDigits = '0123456789abcdef';
var lastModified = null;
var tripID = null;
var agencyID = null;
var vehicleID = null;
var running = false;
var tripSelected = false;
var vehicleSelected = false;
var noSleep = new NoSleep();
var currentModal = null;
var signatureKey = null;
var keyType = null;
var keyLength = -1;
var tripIDLookup = null;
var vehicleList = null;
var lastTouch = 0;
var quickTouchCount = 0;
var lastWakeLockOp = null;
var lastWakeLockMillis = 0;
var startMillis = Date.now();
var lastTripLoadMillis = 0;
var configMatrix = null;
var countFiles = null;
var countGetURLCallbacks = 0;
var startLat = null;
var startLon = null;
var trips = [];
var sessionID = null;
var useBulkAssignmentMode = false;

// Default filter parameters, used when agency doesn't have an agency-config.json file
var maxMinsFromStart = 60;
var isFilterByDayOfWeek = true;
var maxFeetFromStop = 1320;
var ignoreStartEndDate = false;

var testLat = null;
var testLong = null;
var testHour = null;
var testMin = null;
var testDow = null;
var testDate = null;
var version = null;

const UUID_NAME = 'lat_long_id';
const VEHICLE_ID_COOKIE_NAME = 'vehicle_id';
var vehicleIDCookie = null;
const MSG_NO = 'msg_no';
const MAX_AGE_SECS = util.SECONDS_PER_YEAR * 10;
const MAX_LIFE_MILLIS = util.MILLIS_PER_DAY * 7;
const MAX_VEHICLE_ID_AGE_SECS = util.SECONDS_PER_YEAR * 10;

const PEM_HEADER = "-----BEGIN TOKEN-----";
const PEM_FOOTER = "-----END TOKEN-----";

const QR_READER_ELEMENT = "qr-reader";
const CONFIG_TRIP_NAMES = "trip names";
const CONFIG_VEHICLE_IDS = "vehicle IDs";
const CONFIG_AGENCY_PARAMS = "agency params";
const START_STOP_BUTTON = "start-stop";
const START_STOP_BUTTON_LOAD_TEXT = "Load trips"
const START_STOP_BUTTON_STOP_TEXT = "Stop"
const TRIP_SELECT_DROPDOWN = "trip-select";
const TRIP_SELECT_DROPDOWN_TEXT = "Select Trip";
const BUS_SELECT_DROPDOWN = "bus-select";
const BUS_SELECT_DROPDOWN_TEXT = "Select Bus No.";
const ALL_DROPDOWNS = "config";
const LOADING_TEXT_ELEMENT = "loading";
const TRIP_STATS_ELEMENT = "stats";
var BASE_URL = 'https://raw.githubusercontent.com/cal-itp/graas/main/server/agency-config/gtfs/gtfs-aux';

const EARTH_RADIUS_IN_FEET = 20902231;
const FEET_PER_MILE = 5280;

const GRAY_HEX = "#cccccc";

function isObject(obj) {
    return typeof obj === 'object' && obj !== null
}

function toRadians(degrees) {
    return degrees * (Math.PI / 180);
}

// return distance in feet between to lat/long pairs
function getHaversineDistance(lat1, lon1, lat2, lon2) {
    const phi1 = toRadians(lat1);
    const phi2 = toRadians(lat2);
    const deltaPhi = toRadians(lat2 - lat1);
    const deltaLam = toRadians(lon2 - lon1);

    const a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
        + Math.cos(phi1) * Math.cos(phi2)
        * Math.sin(deltaLam / 2) * Math.sin(deltaLam / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_IN_FEET * c;
}

function isMobile() {
    util.log("isMobile()");
    util.log("- navigator.userAgent: " + navigator.userAgent);
    util.log("- navigator.maxTouchPoints: " + navigator.maxTouchPoints);

    if (navigator.maxTouchPoints && navigator.maxTouchPoints > 0) {
        util.log("- navigator.maxTouchPoints: " + navigator.maxTouchPoints);
        return true;
    }

    let result = navigator.userAgent.match(/Android/i)
     || navigator.userAgent.match(/webOS/i)
     || navigator.userAgent.match(/iPhone/i)
     || navigator.userAgent.match(/iPad/i)
     || navigator.userAgent.match(/iPod/i)
     || navigator.userAgent.match(/BlackBerry/i)
     || navigator.userAgent.match(/Windows Phone/i);
    util.log("- result: " + result);

    return result;
}

function isPhone() {
    util.log("isPhone()");
    util.log("- navigator.userAgent: " + navigator.userAgent);
    util.log("- navigator.maxTouchPoints: " + navigator.maxTouchPoints);

    let result = navigator.userAgent.match(/Android/i)
     || navigator.userAgent.match(/iPhone/i);
    util.log("- result: " + result);

    return result;
}

function resizeElement(e) {
    // util.log("resizeElement()");
    // util.log("- e: " + e);

    if (e) {
        e.style.width = "97%";
        e.style.height = "200px";
        e.style.fontSize = "64px";
    }
}

function resizeElementFont(e) {
    // util.log("resizeElementFont()");
    // util.log("- e: " + e);

    if (e) {
        e.style.fontSize = "64px";
    }
}

function handleResumeButton() {
    util.log("handleResumeButton()");
    dismissModal();
    setWakeLock();
}

function setWakeLock() {
    util.log("setWakeLock()");
    util.log("- running: " + running);

    if (window.hasOwnProperty("graasShimVersion")) {
        util.log("+ running in shim, not setting web wake lock");
        return;
    }

    let wakeLockOp = "set";
    let wakeLockOkay = (wakeLockOp != lastWakeLockOp) || (Date.now() - lastWakeLockMillis >= 2500)

    //util.log("- wakeLockOp: " + wakeLockOp);
    //util.log("- lastWakeLockOp: " + lastWakeLockOp);
    //util.log("- wakeLockOp != lastWakeLockOp: " + (wakeLockOp != lastWakeLockOp));

    if (running && wakeLockOkay /*&& isMobile()*/) {
        util.log("+ enabling wake lock");
        if (noSleep) noSleep.disable();
        noSleep = new NoSleep();
        noSleep.enable();
    }

    lastWakeLockOp = wakeLockOp;
    lastWakeLockMillis = Date.now();
}

function clearWakeLock() {
    util.log("clearWakeLock()");
    util.log("- running: " + running);

    if (window.hasOwnProperty("graasShimVersion")) {
        util.log("+ running in shim, not clearing web wake lock");
        return;
    }

    let wakeLockOp = "clear";
    let wakeLockOkay = (wakeLockOp != lastWakeLockOp) || (Date.now() - lastWakeLockMillis >= 2500)

    //util.log("- wakeLockOp: " + wakeLockOp);
    //util.log("- lastWakeLockOp: " + lastWakeLockOp);
    //util.log("- wakeLockOp != lastWakeLockOp: " + (wakeLockOp != lastWakeLockOp));

    if (running && wakeLockOkay /*&& isMobile()*/) {
        util.log("+ disabling wake lock");
        noSleep.disable();
    }

    lastWakeLockOp = wakeLockOp;
    lastWakeLockMillis = Date.now();
}

function handleTouch() {
    //util.log("handleTouch()");

    let now = Date.now();
    let delta = (now - lastTouch);
    //util.log("- delta: " + delta);

    if (now - lastTouch < 300) {
        quickTouchCount++;
        //util.log("- quickTouchCount: " + quickTouchCount);

        if (quickTouchCount >= 6) {
            let p = document.getElementById('console');
            p.style.display = p.style.display === 'none' ? 'block' : 'none';
            //util.log("- p.style.display: " + p.style.display);

            quickTouchCount = 0;
        }
    } else {
        quickTouchCount = 0;
    }

    lastTouch = now;
}

function handleOnBlur() {
    util.log("handleOnBlur()");
    clearWakeLock();
}

function handleOnFocus() {
    util.log("handleOnFocus()");

    if (running) {
        handleModal("resumeModal");
    }
}

function handleModal(name) {
    util.log("handleModal()");
    util.log("- name: " + name);

    currentModal = document.getElementById(name);
    currentModal.style.display = "block";
}

function dismissModal() {
    util.log("dismissModal()");
    util.log("- currentModal: " + currentModal);

    if (currentModal) {
        currentModal.style.display = "none";
        currentModal = undefined;
    }
}

function handleKey() {
    util.log("handleKey()");
    handleModal("keyEntryModal");
}

function parseAgencyData(str) {
    util.log("parseAgencyData()");
    util.log("- str: " + str);

    let id = null;
    let pem = null;

    let i1 = str.indexOf(PEM_HEADER);
    util.log("- i1: " + i1);

    if (i1 > 0 && str.substring(0, i1).trim().length > 0) {
        id = str.substring(0, i1).trim();
        pem = str.substring(i1);
    } else {
        pem = str;
    }

    return {
        id: id,
        pem: pem
    };
}

function handleKeyOkay() {
    util.log("handleKeyOkay()");

    let p = document.getElementById('keyTextArea');
    let value = p.value.replace(/\n/g, "");
    util.log("- value: " + value);

    let i1 = value.indexOf(PEM_HEADER);
    util.log("- i1: " + i1);

    let i2 = value.indexOf(PEM_FOOTER);
    util.log("- i2: " + i2);

    if (i1 < 0 || i2 < 0) {
        alert("not a valid key");
        return;
    }

    dismissModal();
    p.value = "";

    localStorage.setItem("lat-long-pem", value);
    initializeCallback(parseAgencyData(value));
}

function handleRefreshOkay() {
    util.log("handleRefreshOkay()");
    window.location.reload();
}

function handleStartStop() {
    util.log("handleStartStop()");

    let p = document.getElementById(START_STOP_BUTTON);
    let text = p.textContent || p.innerText;
    util.log("- text: " + text);

    // Driver taps "Load trips", dropdown options appear
    if (text === START_STOP_BUTTON_LOAD_TEXT) {
        let millis = Date.now();
        util.log("- millis     : " + millis);
        util.log("- startMillis: " + startMillis);
        util.log("+ delta: " + (millis - startMillis));

        util.hideElement(ALL_DROPDOWNS);
        util.showElement(LOADING_TEXT_ELEMENT);

        configMatrix.setSelected(CONFIG_TRIP_NAMES, false);
        configMatrix.setSelected(CONFIG_VEHICLE_IDS, false);

        vehicleIDCookie = getCookie(VEHICLE_ID_COOKIE_NAME);
        if(vehicleIDCookie){
            assignValue(BUS_SELECT_DROPDOWN, vehicleIDCookie);
            handleBusChoice();
        }

        if(!useBulkAssignmentMode){
            // Only load trips again if they were last loaded more than a minute ago
            if ((millis - lastTripLoadMillis) < util.MILLIS_PER_MINUTE * 1) {
                populateTripList();
            } else {
                util.log("- trip list is stale. Reloading...");
                populateTripList(loadTrips());
            }
        } else {
            util.hideElement(LOADING_TEXT_ELEMENT);
            util.showElement(ALL_DROPDOWNS);
        }

        if (millis - startMillis >= MAX_LIFE_MILLIS) {
            handleModal("staleModal");
            return;
        }
        let p = document.getElementById(START_STOP_BUTTON);
        p.style.background = GRAY_HEX;
    }
    // Driver taps "stop", sends app to blank screen with only "Load trips" button
    else {
        clearWakeLock();
        util.hideElement(TRIP_STATS_ELEMENT);
        util.log('- stopping position updates');
        running = false;
        let dropdowns = [TRIP_SELECT_DROPDOWN, BUS_SELECT_DROPDOWN];
        disableElements(dropdowns);

        p = document.getElementById('okay');
        p.disabled = 'true';
        p.style.background = GRAY_HEX;

        changeText(START_STOP_BUTTON, START_STOP_BUTTON_LOAD_TEXT);

        if (window.hasOwnProperty('graasShimVersion') && graasShimVersion.startsWith("android")) {
            fetch('/graas-stop').then(function(response) {
                util.log('- response.status: ' + response.status);
                util.log('+ requested graas stop');
            });
        }
    }
}

function checkForConfigCompletion() {
    if (configMatrix.isComplete()) {
        let p = document.getElementById('okay');
        p.disabled = false;
        p.style.background = "blue";
        p.addEventListener('click', handleOkay);
    }
}

function handleTripChoice() {
    util.log("handleTripChoice()");

    configMatrix.setSelected(CONFIG_TRIP_NAMES, true);
    checkForConfigCompletion();
}

function handleBusChoice() {
    util.log("handleBusChoice()");

    configMatrix.setSelected(CONFIG_VEHICLE_IDS, true);
    checkForConfigCompletion();
}

// Driver taps "Go" to start a ride
function handleOkay() {
    util.log("handleOkay()");

    sessionID = createUUID();
    let p = document.getElementById("session-id");
    p.innerHTML = "Session ID: " + sessionID;

    if (window.hasOwnProperty('graasShimVersion') && graasShimVersion.startsWith("android")) {
        fetch('/graas-start').then(function(response) {
            util.log('- response.status: ' + response.status);
            util.log('+ requested graas start');
        });
    }
    p = document.getElementById(START_STOP_BUTTON);
    p.style.background = "green";
    p = document.getElementById(BUS_SELECT_DROPDOWN);
    vehicleID = p.value

    document.cookie = `${VEHICLE_ID_COOKIE_NAME}=${vehicleID}; max-age=${MAX_VEHICLE_ID_AGE_SECS}`;
    util.log("- vehicleID: " + vehicleID);

    if(!useBulkAssignmentMode){
        p = document.getElementById(TRIP_SELECT_DROPDOWN);
        let entry = tripIDLookup[p.value];

        if (isObject(entry)) {
            tripID = entry['trip_id'];
        } else {
            tripID = entry;
        }

        util.log("- tripID: " + tripID);
    }

    p = document.getElementById('vehicle-id');
    p.innerHTML = "Vehicle ID: " + vehicleID;

    p = document.getElementById('trip-assignment-mode');
    let tripAssignmentMode = (useBulkAssignmentMode ? 'bulk' : 'manual')
    p.innerHTML = "Trip assignment mode: " + tripAssignmentMode;

    util.hideElement(ALL_DROPDOWNS);
    util.showElement(TRIP_STATS_ELEMENT);
    changeText(START_STOP_BUTTON, START_STOP_BUTTON_STOP_TEXT);

    util.log('- starting position updates');
    running = true;
    setWakeLock();
}

function getCookie(name) {
    const cookie = document.cookie;
    if (!cookie) return null;

    const list = cookie.split('; ');

    for (let i=0; i<list.length; i++) {
        if (list[i].startsWith(name + '=')) {
            return list[i].split('=')[1];
        }
    }

    return null;
}

function getUUID() {
    let uuid = getCookie(UUID_NAME);

    if (!uuid) {
        uuid = createUUID();
        document.cookie = `${UUID_NAME}=${uuid}; max-age=${MAX_AGE_SECS}`;
    }

    return uuid;
}

function getNextMsgNo() {
    let s = getCookie(MSG_NO);

    if (!s) s = '0';

    let n = parseInt(s);
    let nextN = ++n;
    if (nextN < 0) nextN = 0;

    document.cookie = `${MSG_NO}=${nextN}; max-age=${MAX_AGE_SECS}`;

    return n;
}

// ### todo: create UUIDs that follow the spec, instead of these
// completely fake ones
function createUUID() {
    let s = ""

    for (let i=0; i<32; i++) {
        s += hexDigits[Math.floor(Math.random() * hexDigits.length)];

        if (i === 7 || i === 11 || i === 15 || i === 19) {
            s += '-';
        }
    }

    return s;
}

function pad(n) {
    if (n < 10) return '0' + n;
    return n;
}

// Get URL content for one file, send it to gotConfigData.
// gotConfigData will call this function again, to load the next file until they are all loaded.
function getURLContent(agencyID, arg) {
    util.log('getURLContent()');
    countGetURLCallbacks++;
    if (countGetURLCallbacks > countFiles) {
        util.log("* Error: too many callbacks");
        alert('Error: too many callbacks');
    }
    locator = configMatrix.getNextToLoad().locator;
    name = configMatrix.getNextToLoad().name;

    url = `${BASE_URL}/${agencyID}/${locator}?foo=${arg}`
    util.log('- fetching from ' + url);
    util.timedFetch(url, {
        method: 'GET'/*,
        mode: 'no-cors'*/
    })
    .then(function(response) {
        util.log('- response.status: ' + response.status);
        util.log('- response.statusText: ' + response.statusText);
        //util.log('- response.json(): ' + response.json());

        if (response.status == 404) {
            configMatrix.setPresent(name, ConfigMatrix.NOT_PRESENT);
            gotConfigData(null, agencyID, arg);
        } else {
            configMatrix.setPresent(name, ConfigMatrix.PRESENT);
            response.json().then(function(data) {
                gotConfigData(data, agencyID, arg);
            });
        }
    })
    .catch((error) => {
        util.log('*** fetch() error: ' + error);
    });
}

function addSelectOption(sel, text, disabled) {
    let opt = document.createElement('option');

    opt.appendChild(document.createTextNode(text));
    opt.disabled = disabled;
    sel.appendChild(opt);
}

function clearSelectOptions(sel) {
    let l = sel.options.length - 1;
    util.log('- l: ' + l);

    for(let i = l; i >= 0; i--) {
        sel.remove(i);
    }
}

function handleEvent(e) {
    util.log('- event.type: ' + event.type);
}

function getRewriteArgs() {
    const s = window.location.search;
    const arg = s.split(/[&\?]/);

    for (let a of arg) {
        if (!a) continue;

        const t = a.split('=');
        const key = t[0];
        const value = t[1];

        util.log(`- ${key}: ${value}`);

        if (key === 'mode') {
            mode = value;
            util.log(`- mode: ${mode}`);
        } else if (key === 'testlat') {
            testLat = value;
        } else if (key === 'testlong') {
            testLong = value;
        } else if (key === 'testhour') {
            testHour = value;
        } else if (key === 'testmin') {
            testMin = value;
        } else if (key === 'testdow') {
            testDow = value;
        } else if (key === 'testdate') {
            testDate = value;
        }
    }
}

function scanQRCode() {
    util.log("scanQRCode()");

    let lastResult = 0;
    let countResults = 0;

    let elem = document.getElementById('qr-reader');
    let qw = elem.clientWidth;
    util.log(`- qw: ${qw}`);

    let html5QrcodeScanner = new Html5QrcodeScanner(
        "qr-reader", { fps: 10, qrbox: Math.round(qw * .83), videoConstraints: {facingMode: isMobile() ? "environment" : "user"}});

    function onScanSuccess(qrCodeMessage) {
        if (qrCodeMessage !== lastResult) {
            ++countResults;
            lastResult = qrCodeMessage;
            //resultContainer.innerHTML += `<div>[${countResults}] - ${qrCodeMessage}</div>`;
            // util.log(`- qrCodeMessage: '${qrCodeMessage}'`);

            let value = qrCodeMessage.replace(/\n/g, "");
            // util.log("- value: " + value);

            let i1 = value.indexOf(PEM_HEADER);
            // util.log("- i1: " + i1);

            let i2 = value.indexOf(PEM_FOOTER);
            // util.log("- i2: " + i2);

            if (i1 < 0 || i2 < 0) {
                alert("not a valid key");
                return;
            } else {
                localStorage.setItem("lat-long-pem", value);
                initializeCallback(parseAgencyData(value));
                html5QrcodeScanner.clear();

                msg = 'Sucessfully scanned token';

                if (i1 > 0) {
                    msg += ' for agency "' + value.substring(0, i1) + '"';
                }

                msg += '!';

                alert(msg);

            }
        }
    }

    // Optional callback for error, can be ignored.
    function onScanError(qrCodeError) {
        // This callback would be called in case of qr code scan error or setup error.
        // You can avoid this callback completely, as it can be very verbose in nature.
        //console.error(qrCodeError);
    }

    html5QrcodeScanner.render(onScanSuccess, onScanError);
    util.log("+ called html5QrcodeScanner.render()");
}

function initialize() {
    util.log("+ page loaded 6");

    if (window.hasOwnProperty('graasShimVersion')) {
        util.log("- graasShimVersion: " + graasShimVersion);
    }

    const urlArgs = new URLSearchParams(window.location.search);
    const cloudless = urlArgs.get('cloudless');
    util.log('- cloudless: ' + cloudless);

    if (cloudless === 'true') {
        BASE_URL = 'https://127.0.0.1:8080';
    }

    let e = document.getElementById("version");
    version = e.innerHTML;
    let i = version.indexOf(': ');
    if (i > 0 && i + 1 < version.length) {
        version = version.substring(i + 2)
    }
    util.log("- version: " + version);
    getRewriteArgs();

    navigator.geolocation.getCurrentPosition(function(position) {
        util.log("getCurrentPosition() callback");
        startLat = position.coords.latitude;
        startLon = position.coords.longitude;

        positionCallback();
    }, function(error) {
        util.log("* can't get position: " + error);
        alert('can\'t get GPS coordinates');
    }, {
        enableHighAccuracy: true,
        timeout: 5000,
        maximumAge: 0
    });
}

function positionCallback() {
    util.log("+ got start position");

    if (isPhone()) {
        let list = [START_STOP_BUTTON, TRIP_SELECT_DROPDOWN, BUS_SELECT_DROPDOWN, "okay"];
        list.forEach(l => resizeElement(document.getElementById(l)));

        list = ["key-title", "keyTextArea", "key-okay", "stale-title", "stale-okay", "resume"];
        list.forEach(l => resizeElementFont(document.getElementById(l)));
    }

    let str = localStorage.getItem("lat-long-pem") || "";

    if (!str) {
        if (window.hasOwnProperty("graasShimVersion") && graasShimVersion.startsWith("ios")) {
            // ios WKWebView doesn't support camera access :[
            handleModal("keyEntryModal");
        } else {
            scanQRCode();
        }
    } else {
        initializeCallback(parseAgencyData(str));
    }
}

async function handleGPSUpdate(position) {
    util.log('handleGPSUpdate()');
    let uuid = getUUID();
    let lat = 0;
    let long = 0;
    let accuracy = 0;
    let posTimestamp = 0;
    let speed = 0;
    let heading = 0;

    if (position) {
        lat = position.coords.latitude;
        long = position.coords.longitude;
        accuracy = position.coords.accuracy;
        posTimestamp = Math.round(position.timestamp / 1000);
        speed = position.coords.speed;
        heading = position.coords.heading;
    }

    let timestamp = Math.floor(Date.now() / 1000);

    let p = document.getElementById("lat");
    p.innerHTML = "Lat: " + lat;

    p = document.getElementById("long");
    p.innerHTML = "Long: " + long;

    let agent = navigator.userAgent.match(/\(.*?\)/);

    if (!speed) speed = 0;
    if (!heading) heading = 0;

    /*util.log('');
    util.log('- uuid: ' + uuid);
    util.log('- pos timestamp: ' + posTimestamp);
    util.log('- timestamp: ' + timestamp);
    util.log('- lat: ' + lat);
    util.log('- long: ' + long);
    util.log('- accuracy: ' + accuracy);
    util.log('- speed: ' + speed);
    util.log('- heading: ' + heading);
    util.log('- agent: ' + agent);*/

    let data = {
        uuid: uuid,
        agent: agent,
        timestamp: timestamp,
        lat: lat,
        long: long,
        speed: speed,
        heading: heading,
        accuracy: accuracy,
        version: version
    };

    data['trip-id'] = tripID;
    data['agency-id'] = agencyID;
    data['vehicle-id'] = vehicleID;
    data['session-id'] = sessionID;
    data['pos-timestamp'] = posTimestamp;
    data['use-bulk-assignment-mode'] = useBulkAssignmentMode;

    let response = await util.signAndPost(data, signatureKey, '/new-pos-sig');
    let responseJson = await response.json();

    if (!response.ok) {
        let p = document.getElementById('server-response');
        p.innerHTML = 'Server response: ' + responseJson.status + ' ' + responseJson.statusText;
        util.log('server response: ' + responseJson.status + ' ' + responseJson.statusText);
    } else {

        let p = document.getElementById('last-update');
        let now = new Date();
        let hour = now.getHours();
        let ampm = hour >= 12 ? "PM" : "AM";

        if (hour > 12) hour -= 12;
        if (hour === 0) hour = 12;

        let time = hour + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds()) + " " + ampm;

        p.innerHTML = 'Last update: ' + time;

        p = document.getElementById('server-response');
        p.innerHTML = 'Server response: ok';
    }
}

async function initializeCallback(agencyData) {
    let pem = agencyData.pem;

    let i1 = pem.indexOf(PEM_HEADER);
    util.log("- i1: " + i1);

    let i2 = pem.indexOf(PEM_FOOTER);
    util.log("- i2: " + i2);

    let b64 = pem.substring(i1 + PEM_HEADER.length, i2);
    // util.log("- b64: " + b64);
    // util.log("- b64.length: " + b64.length);

    if (b64.length < 256) {
        keyType = "ECDSA";
        keyLength = 256;
    } else if (b64.length < 3000) {
        keyType = "RSA";
        keyLength = b64.length < 1024 ? 1024 : 2048;
    } else {
        alert("unsupported key wrapper length: " + b64.length);
        return;
    }

    util.log("- keyType: " + keyType);
    // util.log("- keyLength: " + keyLength);

    let b = atob(b64);
    // util.log("- key.length: " + key.length);

    const binaryDer = util.str2ab(b);
    try{
        let key = await crypto.subtle.importKey(
            "pkcs8",
            binaryDer,
            keyType === "RSA"
            ? {
                name: "RSASSA-PKCS1-v1_5",
                modulusLength: keyLength,
                publicExponent: new Uint8Array([1, 0, 1]),
                hash: "SHA-256",
            }
            : {
                name: "ECDSA",
                namedCurve: "P-256"
            },
            false,
            ["sign"]
        )

        util.log("- key.type: " + key.type);
        signatureKey = key;

        let str = 'hello ' + Math.floor(Date.now() / 1000);

        let buf = await util.sign(str, signatureKey)

        let sig = btoa(util.ab2str(buf));

        let hello = {
            msg: str,
            sig: sig
        };

        if (agencyData.id) {
            hello.id = agencyData.id;
        }

        util.log("- hello: " + JSON.stringify(hello));

        let response = await util.apiCall(hello, '/hello');
        let responseJson = await response.json();
        util.log("- responseJson: " + JSON.stringify(responseJson));
        agencyIDCallback(responseJson);

    } catch(e){
          util.log('*** initializeCallback() error: ' + e.message);
          localStorage.removeItem("lat-long-pem");
          alert("We've experienced an error and are refreshing the page. Please scan again");
          window.location.reload();
    }
}

function agencyIDCallback(response) {
    agencyID = response.agencyID;
    util.log("- agencyID: " + agencyID);

    util.showElement(LOADING_TEXT_ELEMENT);
    util.hideElement(QR_READER_ELEMENT);

    if (agencyID === 'not found') {
        alert('could not verify client identity');
    } else {
        // Passing current timestamp as an argument ensures that the config file will refresh, rather than loading from cache.
        let arg = Date.now()
        util.log("- arg: " + arg);
        getURLContent(agencyID, arg);
    }
}

function getTimeFromName(s) {
    // s looks something like: "Trip 10 @ 5:58 am"
    // Returns the text that is to the right of the last '@', which solves for the situation where the trip name contains an '@'

    // util.log("- s: " + s)
    // if there is no '@', return null
    if (s.indexOf('@') < 0)
    {
        util.log("* Trip name is missing an '@', will only show up on dropdown if max-mins-from-start < 0")
        return null
    }
    let timeFromName = s.substring(s.lastIndexOf('@') + 2)
    // util.log("- timeFromName: " + timeFromName)
    return timeFromName;
}

// Load json file content, one at a time, and perform filtering in some cases.
// Call getURLContent until all files are loaded, call configComplete when done.
function gotConfigData(data, agencyID, arg) {
    util.log("gotConfigData()");
    util.log("- agencyID: " + agencyID);
    util.log("- arg: " + arg);
    util.log("- data: ");
    util.log(data);

    name = configMatrix.getNextToLoad().name;
    util.log("- name: " + name);
    if (name === CONFIG_AGENCY_PARAMS) {
        configMatrix.setSelected(CONFIG_AGENCY_PARAMS, true);
        if (data != null) {
            isFilterByDayOfWeek = data["is-filter-by-day-of-week"];
            maxMinsFromStart = data["max-mins-from-start"];
            maxFeetFromStop = data["max-feet-from-stop"];
            if(data["use-bulk-assignment-mode"] !== null){
                useBulkAssignmentMode = data["use-bulk-assignment-mode"];
            }
            ignoreStartEndDate = data["ignore-start-end-date"];
            // util.log(`- useBulkAssignmentMode: ${useBulkAssignmentMode}`);
            // util.log(`- isFilterByDayOfWeek: ${isFilterByDayOfWeek}`);
            // util.log(`- maxMinsFromStart: ${maxMinsFromStart}`);
            // util.log(`- maxFeetFromStop: ${maxFeetFromStop}`);
            // util.log(`- ignoreStartEndDate: ${ignoreStartEndDate}`);
        }
    }
    else if (name === CONFIG_TRIP_NAMES) {
        if (useBulkAssignmentMode){
            configMatrix.setPresent(name, ConfigMatrix.NOT_PRESENT)
            util.hideElement(TRIP_SELECT_DROPDOWN);
        } else {
            trips = data;
            loadTrips();
        }
    } else if (name === CONFIG_VEHICLE_IDS) {
        vehicleList = data;
        util.populateSelectOptions(BUS_SELECT_DROPDOWN, BUS_SELECT_DROPDOWN_TEXT, vehicleList);
    }

    configMatrix.setLoaded(name, true);
    if (configMatrix.isLoaded()) {
        configComplete();
    } else getURLContent(agencyID, arg);
}

// Load & filter trips, and then populate dropdown
function loadTrips() {
    util.log("- loading trips");
    tripIDLookup = {};

    if (testLat && testLong) {
        // util.log(`- testLat: ${testLat}`);
        // util.log(`- testLong: ${testLong}`);
        startLat = testLat;
        startLon = testLong;
    }
    let dow = util.getDayOfWeek();
    if (testDow) dow = testDow;

    let date = util.getTodayYYYYMMDD();
    if(testDate) date = testDate;

    for (let i = 0; i < trips.length; i++) {
        // util.log(`- trips.length: ${trips.length}`);
        if (!Array.isArray(trips) || !isObject(trips[i])) continue;
            // util.log(`-- trips[i]: ${trips[i]}`);
            const tripInfo = trips[i];
            const time = getTimeFromName(tripInfo.trip_name);
            //util.log(`- time: ${time}`);
            const lat = tripInfo.departure_pos.lat;
            // util.log(`- lat: ${lat}`);
            const lon = tripInfo.departure_pos.long;
            // util.log(`- lon: ${lon}`);
            const timeDelta = util.getTimeDelta(time);
            // util.log(`- timeDelta: ${timeDelta}`);
            const distance = getHaversineDistance(lat, lon, startLat, startLon);
            // util.log(`- distance: ${distance}`);
            let holidayOn = false;
            let holidayOff = false;
            const onDates = tripInfo.on_dates;
            const offDates = tripInfo.off_dates;

            // Check whether array of numbers (ie [20220101,20221225] contains string date (ie "20221225")
            if(!util.isNullOrUndefined(onDates) && onDates.includes(parseInt(date,10))){
                holidayOn = true;
            }
            if(!util.isNullOrUndefined(offDates) && offDates.includes(parseInt(date,10))){
                holidayOff = true;
            }
            // 4 conditions need to be met for inclusion...
            if (
                    // 1. meets time parameters
                    (maxMinsFromStart < 0 || (timeDelta != null && timeDelta < maxMinsFromStart))
                    &&
                    // 2. meets day-of-week parameters or has holiday exception
                    (
                        holidayOn  ||
                        (
                            (!isFilterByDayOfWeek || (tripInfo.calendar != null && tripInfo.calendar[dow] === 1))
                            && !holidayOff
                        )
                    )
                    &&
                    // 3. meets distance parameters:
                    (maxFeetFromStop < 0 || getHaversineDistance(lat, lon, startLat, startLon) < maxFeetFromStop)
                    &&
                    // 4. Falls between start_date and end_date
                    (ignoreStartEndDate || (date >= tripInfo.start_date && date <= tripInfo.end_date))
                )
            {
                // util.log(`+ adding ${tripInfo["trip_name"]}`);
                tripIDLookup[tripInfo["trip_name"]] = tripInfo;
            }
    }

    lastTripLoadMillis = Date.now()
    return tripIDLookup;
}

function gpsInterval(millis) {
    if (navigator.onLine && running) {
        if (window.hasOwnProperty('graasShimVersion') && graasShimVersion.startsWith("android")) {
            //util.log('+ gpsInterval() tick ' + Math.floor(Date.now() / 1000) + '...');
            //util.log('- graasShimVersion: ' + graasShimVersion);

            fetch('/graas-location').then(function(response) {
                util.log('- response.status: ' + response.status);

                response.json().then(function(data) {
                    util.log('response.json() callback');
                    util.log(JSON.stringify(data));

                    handleGPSUpdate(data);
                });
            });
        } else {
            navigator.geolocation.getCurrentPosition(function(position) {
                handleGPSUpdate(position);
            }, function(error) {
                util.log('*** getCurrentPosition() error: ' + error.message);
            }, {
                enableHighAccuracy: true,
                timeout: 120000,
                maximumAge: 0
            });
        }
    }

    setTimeout(gpsInterval, 3000, Date.now());
}

function disableElement(id) {
    assignValue(id, 'disabled');
}

function disableElements(list) {
    list.forEach(el => disableElement(el));
}

function changeText(id,text) {
    let p = document.getElementById(id);
    p.textContent = text;
}

function assignValue(id, value){
    let p = document.getElementById(id);
    p.value = value;
}

// Populates dropdown, and then shows all dropdowns
function populateTripList(tripIDMap = tripIDLookup) {
    util.log("populateTripList()");
    let p = document.getElementById(TRIP_SELECT_DROPDOWN);
    clearSelectOptions(p);
    addSelectOption(p, TRIP_SELECT_DROPDOWN_TEXT, true);

    for (const [key, value] of Object.entries(tripIDMap)) {
        addSelectOption(p, key, !value);
    }

    util.setupSelectHeader(p);
    util.hideElement(LOADING_TEXT_ELEMENT);
    util.showElement(ALL_DROPDOWNS);
}

function configComplete() {
    util.log("configComplete()");

    vehicleIDCookie = getCookie(VEHICLE_ID_COOKIE_NAME);
    util.hideElement(LOADING_TEXT_ELEMENT);

    // If bulk assignment mode and vehicleID is already cached, simply start tracking
    if(vehicleIDCookie && useBulkAssignmentMode){
        assignValue(BUS_SELECT_DROPDOWN, vehicleIDCookie);
        handleOkay()
    }
    // If bulk assignment mode and vehicleID is NOT cached, present vehicleID dropdown
    else if(useBulkAssignmentMode){
        handleStartStop()
    }
    // If not bulk assignment mode, present the "Load trips" button.
    else{
        util.showElement(START_STOP_BUTTON);
    }

    setInterval(function() {
        if (!running) {
            util.log("checking for updated version..");

            util.timedFetch(window.location, {
                method: 'HEAD',
            })
            .then(function(response) {
                let lm = response.headers.get('Last-Modified');
                util.log('- lastModified: ' + lastModified);
                util.log('-           lm: ' + lm);

                if (!lastModified) {
                    lastModified = lm;
                    util.log('page up-to-date');
                } else {
                    if (lastModified != lm) {
                        util.log('page changed, reloading...');
                        lastModified = lm;

                        window.location.reload();
                    }
                }
            })
            .catch((error) => {
                util.log('*** fetch() error: ' + error);
            });
        }
    }, 15 * 60000);

    if (navigator && navigator.geolocation) {
        let uuid = getUUID();
        let p = document.getElementById("uuid");
        p.innerHTML = "UUID: " + uuid;

        gpsInterval(Date.now());
    } else {
        alert('no geolocation access');
    }
}

if (!Object.entries) {
   Object.entries = function( obj ){
      let ownProps = Object.keys( obj ),
         i = ownProps.length,
         resArray = new Array(i); // preallocate the Array

      while (i--)
         resArray[i] = [ownProps[i], obj[ownProps[i]]];
      return resArray;
   };
}

configMatrix = new ConfigMatrix();

// The below files will be processed in the order they appear here. It's important that agency-params goes before trip-names
configMatrix.addRow(CONFIG_VEHICLE_IDS, "vehicle-ids.json", ConfigMatrix.PRESENT);
configMatrix.addRow(CONFIG_AGENCY_PARAMS, "agency-params.json", ConfigMatrix.UNKNOWN);
configMatrix.addRow(CONFIG_TRIP_NAMES, "trip-names.json", ConfigMatrix.PRESENT);
countFiles = configMatrix.countRows();

initialize();