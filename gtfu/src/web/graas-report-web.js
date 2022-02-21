
const trips = [];
const agencies = new Map();

var utmAgency;
var selectedAgency;
var selectedDate;

var reportImageUrl;
const img = new Image();
var imageWidth;
var imageHeight;

var scaleRatio;
var canvasWidth;

var mapCanvas
var mapCtx;
var mapHeight;
var mapCanvasHeight;

var timelineCanvas;
var timelineCtx
var timelineHeight;
var timelineCanvasHeight;

var dropdownHeight;
var headerHeight;
var windowHeight;

var scrollTop;
var timelineScrollTop;
var mapScrollTop;
var scrollBottom;

var font_size = 20;
const bucketURL = "https://storage.googleapis.com/graas-resources"
const font = "Arial";
const tooltipItems = ["trip_id", "vehicle_id", "agent", "device",
                     "os", "uuid_tail", "avg_update_interval",
                     "max_update_interval", "min_update_interval"];

function initialize(){
    getRewriteArgs()
    url = `${bucketURL}/web/graas-report-agency-dates.json`;
    loadJSON(url, processDropdownJSON);
}

function loadJSON(url, callback){
    // ?nocache= prevents annoying json caching...mostly for debugging purposes
    fetch(url + "?nocache="  + (new Date()).getTime())
    .then(response => {
       return response.json();
    })
    .then(jsondata => callback(jsondata))
}

function processDropdownJSON(object){

    var p = document.getElementById("agency-select");
    var opt = document.createElement('option');
    opt.appendChild(document.createTextNode("Select agency-id..."));
    p.appendChild(opt);

    for (var key in object) {
        agencies.set(key, object[key])
        var opt = document.createElement('option');
        opt.appendChild(document.createTextNode(key));
        p.appendChild(opt);
    }

    if(utmAgency != null){
        selectedAgency = utmAgency;
        handleAgencyChoice();
    } else{
        p.style.display = "inline-block";
    }
}

function handleAgencyChoice(){
    console.log("handleAgencyChoice()");

    if(utmAgency == null){
        selectedAgency = document.getElementById("agency-select").value;
    }
    var p = document.getElementById("date-select");
    clearSelectOptions(p);

    // loop counts down to get reverse-chronological dates
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
    url = `${bucketURL}/graas-report-archive/${selectedAgency}/${selectedAgency}-${selectedDate}.json`;
    loadJSON(url, processTripJSON);
}

function processTripJSON(object){
    trips.length = 0;
    for (var i = 0; i < object.trips.length; i++) {
        var map = {x: object["trips"][i]["boundaries"]["map-x"],
                   y: object["trips"][i]["boundaries"]["map-y"],
                   width: object["trips"][i]["boundaries"]["map-width"],
                   height: object["trips"][i]["boundaries"]["map-height"]
                };
        var timeline = {x: object["trips"][i]["boundaries"]["timeline-x"],
                       y: object["trips"][i]["boundaries"]["timeline-y"],
                       width: object["trips"][i]["boundaries"]["timeline-width"],
                       height: object["trips"][i]["boundaries"]["timeline-height"]
                };

        // Consider switching from map to array
        trips.push({trip_name: object["trips"][i]["trip-name"],
                    agent: object["trips"][i]["agent"],
                    device: object["trips"][i]["device"],
                    uuid_tail: object["trips"][i]["uuid-tail"],
                    os: object["trips"][i]["os"],
                    trip_id: object["trips"][i]["trip-id"],
                    max_update_interval: object["trips"][i]["max-update-interval"],
                    avg_update_interval: object["trips"][i]["avg-update-interval"],
                    min_update_interval: object["trips"][i]["min-update-interval"],
                    vehicle_id: object["trips"][i]["vehicle-id"],
                    map: map,
                    timeline: timeline
                    })
    }
    timelineHeight = object["header-height"];
    load();
}

function load(){
    canvasWidth = window.innerWidth;
    windowHeight = window.innerHeight;

    mapCanvas = document.getElementById('maps');
    mapCtx = mapCanvas.getContext('2d');
    mapCtx.canvas.width  = canvasWidth;

    timelineCanvas = document.getElementById('timeline');
    timelineCtx = timelineCanvas.getContext('2d');
    timelineCtx.canvas.width = canvasWidth;

    dropdownHeight = document.getElementById('dropdowns').offsetHeight;

    reportImageUrl = `${bucketURL}/graas-report-archive/${selectedAgency}/${selectedAgency}-${selectedDate}.png`;
    img.src = reportImageUrl;

    p = document.getElementById('download');
    p.href = reportImageUrl;
    p.download = `${selectedAgency}-${selectedDate}.png`;
    p.style.display = "inline-block";

    img.onload = function() {
        imageWidth = this.naturalWidth
        imageHeight = this.naturalHeight;
        scaleRatio = window.innerWidth / imageWidth;
        console.log("scaleRatio: " + scaleRatio);

        timelineCanvasHeight = timelineHeight * scaleRatio;
        headerHeight = timelineCanvasHeight + dropdownHeight
        timelineCtx.canvas.height = timelineCanvasHeight;

        mapHeight = imageHeight - timelineHeight;
        mapCanvasHeight = mapHeight * scaleRatio;
        mapCtx.canvas.height = mapCanvasHeight;

        // It would be nice to perform this multiplication while initially loading JSON,
        // but that occurs before image is loaded and scaleRatio is determined by image size
        trips.forEach(function (item) {
            item.map.x *= scaleRatio;
            item.map.y *= scaleRatio;
            item.map.width *= scaleRatio;
            item.map.height *= scaleRatio;
            item.timeline.x *= scaleRatio;
            item.timeline.y *= scaleRatio;
            item.timeline.width *= scaleRatio;
            item.timeline.height *= scaleRatio;
        });
        drawReport();
    };

    document.body.addEventListener('click', function(event) {
        drawReport();

        scrollTop = document.documentElement.scrollTop; // Top of current screen
        timelineScrollTop = scrollTop + dropdownHeight; // Top of current timeline
        mapScrollTop = scrollTop + headerHeight;        // Top of current map
        scrollBottom = scrollTop + windowHeight;        // Bottom of current map

        for (var i = 0; i < trips.length; i++){
            var searchY = event.pageY;
            var searchObject;

            // If the click occurs below the header, adjust y value and search map objects
            if(searchY >= mapScrollTop){
                searchY -=headerHeight;
                searchObject = trips[i].map;

            // If the click occurs within the header, adjust y value and search timeline objects
            } else {
                searchY -=timelineScrollTop;
                searchObject = trips[i].timeline;
            }
            if (objectContainsPoint(searchObject, event.pageX, searchY)){
                drawMetadata(trips[i]);
                selectTrip(mapCtx, trips[i].map);
                selectTrip(timelineCtx, trips[i].timeline);
                scrollToTrip(trips[i])
                break;
            }
        }
    });
}

function objectContainsPoint(object, x, y){
    if(x >= object.x && x <= object.x + object.width && y >= object.y && y <= object.y + object.height){
        return true;
    }
    else return false;
}

function drawReport(){
    console.log("Drawing map...");
    timelineCtx.drawImage(img, 0, 0, imageWidth, timelineHeight, 0, 0, canvasWidth, timelineCanvasHeight);
    mapCtx.drawImage(img, 0, timelineHeight, imageWidth, mapHeight, 0, 0, canvasWidth, mapCanvasHeight);
}

function drawMetadata(trip){
    console.log("drawMetadata()");

    // Font size would only get to 0 as a result of very buggy behavior. If that occurs, simply don't display metadata.
    if (font_size <= 0) return;

    var margin = 5;
    metadataWidth = trip.map.width;
    metadataX = trip.map.x + metadataWidth;
    metadataY = trip.map.y;
    metadataHeight = trip.map.height;

    while ((tooltipItems.length * (font_size + margin) >= metadataHeight)
            || (getMaxTextWidth(trip) + margin * 2 >= metadataWidth))  {
        font_size -= 5;
        if (font_size <= 0) return;
    }

    // Metadata on right side unless it would go over edge
    if((metadataX + metadataWidth) > canvasWidth){
        metadataX -= (2 * metadataWidth);
    }

    // Draw tooltip box outline
    mapCtx.beginPath();
    mapCtx.lineWidth = 1
    mapCtx.strokeStyle = "black";
    mapCtx.fillStyle = "white";
    mapCtx.fillRect(metadataX, metadataY, metadataWidth, metadataHeight);
    mapCtx.rect(metadataX, metadataY, metadataWidth, metadataHeight);

    // Draw tooltip text
    mapCtx.stroke();
    mapCtx.fillStyle = "black";
    mapCtx.font= `${font_size}px ${font}`;

    for (var i = 0; i < tooltipItems.length; i++) {
        metadataY += (font_size + margin);
        mapCtx.fillText(`${tooltipItems[i]}: ${trip[tooltipItems[i]]}`, metadataX + margin , metadataY);
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

function selectTrip(ctx, object){
    ctx.beginPath();
    ctx.lineWidth = 4;
    ctx.strokeStyle = "green";
    ctx.rect(object.x, object.y, object.width, object.height);
    ctx.stroke();
}

function scrollToTrip(trip){
    var mapY = trip.map.y;
    var tripMapHeight = trip.map.height;

    // Center the mapview if it's overlapping top or bottom
    if(mapY < mapScrollTop || mapY + tripMapHeight + headerHeight > scrollBottom){
        document.documentElement.scrollTop = mapY  + tripMapHeight * 1/3 - headerHeight;
    }
}

// Below two functions are copied from graas.js
// TODO: consolidate this type of html util functions into one file
function clearSelectOptions(sel) {
    var l = sel.options.length - 1;

    for(var i = l; i >= 0; i--) {
        sel.remove(i);
    }
}

function getRewriteArgs() {
    const s = window.location.search;
    const arg = s.split(/[&\?]/);

    for (var a of arg) {
        if (!a) continue;

        const t = a.split('=');
        const key = t[0];
        const value = t[1];

        if (key === 'agency') {
            utmAgency = value
        }
    }
}