const BLOCKS_VOFF = 65;
const VEHICLES_VOFF = 425;
const BLOCK_HEIGHT = 60;
const VEHICLE_HEIGHT = 30;
const SHADOW_BLUR = 5;
const GAP = 10;
const COLS = 10;
const FONT_SIZE = 22;
const PEM_HEADER = "-----BEGIN TOKEN-----";
const PEM_FOOTER = "-----END TOKEN-----";

const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

var closeButtonData = {x: -1, y: -1, w: 0, h: 0, active:false, item: null};
var pressTimer = null;
var pressItem = null;
var dragging = false;
var dragItem = null;
var dragReceiver = null;
var dragWidth = 0;
var dragHeight = 0;
var dragColor = '';
var lastRefresh = 0;
var items = [];
var blockMap = {};
var currentModal = null;
var signatureKey = null;

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

function layout(blockIDList, vehicleIDList) {
    items = [];

    var bw = (window.innerWidth - (COLS + 1) * GAP) / COLS;
    var bh = BLOCK_HEIGHT;
    var xx = GAP;
    var yy = BLOCKS_VOFF;

    for (var i=0; i<blockIDList.length; i++) {
        var item = {
            type: 'block',
            x: xx,
            y: yy,
            w: bw,
            h: bh,
            label: blockIDList[i],
            status: 'unassigned',
            vehicle: null
        };

        items.push(item);

        xx += GAP + bw;

        if (xx >= window.innerWidth) {
            xx = GAP;
            yy += GAP + bh;
        }
    }

    bh = VEHICLE_HEIGHT;
    xx = GAP;
    yy = VEHICLES_VOFF;

    for (var i=0; i<vehicleIDList.length; i++) {
        var item = {
            type: 'vehicle',
            x: xx,
            y: yy,
            w: bw,
            h: bh,
            label: vehicleIDList[i],
            status: Math.random() < .15 ? 'inactive' : 'active'
        };

        items.push(item);

        xx += GAP + bw;

        if (xx >= window.innerWidth) {
            xx = GAP;
            yy += GAP + bh;
        }
    }

    repaint();
}

function initialize() {
    //window.addEventListener('resize', resizeCanvas, false);

    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight * .9;

    repaint();

    var date = util.getDate('03/02/22');
    util.log('- date: ' + date.toString());
    var seconds = util.millisToSeconds(date.getTime());
    util.log('- seconds: ' + seconds);

    var today = new Date();
    util.log('- today: ' + today);
    var midnight = util.getMidnightDate(today);
    util.log('- midnight: ' + midnight);
    var tomorrow = util.nextDay(midnight);
    util.log('- tomorrow: ' + tomorrow);

    var str = localStorage.getItem("app-data") || "";

    if (!str) {
        if (!isMobile()) {
            // ios WKWebView doesn't support camera access :[
            handleModal("keyEntryModal");
        } else {
            util.log('ADD QR CODE SCANNING!');
            //scanQRCode();
        }
    } else {
        completeInitialization(parseAgencyData(str));
    }
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
    }

    return {
        id: id,
        pem: pem
    };
}

function handleKey(id) {
    util.log('handleKey()');
    util.log('- id: ' + id);

    if (id === 'key-okay') {
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

        localStorage.setItem("app-data", value);
        completeInitialization(parseAgencyData(value));
    } else if (id === 'key-deploy-tomorrow' || id == 'key-deploy-today') {
        var blockData = [];

        var fromDate = new Date();

        if (id === 'key-deploy-tomorrow') {
            fromDate = util.nextDay(fromDate);
        }

        fromDate = util.getMidnightDate(fromDate);
        var toDate = util.nextDay(fromDate);

        util.log('- fromDate: ' + fromDate);
        util.log('- toDate  : ' + toDate);

        for (var item of items) {
            if (item.type === 'block' && item.status === 'assigned') {
                var id = item.label;
                util.log('-- id: ' + id);
                var vid = item.vehicle;
                util.log('-- vid: ' + vid);
                var block = blockMap[id];
                //util.log('-- block: ' + JSON.stringify(block));

                var data = {};

                data['id'] = id;
                data['agency_id'] = agencyID;
                data['vehicle_id'] = vid;
                data['valid_from'] = util.millisToSeconds(fromDate.getTime());
                data['valid_to'] = util.millisToSeconds(toDate.getTime());
                data['trips'] = block.trips;

                blockData.push(data);
            }
        }

        var data = {
            'agency-id': agencyID,
            'block-data': blockData
        };

        util.log('- data: ' + JSON.stringify(data));
        util.log('### FIXME: actually send data');

        //util.signAndPost(data, signatureKey, '/block-data-update', document);
    }
}

async function completeInitialization(agencyData) {
    agencyID = agencyData.id;
    util.log("- agencyID: " + agencyID);

    var pem = agencyData.pem;

    var i1 = pem.indexOf(PEM_HEADER);
    util.log("- i1: " + i1);

    var i2 = pem.indexOf(PEM_FOOTER);
    util.log("- i2: " + i2);

    var b64 = pem.substring(i1 + PEM_HEADER.length, i2);
    // util.log("- b64: " + b64);

    keyType = "ECDSA";
    keyLength = 256;

    var key = atob(b64);
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

    document.body.addEventListener('mousedown', handleMouseDown);
    document.body.addEventListener('mouseup', handleMouseUp);
    document.body.addEventListener('mousemove', handleMouseMove);

    var blocks = await getBucketData(agencyID, 'blocks.json');
    //util.log("- blocks: " + JSON.stringify(blocks));

    var bidList = [];

    for (var block of blocks) {
        bidList.push(block.id);
        blockMap[block.id] = block;
    }

    util.log('- bidList: ' + bidList);

    var vidList = await getBucketData(agencyID, 'vehicle-ids.json');
    util.log('- vidList: ' + vidList);

    layout(bidList, vidList);
}

function getBucketData(agencyID, filename) {
    //util.log('getBucketData()');

    var arg = Math.round(Math.random() * 100000)
    //util.log("- arg: " + arg);


    var url = `https://storage.googleapis.com/graas-resources/gtfs-aux/${agencyID}/${filename}?foo=${arg}`;
    return util.getJSONResponse(url);
}

function handleCloseButtonPress() {
    util.log('handleCloseButtonPress()');
    util.log('- closeButtonData.item: ' + JSON.stringify(closeButtonData.item));

    if (closeButtonData.item.type === 'vehicle') {
        for (var item of items) {
            if (item.vehicle === closeButtonData.item.label) {
                util.log('-- item: ' + JSON.stringify(item));
                item.vehicle = null;
                item.status = 'unassigned';
                repaint();

                break;
            }
        }

        closeButtonData.item.status = 'active';
    }

    if (closeButtonData.item.type === 'block') {
        for (var item of items) {
            if (item.label === closeButtonData.item.vehicle) {
                util.log('-- item: ' + JSON.stringify(item));
                item.status = 'active';
                repaint();

                break;
            }
        }

        closeButtonData.item.status = 'unassigned';
        closeButtonData.item.vehicle = null;
    }

    closeButtonData.x = -1;
    closeButtonData.y = -1;
    closeButtonData.w = 0;
    closeButtonData.h = 0;
    closeButtonData.active = false;
    closeButtonData.item = null;
}

/*
mobile version todos:
- disable default long press: https://stackoverflow.com/questions/12304012/preventing-default-context-menu-on-longpress-longclick-in-mobile-safari-ipad
- if runnning on mobile, start 2s timer on mousedown: setInterval(), clearInterval()
- if dragging or short press, cancel timer
- when timer expires, cancel timer, play long-press sound and call longPress()
*/
function longPress() {
    clearInterval(pressTimer);
    pressTimer = null;

    if (pressItem.type === 'vehicle') {
        if (pressItem.status == 'inactive') {
            pressItem.status = 'active';
        } else if (pressItem.status == 'active') {
            pressItem.status = 'inactive';
        } else if (pressItem.status == 'assigned') {
            for (var item of items) {
                if (item.vehicle === pressItem.label) {
                    util.log('-- item: ' + JSON.stringify(item));
                    item.vehicle = null;
                    item.status = 'unassigned';

                    break;
                }
            }

            pressItem.status = 'active';
        }

        repaint();
    } else if (pressItem.type === 'block' && pressItem.status === 'assigned') {
        for (var item of items) {
            if (item.label === pressItem.vehicle) {
                util.log('-- item: ' + JSON.stringify(item));
                item.status = 'active';

                break;
            }
        }

        pressItem.status = 'unassigned';
        pressItem.vehicle = null;
        repaint();
    }

    pressItem = null;
}

function handleMouseDown(e) {
    util.log('handleMouseDown()');

    var x = e.x;
    var y = e.y;

    util.log('- x: ' + x);
    util.log('- y: ' + y);

    util.log('- closeButtonData: ' + JSON.stringify(closeButtonData));

    if (x >= closeButtonData.x && x < closeButtonData.x + closeButtonData.w && y >= closeButtonData.y && y < closeButtonData.y + closeButtonData.h) {
        handleCloseButtonPress();
        return;
    }

    dragging = 'true'
    dragItem = null;

    util.log('- x: ' + x);
    util.log('- y: ' + y);

    for (var item of items) {
        //log('- item: ' + JSON.stringify(item));

        if (item.type === 'vehicle' && item.status === 'active'
            && x >= item.x && x < item.x + item.w && y >= item.y && y < item.y + item.h)
        {
            dragItem = item;
            //log('- dragItem: ' + dragItem);
            repaint();
            break;
        }
    }
}

function handleMouseUp(e) {
    util.log('handleMouseUp()');
    util.log('- dragReceiver: ' + JSON.stringify(dragReceiver));

    if (dragReceiver !== null) {
        dragReceiver.vehicle = dragItem.label;
        dragReceiver.status = 'assigned';

        if (dragItem !== null) {
            dragItem.status = 'assigned';
        }
    }

    dragReceiver = null;
    dragging = false;
    dragItem = null;

    repaint();
}

function drawCloseButton(item) {
    repaint();

    var xx = item.x + item.w;
    var yy = item.y + (item.type === 'block' ? item.h / 2 : 0);
    var radius = 10;
    var len = Math.floor(radius * .4);

    closeButtonData.x = xx - radius;
    closeButtonData.y = yy - radius;
    closeButtonData.w = 2 * radius;
    closeButtonData.h = 2 * radius;

    ctx.shadowBlur = 0;

    ctx.fillStyle = 'black';
    ctx.beginPath();
    ctx.ellipse(xx, yy, radius, radius, 0, 0, 2 * Math.PI);
    ctx.fill();

    ctx.strokeStyle = 'white';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(xx - len, yy + len);
    ctx.lineTo(xx + len, yy - len);
    ctx.moveTo(xx - len, yy - len);
    ctx.lineTo(xx + len, yy + len);
    ctx.stroke();

    ctx.shadowBlur = SHADOW_BLUR;
}

function handleMouseMove(e) {
    //log('handleMouseMove()');

    var x = e.x;
    var y = e.y;

    if (dragging && dragItem !== null) {
        //log('+ dragItem: ' + dragItem);
        var millis = (new Date).getTime();

        if (millis - lastRefresh >= 30) {
            lastRefresh = millis;
            repaint();

            for (var item of items) {
                if (item.type === 'block' && item.status === 'unassigned'
                    && x >= item.x && x < item.x + item.w && y >= item.y && y < item.y + item.h)
                {
                    //log('-- item: ' + JSON.stringify(item));
                    dragReceiver = item;

                    ctx.strokeStyle = '#c33';
                    var lw = ctx.lineWidth;
                    ctx.lineWidth = 4;
                    var skirt = 10;
                    ctx.strokeRect(item.x - skirt, item.y - skirt, item.w + 2 * skirt, item.h + 2 * skirt);
                    ctx.lineWidth = lw;
                    break;
                } else {
                    dragReceiver = null;
                }
            }

            //ctx.fillStyle = 'white';
            //ctx.fillText(dragItem.label, e.x, e.y);

            drawWidget(dragItem, e.x - dragItem.w / 2, e.y - dragItem.h / 2);
        }
    } else {
        var millis = (new Date).getTime();

        if (millis - lastRefresh >= 30) {
            lastRefresh = millis;
            var match = false;
            var skirt = 7;

            for (var item of items) {
                if ((item.type === 'block' || item.type === 'vehicle') && item.status === 'assigned'
                    && x >= item.x - skirt && x < item.x + item.w  + skirt && y >= item.y - skirt && y < item.y + item.h + skirt)
                {
                    repaint();
                    drawCloseButton(item);

                    closeButtonData.active = true;
                    closeButtonData.item = item;
                    match = true;
                    break;
                }
            }

            if (!match) {
                if (closeButtonData.active) {
                    repaint();
                }

                closeButtonData.active = false;
                closeButtonData.item = null;
            }
        }
    }
}

function drawWidget(item, xx = -1, yy = -1) {
    var color = '#aab';

    var x = xx < 0 ? item.x : xx;
    var y = yy < 0 ? item.y : yy;

    if (item.status === 'active') {
        color = '#c33';
    } else if (item.type === 'vehicle' && item.status === 'assigned') {
        color = '#3c3';
    }

    ctx.fillStyle = color;
    ctx.fillRect(x, y, item.w, item.h);

    ctx.fillStyle = 'white';
    ctx.textAlign = 'center';
    ctx.fillText(item.label, x + item.w / 2, y + FONT_SIZE * .7);

    if (item.type === 'block' && item.vehicle !== null) {
        ctx.fillStyle = '#3c3';
        ctx.fillRect(x, y + item.h / 2, item.w, item.h / 2);

        ctx.fillStyle = 'white';
        ctx.textAlign = 'right';

        //log('- item.vehicle: ' + JSON.stringify(item.vehicle));
        ctx.fillText(item.vehicle, x + item.w - 5, y + item.h - FONT_SIZE * .6);
    }
}

function repaint() {
    //log('repaint()');

    ctx.fillStyle = '#ccd';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.shadowColor = 'black';
    ctx.shadowBlur = SHADOW_BLUR;

    ctx.font = `bold ${FONT_SIZE}px arial`;
    ctx.fillStyle = 'white';
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'center';

    ctx.fillText('GTFS BLOCKS', window.innerWidth / 2,  BLOCKS_VOFF - FONT_SIZE * 1.3);
    ctx.fillText('VEHICLES', window.innerWidth / 2, VEHICLES_VOFF - FONT_SIZE * 1.3);


    for (var item of items) {
        drawWidget(item);
    }
}

initialize();
