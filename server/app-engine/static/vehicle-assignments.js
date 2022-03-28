const BLOCKS_VOFF = 65;
const BLOCK_HEIGHT = 60;
const VEHICLE_HEIGHT = 30;
const SHADOW_BLUR = 3;
const GAP = 10;
const COLS = 15;
const FONT_SIZE = 20;
const PEM_HEADER = "-----BEGIN TOKEN-----";
const PEM_FOOTER = "-----END TOKEN-----";

const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

var closeButtonData = {x: -1, y: -1, w: 0, h: 0, active:false, item: null};
var longPressTimer = null;
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
var currentToast = null;
var timerID = null;
var signatureKey = null;
var fromDate = null;
var agencyID = '';
var agencyName = '';
var deployedAssignments = [];
var currentAssignments = [];
var VEHICLES_VOFF = 0;
var startTouchX;
var startTouchY;
var lastTouchX;
var lastTouchY;

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

function showToast(text) {
    util.log("showToast()");
    util.log("- text: " + text);

    var toastText = document.getElementById('toast-text');
    toastText.innerHTML = text;

    currentToast = document.getElementById('toast-container');
    currentToast.style.display = "block";

    var that = this;

    timerID = setInterval(function() {
        util.log('toast callback');
        util.log('- that.timerID: ' + that.timerID);
        util.log('- that.toast: ' + that.toast);

        that.currentToast.style.display = "none";
        clearInterval(that.timerID);
        that.repaint();
    }, 2000);
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

function getAssignedVehicle(assignments, blockID) {
    util.log('getAssignedVehicle()');
    util.log('- assignments: ' + assignments);
    util.log('- blockID: ' + blockID);

    for (a of assignments) {
        if (a.block_id === blockID) return a.vehicle_id;
    }

    return null;
}

function getAssignedBlock(assignments, vehicleID) {
    for (a of assignments) {
        if (a.vehicle_id === vehicleID) return a.block_id;
    }

    return null;
}

function layout(blockIDList, vehicleIDList, assignments) {
    util.log('layout()');
    util.log('- blockIDList: ' + blockIDList);
    util.log('- vehicleIDList: ' + vehicleIDList);
    util.log('- assignments: ' + assignments);

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

        var vehicleID = getAssignedVehicle(assignments, blockIDList[i]);

        if (vehicleID) {
            item.status = 'assigned';
            item.vehicle = vehicleID;

            var a = {
                blockID: blockIDList[i],
                vehicleID: vehicleID
            };

            currentAssignments.push(a);
        }

        items.push(item);

        xx += GAP + bw;

        if (xx >= window.innerWidth) {
            xx = GAP;
            yy += GAP + bh;
        }
    }

    for (var a of currentAssignments) {
        deployedAssignments.push(a);
    }

    deployedAssignments.sort((a, b) => {return a.blockID.localeCompare(b.blockID);});
    updateDeploymentIndicator();

    bh = VEHICLE_HEIGHT;
    xx = GAP;


    yy += 3 * BLOCK_HEIGHT;
    VEHICLES_VOFF = yy;

    for (var i=0; i<vehicleIDList.length; i++) {
        var blockID = getAssignedBlock(assignments, vehicleIDList[i]);

        var item = {
            type: 'vehicle',
            x: xx,
            y: yy,
            w: bw,
            h: bh,
            label: vehicleIDList[i],
            //status: Math.random() < .15 ? 'inactive' : 'active'
            status: blockID ? 'assigned' : 'active'
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
    canvas.height = window.innerHeight * .9125;

    repaint();

    var str = localStorage.getItem("app-data") || "";

    if (!str) {
        if (!isMobile()) {
            // ios WKWebView doesn't support camera access :[
            handleModal("keyEntryModal");
        } else {
            // ### TODO add QR code scanning once
            // we're past the initial implementation
            //scanQRCode();
            handleModal("keyEntryModal");
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

async function handleDeploy(data) {
    handleModal('infiniteProgressModal');
    var json = await util.getJSONResponse('/block-collection', data, signatureKey);
    util.log('- json: ' + JSON.stringify(json));
    util.log('- json.status: ' + json);

    var status = 'failed';
    if (json && json.status) status = json.status;

    var elem = document.getElementById('key-confirm-text');
    elem.innerHTML = 'Deploy status: ' + status;

    if (status === 'ok') {
        deployedAssignments = []

        for (var ca of currentAssignments) {
            var a = {
                blockID: ca.blockID,
                vehicleID: ca.vehicleID
            };

            deployedAssignments.push(a);
        }

        updateDeploymentIndicator();
    }

    dismissModal();
    handleModal('confirmationModal');
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

        if (i1 === 0) {
            alert('missing agency id');
            return;
        } else if (i1 < 0 || i2 < 0) {
            alert('not a valid key');
            return;
        }

        dismissModal();
        p.value = "";

        localStorage.setItem('app-data', value);
        completeInitialization(parseAgencyData(value));
    } else if (id === 'key-deploy') {
        var blockData = [];
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
                data['vehicle_id'] = vid;
                data['trips'] = block.trips;

                blockData.push(data);
            }
        }

        var data = {
            'agency_id': agencyID,
            'valid_from': Math.floor(fromDate.getTime() / 1000),
            'valid_to': Math.floor(toDate.getTime() / 1000),
            'blocks': blockData
        };
        util.log('- data: ' + JSON.stringify(data));

        handleDeploy(data);
    } else if (id === 'key-select-today') {
        fromDate = util.getMidnightDate();
        var str = util.getYYYYMMDD(fromDate);
        util.log('- str: ' + str);

        dismissModal();
        loadBlockData(str);
    } else if (id === 'key-select-tomorrow') {
        fromDate = util.nextDay(util.getMidnightDate());
        var str = util.getYYYYMMDD(fromDate);
        util.log('- str: ' + str);

        dismissModal();
        loadBlockData(str);
    } else if (id === 'key-confirm-okay') {
        dismissModal();
    }
}

async function completeInitialization(agencyData) {
    agencyID = agencyData.id;
    util.log("- agencyID: " + agencyID);
    agencyName = getDisplayName(agencyID);

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

    addEventListener('beforeunload', (event) => {
        util.log('beforeunload callback');
    });

    canvas.addEventListener('touchstart', handleTouchStart);
    canvas.addEventListener('touchmove', handleTouchMove);
    canvas.addEventListener('touchcancel', handleTouchCancel);
    canvas.addEventListener('touchend', handleTouchEnd);

    canvas.addEventListener('mousedown', handleMouseDown);
    canvas.addEventListener('mouseup', handleMouseUp);
    canvas.addEventListener('mousemove', handleMouseMove);

    handleModal('dateSelectModal');
}

function handleTouchStart(e) {
    util.log('* handleTouchStart() *');

    e.preventDefault();

    /* ### execute only first time around
    const sound = new Audio('path/to/your/sound/notification.mp3');

    sound.play();
    sound.pause();
    sound.currentTime = 0;

    */

    /*util.log('- e.touches.length: ' + e.touches.length);
    util.log('- e.touches[0]: ' + JSON.stringify(e.touches[0]));
    util.log('- e.touches[0].clientX: ' + e.touches[0].clientX);
    util.log('- e.touches[0].clientY: ' + e.touches[0].clientY);*/

    startTouchX = e.touches[0].clientX;
    startTouchY = e.touches[0].clientY;

    longPressTimer = setInterval(longPress, 2000, startTouchX, startTouchY);
    util.log('- longPressTimer: ' + longPressTimer);

    handleMouseDown({x: startTouchX, y: startTouchY})
}

function handleTouchMove(e) {
    //util.log('* handleTouchMove() *');

    //e.preventDefault();

    /*util.log('- e.touches.length: ' + e.touches.length);
    util.log('- e.touches[0]: ' + JSON.stringify(e.touches[0]));
    util.log('- e.touches[0].clientX: ' + e.touches[0].clientX);
    util.log('- e.touches[0].clientY: ' + e.touches[0].clientY);*/

    if (longPressTimer) {
        clearInterval(longPressTimer);
        longPressTimer= null;
    }

    lastTouchX = e.touches[0].clientX;
    lastTouchY = e.touches[0].clientY;

    handleMouseMove({x: lastTouchX, y: lastTouchY})
}

function handleTouchEnd(e) {
    util.log('* handleTouchEnd() *');

    //e.preventDefault();
    handleMouseUp({x: lastTouchX, y: lastTouchY})
}

function handleTouchCancel(e) {
    util.log('* handleTouchCancel() *');

    e.preventDefault();

    util.log('- e.touches.length: ' + e.touches.length);
    util.log('- e.touches[0]: ' + JSON.stringify(e.touches[0]));
    util.log('- e.touches[0].clientX: ' + e.touches[0].clientX);
    util.log('- e.touches[0].clientY: ' + e.touches[0].clientY);
}

function handleUnload(e) {
    util.log('* handleUnload() *');
    util.log('- e: ' + JSON.stringify(e));

    currentAssignments.sort((a, b) => {return a.blockID.localeCompare(b.blockID);});

    if (JSON.stringify(currentAssignments) === JSON.stringify(deployedAssignments)) {
        util.log('+ no unsaved changes');

        delete e['returnValue'];
    } else {
        util.log('+ unsaved changes');
        util.log('- currentAssignments: ' + JSON.stringify(currentAssignments));
        util.log('- deployedAssignments: ' + JSON.stringify(deployedAssignments));

        e.preventDefault();
        e.returnValue = '';
    }
}

async function loadBlockData(dateString) {
    util.log('loadBlockData()');
    util.log('- dateString: ' + dateString);

    handleModal('infiniteProgressModal');
    var name = `blocks-${dateString}.json`;
    //util.log('- name: ' + name);
    var blocks = await getGithubData(agencyID, name);
    //util.log("- blocks: " + JSON.stringify(blocks));

    var bidList = [];

    for (var block of blocks) {
        bidList.push(block.id);
        blockMap[block.id] = block;
    }

    util.log('- bidList: ' + bidList);

    var vidList = await getGithubData(agencyID, 'vehicle-ids.json');
    util.log('- vidList: ' + vidList);

    var data = {
        agency_id: agencyID,
        date: dateString
    };
    var json = await util.getJSONResponse('/get-assignments', data, signatureKey);
    util.log('- json: ' + json);
    var assignments = json.assignments;

    layout(bidList, vidList, assignments);

    dismissModal();
    var deployButton = document.getElementById('key-deploy');
    deployButton.style.display = 'inline-block';
}

function getGithubData(agencyID, filename) {
    //util.log('getGithubData()');

    var arg = Math.round(Math.random() * 100000)
    //util.log("- arg: " + arg);


    // ### FIXME: replace with GH repo base URL, still using bucket URL for
    // now to break must-test-before-checkin, can't-test-before-checkin catch 22
    //var url = `https://raw.githubusercontent.com/cal-itp/graas/main/server/agency-config/gtfs/gtfs-aux/${agencyID}/${filename}?foo=${arg}`;
    var url = `https://storage.googleapis.com/graas-resources/gtfs-aux/${agencyID}/${filename}?foo=${arg}`;
    return util.getJSONResponse(url);
}

function updateDeploymentIndicator() {
    util.log('updateDeploymentIndicator()');
    var button = document.getElementById('key-deploy');
    var text = document.getElementById('text-deployment-status');

    currentAssignments.sort((a, b) => {return a.blockID.localeCompare(b.blockID);});

    util.log('- currentAssignments: ' + JSON.stringify(currentAssignments));
    util.log('- deployedAssignments: ' + JSON.stringify(deployedAssignments));

    if (JSON.stringify(currentAssignments) === JSON.stringify(deployedAssignments)) {
        util.log('+ no unsaved changes');
        button.disabled = true;
        text.innerHTML = 'All changes are saved.';
    } else {
        util.log('+ unsaved changes');
        button.disabled = false;
        text.innerHTML = '';
    }
}

function handleCloseButtonPress() {
    util.log('handleCloseButtonPress()');
    util.log('- closeButtonData.item: ' + JSON.stringify(closeButtonData.item));

    var blockID = null;

    if (closeButtonData.item.type === 'vehicle') {
        for (var item of items) {
            if (item.vehicle === closeButtonData.item.label) {
                util.log('-- item: ' + JSON.stringify(item));
                item.vehicle = null;
                item.status = 'unassigned';
                blockID = item.label;
                repaint();

                break;
            }
        }

        for (var i=currentAssignments.length-1; i>=0; i--) {
            if (currentAssignments[i].vehicleID === closeButtonData.item.label) {
                currentAssignments.splice(i, 1);
                break;
            }
        }

        updateDeploymentIndicator();
        closeButtonData.item.status = 'active';
    }

    if (closeButtonData.item.type === 'block') {
        for (var item of items) {
            if (item.label === closeButtonData.item.vehicle) {
                util.log('-- item: ' + JSON.stringify(item));
                item.status = 'active';
                blockID = closeButtonData.item.label;
                repaint();

                break;
            }
        }

        for (var i=currentAssignments.length-1; i>=0; i--) {
            if (currentAssignments[i].blockID === closeButtonData.item.label) {
                currentAssignments.splice(i, 1);
                break;
            }
        }

        updateDeploymentIndicator();
        closeButtonData.item.status = 'unassigned';
        closeButtonData.item.vehicle = null;
    }

    closeButtonData.x = -1;
    closeButtonData.y = -1;
    closeButtonData.w = 0;
    closeButtonData.h = 0;
    closeButtonData.active = false;
    closeButtonData.item = null;

    if (blockID) {
        showToast(`unassigned block '${blockID}'`);

        for (var i=currentAssignments.length-1; i>=0; i--) {
            if (currentAssignments[i].blockID === blockID) {
                currentAssignments.splice(i, 1);
                break;
            }
        }

        updateDeploymentIndicator();
    }
}

function playSound(url) {
  const audio = new Audio(url);
  audio.play();
}

function getItemAt(x, y) {
    for (item of items) {
        if (x >= item.x && x < item.x + item.w && y >= item.y && y < item.y + item.h) {
            return item;
        }
    }

    return null;
}

function longPress(x, y) {
    util.log('longPress()');
    util.log('- x: ' + x);
    util.log('- y: ' + y);

    if (longPressTimer) {
        clearInterval(longPressTimer);
        longPressTimer = null;
    }

    var pressItem = getItemAt(x, y);
    util.log('- pressItem: ' + pressItem);
    if (!pressItem) return;

    util.log('- navigator.vibrate: ' + navigator.vibrate);

    if (navigator && navigator.vibrate) {
        util.log('+ vibrate');
        navigator.vibrate(1000);
    }

    //playSound('vibrate.mp3');

    var blockID = null;

    if (pressItem.type === 'vehicle') {
        if (pressItem.status == 'assigned') {
            for (var item of items) {
                if (item.vehicle === pressItem.label) {
                    util.log('-- item: ' + JSON.stringify(item));
                    item.vehicle = null;
                    item.status = 'unassigned';
                    blockID = item.label;

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
                blockID = pressItem.label;

                break;
            }
        }

        pressItem.status = 'unassigned';
        pressItem.vehicle = null;
        repaint();
    }

    if (blockID) {
        showToast(`unassigned block '${blockID}'`);

        for (var i=currentAssignments.length-1; i>=0; i--) {
            if (currentAssignments[i].blockID === blockID) {
                currentAssignments.splice(i, 1);
                break;
            }
        }

        updateDeploymentIndicator();
    }
}

function handleMouseDown(e) {
    util.log('handleMouseDown()');
    util.log('- e: ' + JSON.stringify(e));

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
    util.log('- e.x: ' + e.x);
    util.log('- e.y: ' + e.y);
    util.log('- dragReceiver: ' + JSON.stringify(dragReceiver));

    if (dragReceiver !== null) {
        dragReceiver.vehicle = dragItem.label;
        dragReceiver.status = 'assigned';

        var a = {
            blockID: dragReceiver.label,
            vehicleID: dragItem.label
        };

        currentAssignments.push(a);
        updateDeploymentIndicator();

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

    var savedShadowBlur = ctx.shadowBlur;
    //ctx.shadowBlur = SHADOW_BLUR;

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
    ctx.lineWidth = 1;

    //ctx.shadowColor = null;
    //ctx.shadowBlur = 0;
}

function handleMouseMove(e) {
    //util.log('handleMouseMove()');

    var x = e.x;
    var y = e.y;

    if (dragging && dragItem !== null) {
        //util.log('- x: ' + x);
        //util.log('- y: ' + y);

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

            var divider = navigator.maxTouchPoints > 0 ? 1.5 : 2
            drawWidget(dragItem, e.x - dragItem.w / divider, e.y - dragItem.h / divider);
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

function fillRoundedRect(ctx, x, y, width, height, radius) {
    var savedShadowBlur = ctx.shadowBlur;
    ctx.shadowColor = 'gray';
    ctx.shadowBlur = SHADOW_BLUR;

    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.lineTo(x + width - radius, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
    ctx.lineTo(x + width, y + height - radius);
    ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
    ctx.lineTo(x + radius, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
    ctx.closePath();
    ctx.fill();

    ctx.shadowColor = null;
    ctx.shadowBlur = 0;
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

    if (item.type === 'block') {
        ctx.fillStyle = 'aliceBlue';
        ctx.fillRect(x, y, item.w, item.h);
        ctx.fillStyle = 'black';
    } else {
        ctx.fillStyle = color;
        fillRoundedRect(ctx, x, y, item.w, item.h, 4);
        ctx.fillStyle = 'white';
    }

    ctx.textAlign = 'center';
    ctx.fillText(item.label, x + item.w / 2, y + FONT_SIZE * .8);

    if (item.type === 'block' && item.vehicle !== null) {
        ctx.fillStyle = '#3c3';
        ctx.fillRect(x, y + item.h / 2, item.w, item.h / 2);

        ctx.fillStyle = 'white';
        ctx.textAlign = 'right';

        //log('- item.vehicle: ' + JSON.stringify(item.vehicle));
        ctx.fillText(item.vehicle, x + item.w - 5, y + item.h - FONT_SIZE * .7);
    }
}

function getDisplayName(s) {
    var capitalize = true;
    var r = '';

    for (var i=0; i<s.length; i++) {
        var c = s.charAt(i);

        if (capitalize) {
            c = c.toUpperCase();
            capitalize = false;
        }

        if (c === '_') {
            capitalize = true;
            c = ' ';
        }

        r += c;
    }

    return r;
}

function repaint() {
    //log('repaint()');

    ctx.shadowOffsetX = 0;
    ctx.shadowOffsetY = 0;

    ctx.fillStyle = 'white';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.shadowColor = 'rgba(0,0,0,0)';

    ctx.font = `${FONT_SIZE}px arial`;
    ctx.fillStyle = 'black';
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'center';

    ctx.fillText(agencyName + ' GTFS Blocks For ' + util.getShortDate(fromDate), window.innerWidth / 2,  BLOCKS_VOFF - FONT_SIZE * 1.3);
    if (VEHICLES_VOFF > 0) ctx.fillText('Vehicles', window.innerWidth / 2, VEHICLES_VOFF - FONT_SIZE * 1.3);


    ctx.font = `${FONT_SIZE}px arial`;
    ctx.shadowOffsetX = 1;
    ctx.shadowOffsetY = 1;

    for (var item of items) {
        drawWidget(item);
    }
}

initialize();
