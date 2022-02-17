var scaleRatio;
const trips = new Map();
const agencies = new Map();
var mapCanvas
var mapCtx;
var timelineCanvas;
var timelineCtx
const img = new Image();
var canvasWidth;
var windowHeight;
var timelineHeight;
var imageWidth;
var imageHeight;
var timelineCanvasHeight;
var mapCanvasHeight;
var selectedAgency;
var selectedDate;
var dropdownHeight
var font_size
const bucketURL = "https://storage.googleapis.com/graas-resources/"
const MIN_HEIGHT = 1000;
const font = "Arial";
const tooltipItems = ["trip_id", "vehicle_id", "agent", "device",
                     "os", "uuid_tail", "avg_update_interval",
                     "max_update_interval", "min_update_interval"];

function load(){

    if(trips.size == 0) {
        setTimeout(load, 500);
    }

    canvasWidth = window.innerWidth;
    windowHeight = window.innerHeight;

    mapCanvas = document.getElementById('maps');
    mapCtx = mapCanvas.getContext('2d');
    mapCtx.canvas.width  = canvasWidth;

    timelineCanvas = document.getElementById('timeline');
    timelineCtx = timelineCanvas.getContext('2d');
    timelineCtx.canvas.width  = canvasWidth;

    var dropdowns = document.getElementById('dropdowns');
    dropdownHeight = dropdowns.offsetHeight;

    img.src = bucketURL + "graas-report-archive/" + selectedAgency + "/" + selectedAgency + "-" + selectedDate + "-dev.png?nocache=123";

    img.onload = function() {
        imageWidth = this.naturalWidth
        imageHeight = this.naturalHeight;
        scaleRatio = window.innerWidth / imageWidth;
        console.log("scaleRatio: " + scaleRatio);
        timelineCanvasHeight = timelineHeight * scaleRatio;
        mapCanvasHeight = (imageHeight - timelineHeight) * scaleRatio;
        canvasHeight = imageHeight * scaleRatio;
        mapCtx.canvas.height = mapCanvasHeight;
        drawReport();
    };

    // Add event listener for `click` events.
    document.body.addEventListener('click', function(event) {
        var x = event.pageX;
        var y = event.pageY;
        // console.log("x:" + x);
        // console.log("y:" + y);

        var scrollTop = document.documentElement.scrollTop;
        var foundTrip = false;
        if(y >=  (scrollTop + timelineCanvasHeight)){
            for (const [key, value] of trips.entries()) {
                if (mapContainsPoint(value, x, y - timelineCanvasHeight)){
                    selectTrip(value);
                    drawToolTip(value);
                    foundTrip = true;
                    break;
                }
            }
        } else {
            for (const [key, value] of trips.entries()) {
                if (timelineContainsPoint(value, x, y - scrollTop - dropdownHeight)){
                    selectTrip(value);
                    drawToolTip(value);
                    foundTrip = true;
                    break;
                }
            }
        }
        if (!foundTrip){
            // Since we can't simply remove rectangle, reload the map
            drawReport()
        }
    }, false);
}

function drawReport(){
    console.log("Drawing map...");
    timelineCtx.drawImage(img, 0, 0, imageWidth, timelineHeight, 0, 0, canvasWidth, timelineCanvasHeight);
    mapCtx.drawImage(img, 0, timelineHeight, imageWidth, imageHeight - timelineHeight, 0, 0, canvasWidth, mapCanvasHeight);
}

function loadDropdownJSON(){
    // ?nocache= prevents annoying json caching...mostly for debugging purposes
    fetch(bucketURL + "web/graas-report-agency-dates.json?nocache="  + (new Date()).getTime())
    .then(response => {
       return response.json();
    })
    .then(jsondata => processDropdownJSON(jsondata))
}

function loadTripJSON(){
    // ?nocache= prevents annoying json caching...mostly for debugging purposes
    fetch(bucketURL + "graas-report-archive/" + selectedAgency + "/" + selectedAgency + "-" + selectedDate + ".json?nocache=" + (new Date()).getTime())
    .then(response => {
       return response.json();
    })
    .then(jsondata => processTripJSON(jsondata))
}

function processDropdownJSON(object){

    var p = document.getElementById("agency-select");
    clearSelectOptions(p);
    var opt = document.createElement('option');
    opt.appendChild(document.createTextNode("Select agency-id..."));
    p.appendChild(opt);

    for (var key in object) {
        agencies.set(key, object[key])
        var opt = document.createElement('option');
        opt.appendChild(document.createTextNode(key));
        p.appendChild(opt);
    }
}

function handleAgencyChoice(){
    console.log("handleAgencyChoice()");
    selectedAgency = document.getElementById("agency-select").value;
    var p = document.getElementById("date-select");
    clearSelectOptions(p);

    for (var i = agencies.get(selectedAgency).length -1; i >= 0; i--) {
        var opt = document.createElement('option');
        opt.appendChild(document.createTextNode(agencies.get(selectedAgency)[i]));
        p.appendChild(opt);
    }
    handleDateChoice();
}

function handleDateChoice(){
    console.log("handleDateChoice()");
    selectedDate = document.getElementById("date-select").value;
    loadTripJSON();
    load();
}
function processTripJSON(object){
    for (var i = 0; i < object.trips.length; i++) {
        // Consider switching from map to array
        trips.set(object.trips[i]["trip-id"], {trip_name: object["trips"][i]["trip-name"],
                                                agent: object["trips"][i]["agent"],
                                                device: object["trips"][i]["device"],
                                                uuid_tail: object["trips"][i]["uuid-tail"],
                                                os: object["trips"][i]["os"],
                                                trip_id: object["trips"][i]["trip-id"],
                                                max_update_interval: object["trips"][i]["max-update-interval"],
                                                avg_update_interval: object["trips"][i]["avg-update-interval"],
                                                min_update_interval: object["trips"][i]["min-update-interval"],
                                                vehicle_id: object["trips"][i]["vehicle-id"],
                                                timeline_x: object["trips"][i]["boundaries"]["timeline-x"],
                                                timeline_y: object["trips"][i]["boundaries"]["timeline-y"],
                                                timeline_width: object["trips"][i]["boundaries"]["timeline-width"],
                                                timeline_height: object["trips"][i]["boundaries"]["timeline-height"],
                                                map_x: object["trips"][i]["boundaries"]["map-x"],
                                                map_y: object["trips"][i]["boundaries"]["map-y"],
                                                map_width: object["trips"][i]["boundaries"]["map-width"],
                                                map_height: object["trips"][i]["boundaries"]["map-height"],
                                                })
    }
    timelineHeight = object["header-height"];
}

function selectTrip(trip){
    console.log("selectTrip()");
    drawReport();
    mapCtx.beginPath();
    mapCtx.lineWidth = 4;
    mapCtx.strokeStyle = "green";
    var mapY = trip.map_y * scaleRatio;
    var mapHeight = trip.map_height * scaleRatio;
    mapCtx.rect(trip.map_x * scaleRatio, mapY, trip.map_width * scaleRatio, trip.map_height * scaleRatio);
    mapCtx.stroke();

    var scrollTop = document.documentElement.scrollTop;
    var scrollBottom = scrollTop + windowHeight;

    if(mapY < scrollTop || mapY + mapHeight > scrollBottom){
        scrollTop = Math.min((mapY - (windowHeight - mapHeight) / 2), mapCanvasHeight )
        document.documentElement.scrollTop = scrollTop;
    }

    timelineCtx.beginPath();
    timelineCtx.lineWidth = 4;
    timelineCtx.strokeStyle = "green";
    timelineCtx.rect(trip.timeline_x * scaleRatio, trip.timeline_y * scaleRatio, trip.timeline_width * scaleRatio, trip.timeline_height * scaleRatio);
    timelineCtx.stroke();
}

function drawToolTip(trip){
    console.log("drawTooltip()");

    var margin = 5;
    font_size = 30;

    mapWidth = trip["map_width"] * scaleRatio;
    toolTipX = trip["map_x"] * scaleRatio + mapWidth;
    toolTipY = trip["map_y"] * scaleRatio;
    mapHeight = trip ["map_height"] * scaleRatio;

    while (tooltipItems.length * (font_size + margin) >= mapHeight){
        console.log("reducing font size...");
        font_size -= 5;
    }

    var toolTipWidth = getMaxTextWidth(trip) + margin * 2;

    // Tooltip on right side unless it would go over edge
    if((toolTipX + toolTipWidth) > canvasWidth){
        toolTipX -= (mapWidth + toolTipWidth);
    }

    // Draw tooltip box outline
    mapCtx.beginPath();
    mapCtx.lineWidth = 1
    mapCtx.strokeStyle = "black";
    mapCtx.fillStyle = "white";
    mapCtx.fillRect(toolTipX, toolTipY, toolTipWidth, mapHeight);
    mapCtx.rect(toolTipX, toolTipY, toolTipWidth, mapHeight);

    // Draw tooltip text
    mapCtx.stroke();
    mapCtx.fillStyle = "black";
    mapCtx.font= font_size + "px " + font;

    for (var i = 0; i < tooltipItems.length; i++) {
        toolTipY += (font_size + margin);
        mapCtx.fillText(tooltipItems[i] + ": " + trip[tooltipItems[i]], toolTipX + margin , toolTipY);
    }
}

function getMaxTextWidth(trip) {
    maxWidth = 0;
    mapCtx.font= font_size + "px " + font;
    for (var i = 0; i < tooltipItems.length; i++) {
        text = tooltipItems[i] + ": " + trip[tooltipItems[i]];
        length = mapCtx.measureText(text).width;
        if (length > maxWidth){
            maxWidth = length;
        }
    }
    return Math.round(maxWidth);
}

function mapContainsPoint(object, x, y){
    if(x >= object.map_x * scaleRatio){
        if(x <= (object.map_x + object.map_width) * scaleRatio){
            if(y >= object.map_y * scaleRatio ){
                if(y <= (object.map_y + object.map_height) * scaleRatio){
                    return true;
                }
            }
        }
    }
    return false
}

function timelineContainsPoint(object, x, y){
    if(x >= object.timeline_x * scaleRatio){
        if(x <= (object.timeline_x + object.timeline_width) * scaleRatio){
            if(y >= object.timeline_y * scaleRatio){
                if(y <= (object.timeline_y + object.timeline_height) * scaleRatio){
                    return true;
                }
            }
        }
    }
    return false;
}

function clearSelectOptions(sel) {
    var l = sel.options.length - 1;

    for(var i = l; i >= 0; i--) {
        sel.remove(i);
    }
}

loadDropdownJSON();