var hexDigits = '0123456789abcdef';
var lastModified = null;
var tripID = null;
var agencyID = null;
var vehicleID = null;
var running = false;
var routeSelected = false;
var vehicleSelected = false;
var noSleep = new NoSleep();
var canvas = null;
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
var startMillis = 0;
var configMatrix = null;
var countFiles = null;
var countGetURLCallbacks = 0;
var driverList = null;
var driverName = null;
var startLat = null;
var startLon = null;
var trips = [];
var mode = 'vanilla';

// Default filter parameters, used when agency doesn't have a filter-config.json file
var maxMinsFromStart = 60;
var isFilterByDayOfWeek = true;
var maxFeetFromStop = 1320;

var testLat = null;
var testLong = null;
var testHour = null;
var testMin = null;
var testDow = null;
var version = null;

const UUID_NAME = 'lat_long_id';
const MSG_NO = 'msg_no';
const MAX_AGE = 10 * 60 * 60 * 24 * 365;
const MAX_LIFE = 1000 * 60 * 60 * 24 * 7;

const PEM_HEADER = "-----BEGIN TOKEN-----";
const PEM_FOOTER = "-----END TOKEN-----";

const CONFIG_ROUTE_NAMES = "route names";
const CONFIG_VEHICLE_IDS = "vehicle IDs";
const CONFIG_DRIVER_NAMES = "driver names";
const CONFIG_FILTER_PARAMS = "filter params";

const EARTH_RADIUS_IN_FEET = 20902231;
const FEET_PER_MILE = 5280;

function isObject(obj) {
    return typeof obj === 'object' && obj !== null
}

// returns 0 for Monday...and 6 for Sunday
function getDayOfWeek() {
    if (testDow) {
        return testDow;
    } else return ((new Date()).getDay() + 6) % 7;
}

// 's' is assumed to be a time string like '8:23 am'
function getTimeFromString(s) {
    if (s == null){
        util.log("* Time is null")
        return null;
    }
    var cap = s.match(/([0-9]+):([0-9]+) ([ap]m)/);

    var hour = parseInt(cap[1]);
    var min = parseInt(cap[2]);
    var ampm = cap[3];

    if (ampm === 'pm'){
        // 2:00pm becomes 14:00
        if (hour < 12) hour += 12;
        // note that 12:00pm stays 12:00
    }
    // 12:00am becomes 0:00
    else if (hour === 12) hour = 0;

    var date = new Date();
    date.setHours(hour, min);
    util.log(" - date: " + date);
    return date;
}

// return the difference in minutes between 's' and the current time.
function getTimeDelta(s) {
    var now = new Date();
    if (testHour && testMin) {
        now.setHours(testHour, testMin);
    }
    var routeTime = getTimeFromString(s);
    return Math.abs((routeTime - now) / 60000);
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

    var result = navigator.userAgent.match(/Android/i)
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

    var result = navigator.userAgent.match(/Android/i)
     || navigator.userAgent.match(/iPhone/i);
    util.log("- result: " + result);

    return result;
}

function resizeElement(e) {
    util.log("resizeElement()");
    util.log("- e: " + e);

    if (e) {
        e.style.width = "97%";
        e.style.height = "200px";
        e.style.fontSize = "64px";
    }
}

function resizeElementFont(e) {
    util.log("resizeElementFont()");
    util.log("- e: " + e);

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

    var wakeLockOp = "set";
    var wakeLockOkay = (wakeLockOp != lastWakeLockOp) || (Date.now() - lastWakeLockMillis >= 2500)

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

    var wakeLockOp = "clear";
    var wakeLockOkay = (wakeLockOp != lastWakeLockOp) || (Date.now() - lastWakeLockMillis >= 2500)

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

    var now = Date.now();
    var delta = (now - lastTouch);
    //util.log("- delta: " + delta);

    if (now - lastTouch < 300) {
        quickTouchCount++;
        //util.log("- quickTouchCount: " + quickTouchCount);

        if (quickTouchCount >= 6) {
            var p = document.getElementById('console');
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

    var id = null;
    var pem = null;

    var i1 = str.indexOf(PEM_HEADER);
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

    var p = document.getElementById('keyTextArea');
    var value = p.value.replace(/\n/g, "");
    util.log("- value: " + value);

    var i1 = value.indexOf(PEM_HEADER);
    util.log("- i1: " + i1);

    var i2 = value.indexOf(PEM_FOOTER);
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

    var p = document.getElementById('start-stop');
    var text = p.textContent || p.innerText;
    util.log("- text: " + text);

    if (text == "Load trips") {
        var millis = Date.now();
        loadRoutes();
        util.log("- millis     : " + millis);
        util.log("- startMillis: " + startMillis);
        util.log("+ delta: " + (millis - startMillis));

        if (millis - startMillis >= MAX_LIFE) {
            handleModal("staleModal");
            return;
        }
        var p = document.getElementById('start-stop');
        p.style.background = "#cccccc";

        var p = document.getElementById('config');
        p.style.display = 'block';

        configMatrix.setSelected(CONFIG_ROUTE_NAMES, false);
        configMatrix.setSelected(CONFIG_VEHICLE_IDS, false);
        configMatrix.setSelected(CONFIG_DRIVER_NAMES, false);
    } else {
        clearWakeLock();
        var p = document.getElementById('stats');
        p.style.display = 'none';

        running = false;

        p = document.getElementById('route-select');
        p.value = 'disabled';

        p = document.getElementById('bus-select');
        p.value = 'disabled';

        p = document.getElementById('driver-select');
        p.value = 'disabled';

        p = document.getElementById('okay');
        p.disabled = 'true';
        p.style.background = "#cccccc";

        p = document.getElementById('start-stop');
        p.textContent = 'Load trips';

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
        var p = document.getElementById('okay');

        p.disabled = false;
        p.style.background = "blue";
        p.addEventListener('click', handleOkay);
    }
}

function handleDriverChoice() {
    util.log("handleDriverChoice()");

    configMatrix.setSelected(CONFIG_DRIVER_NAMES, true);
    checkForConfigCompletion();
}

function handleRouteChoice() {
    util.log("handleRouteChoice()");

    configMatrix.setSelected(CONFIG_ROUTE_NAMES, true);
    checkForConfigCompletion();
}

function handleBusChoice() {
    util.log("handleBusChoice()");

    configMatrix.setSelected(CONFIG_VEHICLE_IDS, true);
    checkForConfigCompletion();
}

function handleOkay() {
    util.log("handleOkay()");

    if (window.hasOwnProperty('graasShimVersion') && graasShimVersion.startsWith("android")) {
        fetch('/graas-start').then(function(response) {
            util.log('- response.status: ' + response.status);
            util.log('+ requested graas start');
        });
    }
    var p = document.getElementById('start-stop');
    p.style.background = "green";
    var p = document.getElementById('bus-select');
    vehicleID = p.value
    util.log("- vehicleID: " + vehicleID);

    p = document.getElementById('route-select');
    var entry = tripIDLookup[p.value];

    if (isObject(entry)) {
        tripID = entry['trip_id'];
    } else {
        tripID = entry;
    }

    util.log("- tripID: " + tripID);

    if (configMatrix.getPresent(CONFIG_DRIVER_NAMES) == ConfigMatrix.PRESENT) {
        p = document.getElementById('driver-select');
        driverName = p.value;
        util.log("- driverName: " + driverName);
    }

    var p = document.getElementById('vehicle-id');
    p.innerHTML = "Vehicle ID: " + vehicleID;

    var p = document.getElementById('config');
    p.style.display = "none";

    var p = document.getElementById('stats');
    p.style.display = "block";

    var p = document.getElementById('start-stop');
    p.textContent = "Stop";

    running = true;
    setWakeLock();
}

function getCookie(name) {
    const cookie = document.cookie;
    if (!cookie) return null;

    const list = cookie.split('; ');

    for (var i=0; i<list.length; i++) {
        if (list[i].startsWith(name + '=')) {
            return list[i].split('=')[1];
        }
    }

    return null;
}

function getUUID() {
    var uuid = getCookie(UUID_NAME);

    if (!uuid) {
        uuid = createUUID();
        document.cookie = `${UUID_NAME}=${uuid}; max-age=${MAX_AGE}`;
    }

    return uuid;
}

function getNextMsgNo() {
    var s = getCookie(MSG_NO);

    if (!s) s = '0';

    var n = parseInt(s);
    var nextN = ++n;
    if (nextN < 0) nextN = 0;

    document.cookie = `${MSG_NO}=${nextN}; max-age=${MAX_AGE}`;

    return n;
}

// ### todo: create UUIDs that follow the spec, instead of these
// completely fake ones
function createUUID() {
    var s = ""

    for (var i=0; i<32; i++) {
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

    // For new instances of GRaaS, replace 'graas-resources' with a globally unique directory name:
    url = `https://storage.googleapis.com/graas-resources/gtfs-aux/${agencyID}/${locator}?foo=${arg}`
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
    var opt = document.createElement('option');

    opt.appendChild(document.createTextNode(text));
    opt.disabled = disabled;
    sel.appendChild(opt);
}

function clearSelectOptions(sel) {
    var l = sel.options.length - 1;
    util.log('- l: ' + l);

    for(var i = l; i >= 0; i--) {
        sel.remove(i);
    }
}

function handleEvent(e) {
    util.log('- event.type: ' + event.type);
}

function getRewriteArgs() {
    const s = window.location.search;
    const arg = s.split(/[&\?]/);

    for (var a of arg) {
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
        }
    }
}

function scanQRCode() {
    util.log("scanQRCode()");

    var button = document.getElementById('start-stop');
    button.style.display = 'none';

    var lastResult, countResults = 0;

    var elem = document.getElementById('qr-reader');
    var qw = elem.clientWidth;
    util.log(`- qw: ${qw}`);

    var html5QrcodeScanner = new Html5QrcodeScanner(
        "qr-reader", { fps: 10, qrbox: Math.round(qw * .83), videoConstraints: {facingMode: isMobile() ? "environment" : "user"}});

    function onScanSuccess(qrCodeMessage) {
        if (qrCodeMessage !== lastResult) {
            ++countResults;
            lastResult = qrCodeMessage;
            //resultContainer.innerHTML += `<div>[${countResults}] - ${qrCodeMessage}</div>`;
            util.log(`- qrCodeMessage: '${qrCodeMessage}'`);

            var value = qrCodeMessage.replace(/\n/g, "");
            util.log("- value: " + value);

            var i1 = value.indexOf(PEM_HEADER);
            util.log("- i1: " + i1);

            var i2 = value.indexOf(PEM_FOOTER);
            util.log("- i2: " + i2);

            if (i1 < 0 || i2 < 0) {
                alert("not a valid key");
                return;
            } else {
                localStorage.setItem("lat-long-pem", value);
                initializeCallback(parseAgencyData(value));
                html5QrcodeScanner.clear();
                button.style.display = 'block';

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

    var e = document.getElementById("version");
    version = e.innerHTML;
    var i = version.indexOf(': ');
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

    var dow = getDayOfWeek();
    util.log(`- dow: ${dow}`);

    var delta = getTimeDelta('5:00 pm');
    util.log(`- delta: ${delta}`);

    var feet = getHaversineDistance(38.545697227762936, -121.71125700874212, 38.54566366317534, -121.70840313850653);
    util.log(`- feet: ${feet}`);

    startMillis = Date.now();
    util.log("- startMillis: " + startMillis);

    if (isPhone()) {
        var list = ["start-stop", "route-select", "bus-select", "driver-select", "okay"];
        list.forEach(l => resizeElement(document.getElementById(l)));

        list = ["key-title", "keyTextArea", "key-okay", "stale-title", "stale-okay", "resume"];
        list.forEach(l => resizeElementFont(document.getElementById(l)));
    }

    var str = localStorage.getItem("lat-long-pem") || "";

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

function handleGPSUpdate(position) {
    util.log('handleGPSUpdate()');
    var lat, long, accuracy, posTimestamp, speed, heading;
    var uuid = getUUID();

    if (position) {
        lat = position.coords.latitude;
        long = position.coords.longitude;
        accuracy = position.coords.accuracy;
        posTimestamp = Math.round(position.timestamp / 1000);
        speed = position.coords.speed;
        heading = position.coords.heading;
    } else {
        lat = 0;
        long = 0;
        accuracy = 0;
        posTimestamp = 0;
        speed = 0;
        heading = 0;
    }

    var timestamp = Math.floor(Date.now() / 1000);

    var p = document.getElementById("lat");
    p.innerHTML = "Lat: " + lat;

    p = document.getElementById("long");
    p.innerHTML = "Long: " + long;

    var agent = navigator.userAgent.match(/\(.*?\)/);

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

    var data = {
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
    data['pos-timestamp'] = posTimestamp;

    if (driverName) {
        data['driver-name'] = driverName;
    }

    //var data_str = JSON.stringify(data);
    //util.log('- data_str: ' + data_str);

    util.signAndPost(data, signatureKey, '/new-pos-sig', document);
}

function initializeCallback(agencyData) {
    var pem = agencyData.pem;

    var i1 = pem.indexOf(PEM_HEADER);
    util.log("- i1: " + i1);

    var i2 = pem.indexOf(PEM_FOOTER);
    util.log("- i2: " + i2);

    var b64 = pem.substring(i1 + PEM_HEADER.length, i2);
    util.log("- b64: " + b64);
    util.log("- b64.length: " + b64.length);

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
    util.log("- keyLength: " + keyLength);

    var key = atob(b64);
    util.log("- key.length: " + key.length);

    const binaryDer = util.str2ab(key);

    crypto.subtle.importKey(
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
    ).then(function(key) {
        util.log("- key.type: " + key.type);
        signatureKey = key;

        var str = 'hello ' + Math.floor(Date.now() / 1000);

        util.sign(str, signatureKey).then(function(buf) {
            var sig = btoa(util.ab2str(buf));

            var hello = {
                msg: str,
                sig: sig
            };

            if (agencyData.id) {
                hello.id = agencyData.id;
            }

            util.log("- hello: " + JSON.stringify(hello));

            util.apiCall(hello, '/hello', agencyIDCallback);
        });
    });
}

function agencyIDCallback(response) {
    agencyID = response.agencyID;
    util.log("- agencyID: " + agencyID);

    if (agencyID === 'not found') {
        alert('could not verify client identity');
    } else {
        // ### TODO: write agency ID to local storage if not already present
        var arg = Math.round(Math.random() * 100000)
        util.log("- arg: " + arg);
        getURLContent(agencyID, arg);
    }
}

function getTimeFromName(s) {
    // s looks something like: "Route 10 @ 5:58 am"
    // Returns the text that is to the right of the last '@', which solves for the situation where the route name contains an '@'

    util.log("- s: " + s)
    // if there is no '@', return null
    if (s.indexOf('@') < 0)
    {
        util.log("* Route name is missing an '@', will only show up on dropdown if max-mins-from-start < 0")
        return null
    }
    let timeFromName = s.substring(s.lastIndexOf('@') + 2)
    util.log("- timeFromName: " + timeFromName)
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
    if (name === CONFIG_FILTER_PARAMS) {
        configMatrix.setSelected(CONFIG_FILTER_PARAMS, true);
        if (data != null) {
            isFilterByDayOfWeek = data["is-filter-by-day-of-week"];
            maxMinsFromStart = data["max-mins-from-start"];
            maxFeetFromStop = data["max-feet-from-stop"];
            util.log(`- isFilterByDayOfWeek: ${isFilterByDayOfWeek}`);
            util.log(`- maxMinsFromStart: ${maxMinsFromStart}`);
            util.log(`- maxFeetFromStop: ${maxFeetFromStop}`);
        }
    }
    else if (name === CONFIG_ROUTE_NAMES) {
        trips = data;
        loadRoutes();
    } else if (name == CONFIG_VEHICLE_IDS) {
        vehicleList = data;
        populateList('bus-select', 'Select Bus No.', vehicleList);
    } else if (name == CONFIG_DRIVER_NAMES) {
        driverList = data;
        if (configMatrix.getPresent(CONFIG_DRIVER_NAMES) == ConfigMatrix.PRESENT) {
            populateList('driver-select', 'Select Driver', driverList);
            var p = document.getElementById('driver-select');
            p.style.display = 'block';
        }

    }

    configMatrix.setLoaded(name, true);
    if (configMatrix.isLoaded()) {
        configComplete();
    } else getURLContent(agencyID, arg);
}
function loadRoutes() {
    util.log("- loading routes");
    tripIDLookup = {};

    if (testLat && testLong) {
        util.log(`- testLat: ${testLat}`);
        util.log(`- testLong: ${testLong}`);
        startLat = testLat;
        startLon = testLong;
    }

    for (var i = 0; i < trips.length; i++) {
        util.log(`- trips.length: ${trips.length}`);
        if (Array.isArray(trips)) {
            util.log(`-- trips[i]: ${trips[i]}`);
            const routeInfo = trips[i];
            //util.log(`-- value: ${value}`);

            if (mode === 'vanilla' && isObject(routeInfo)) {
                const time = getTimeFromName(routeInfo["route_name"]);
                //util.log(`- time: ${time}`);
                const dow = getDayOfWeek();
                //util.log(`- dow: ${dow}`);
                const lat = routeInfo.departure_pos.lat;
                util.log(`- lat: ${lat}`);
                const lon = routeInfo.departure_pos.long;
                util.log(`- lon: ${lon}`);

                const timeDelta = getTimeDelta(time);
                util.log(`- timeDelta: ${timeDelta}`);

                const distance = getHaversineDistance(lat, lon, startLat, startLon);
                util.log(`- distance: ${distance}`);
                var cal = 0;
                if (routeInfo.calendar != null) {
                    cal = routeInfo.calendar[dow];
                }
                util.log(`- cal: ${cal}`);
                // 3 conditions need to be met for inclusion...
                if (
                    // 1. meets time parameters
                    (maxMinsFromStart < 0 || (timeDelta != null && timeDelta < maxMinsFromStart))
                    &&
                    // 2. meets day-of-weel parameters:
                    (!isFilterByDayOfWeek || (routeInfo.calendar != null && routeInfo.calendar[dow] === 1))
                    &&
                    // 3. meets distance parameters:
                    (maxFeetFromStop < 0 || getHaversineDistance(lat, lon, startLat, startLon) < maxFeetFromStop)
                    ){
                    //util.log(`+ adding ${key}`);
                    tripIDLookup[routeInfo["route_name"]] = routeInfo;
                }
            } else {
                tripIDLookup[routeInfo["route_name"]] = routeInfo;
            }
        }
    }
    populateRouteList();
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

function populateList(id, str, list) {
    util.log("populateList()");
    util.log("str: " + str);
    var p = document.getElementById(id);
    addSelectOption(p, str, true);

    list.forEach(el => addSelectOption(p, el, false));

    setupListHeader(p);
}

 function populateRouteList() {
    var p = document.getElementById('route-select');
    clearSelectOptions(p);
    addSelectOption(p, "Select Route", true);

    for (const [key, value] of Object.entries(tripIDLookup)) {
        addSelectOption(p, key, !value);
    }

    setupListHeader(p);
}

function setupListHeader(p) {
    p.selectedIndex = 0;
    p.options[0].value = "disabled";
    p.options[0].disabled = true;
}

function configComplete() {
    util.log("configComplete()");

    setInterval(function() {
        if (!running) {
            util.log("checking for updated version..");

            util.timedFetch(window.location, {
                method: 'HEAD',
            })
            .then(function(response) {
                var lm = response.headers.get('Last-Modified');
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
        var uuid = getUUID();

        var p = document.getElementById("uuid");
        p.innerHTML = "UUID: " + uuid;

        gpsInterval(Date.now());
    } else {
        alert('no geolocation access');
    }
}

if (!Object.entries) {
   Object.entries = function( obj ){
      var ownProps = Object.keys( obj ),
         i = ownProps.length,
         resArray = new Array(i); // preallocate the Array

      while (i--)
         resArray[i] = [ownProps[i], obj[ownProps[i]]];
      return resArray;
   };
}

configMatrix = new ConfigMatrix();

// The below files will be processed in the order they appear here. It's important that filter-params goes before route-names
configMatrix.addRow(CONFIG_FILTER_PARAMS, "filter-params.json", ConfigMatrix.UNKNOWN);
configMatrix.addRow(CONFIG_ROUTE_NAMES, "route-names.json", ConfigMatrix.PRESENT);
configMatrix.addRow(CONFIG_VEHICLE_IDS, "vehicle-ids.json", ConfigMatrix.PRESENT);
configMatrix.addRow(CONFIG_DRIVER_NAMES, "driver-names.json", ConfigMatrix.UNKNOWN);
countFiles = configMatrix.countRows();

initialize();