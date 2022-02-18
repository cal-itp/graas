
const trips = new Map();
const agencies = new Map();

var selectedAgency;
var selectedDate;

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
var mapWindowHeight

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
    timelineCtx.canvas.width = canvasWidth;

    dropdownHeight = document.getElementById('dropdowns').offsetHeight;

    img.src = `${bucketURL}/graas-report-archive/${selectedAgency}/${selectedAgency}-${selectedDate}-dev.png`;

    img.onload = function() {
        imageWidth = this.naturalWidth
        imageHeight = this.naturalHeight;
        scaleRatio = window.innerWidth / imageWidth;
        console.log("scaleRatio: " + scaleRatio);

        canvasHeight = imageHeight * scaleRatio;

        timelineCanvasHeight = timelineHeight * scaleRatio;
        headerHeight = timelineCanvasHeight + dropdownHeight
        timelineCtx.canvas.height = timelineCanvasHeight;

        mapHeight = imageHeight - timelineHeight;
        mapCanvasHeight = mapHeight * scaleRatio;
        mapCtx.canvas.height = mapCanvasHeight;
        mapWindowHeight = windowHeight - headerHeight;

        // It would be nice to do this while loading JSON,
        // but that occurs before image is loaded and scaleRatio is determined
        for (const [key, value] of trips.entries()) {
            value.map.x *= scaleRatio;
            value.map.y *= scaleRatio;
            value.map.width *= scaleRatio;
            value.map.height *= scaleRatio;
            value.timeline.x *= scaleRatio;
            value.timeline.y *= scaleRatio;
            value.timeline.width *= scaleRatio;
            value.timeline.height *= scaleRatio;
        }

        drawReport();
    };

    document.body.addEventListener('click', function(event) {
        console.log("event.pageY:" + event.pageY);

        drawReport();

        // Top of current screen
        scrollTop = document.documentElement.scrollTop;
        // Top of current timeline
        timelineScrollTop = scrollTop + dropdownHeight;
        // Top of current map
        mapScrollTop = scrollTop + headerHeight;
        // Bottom of current map
        scrollBottom = scrollTop + windowHeight;

        for (const [key, value] of trips.entries()) {
            var searchY = event.pageY;
            var searchObject;

            // If the click occurs below the header, adjust y value and search map objects
            if(searchY >= mapScrollTop){
                searchY -=headerHeight;
                searchObject = value.map;

            // If the click occurs within the header, adjust y value and search timeline objects
            } else {
                searchY -=timelineScrollTop;
                searchObject = value.timeline;
            }
            if (objectContainsPoint(searchObject, event.pageX, searchY)){
                drawMetadata(value);
                selectTrip(mapCtx, value.map);
                selectTrip(timelineCtx, value.timeline);
                scrollToTrip(value)
                break;
            }
        }
    });
}

function drawReport(){
    console.log("Drawing map...");
    timelineCtx.drawImage(img, 0, 0, imageWidth, timelineHeight, 0, 0, canvasWidth, timelineCanvasHeight);
    mapCtx.drawImage(img, 0, timelineHeight, imageWidth, mapHeight, 0, 0, canvasWidth, mapCanvasHeight);
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
    clearSelectOptions(p);
    // uncomment for prod:
    // var opt = document.createElement('option');
    // opt.appendChild(document.createTextNode("Select agency-id..."));
    // p.appendChild(opt);

    for (var key in object) {
        agencies.set(key, object[key])
        var opt = document.createElement('option');
        opt.appendChild(document.createTextNode(key));
        p.appendChild(opt);
    }
    // remove for prod:
    handleAgencyChoice()
}

function handleAgencyChoice(){
    console.log("handleAgencyChoice()");
    selectedAgency = document.getElementById("agency-select").value;
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
    load();
}

function processTripJSON(object){
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
                                                map: map,
                                                timeline: timeline
                                                })
    }
    timelineHeight = object["header-height"];
}

function selectTrip(ctx, object){
    ctx.beginPath();
    ctx.lineWidth = 4;
    ctx.strokeStyle = "green";
    ctx.rect(object.x, object.y, object.width, object.height);
    ctx.stroke();
}

// Currently not working when lower half of trip overhangs bottom of screen
function scrollToTrip(trip){
    var mapY = trip.map.y;
    var tripMapHeight = trip.map.height;

    console.log(`scrollBottom: ${scrollBottom}`)
    console.log(`mapY + tripMapHeight: ${mapY + tripMapHeight}`)
    console.log(`mapY: ${mapY}`)
    console.log(`tripMapHeight: ${tripMapHeight}`)
    // Center the mapview if it's overlapping an edge
    if(mapY < mapScrollTop || mapY + tripMapHeight > scrollBottom){
        document.documentElement.scrollTop = mapY  + tripMapHeight * 1/3 - headerHeight;
    }
}

function drawMetadata(trip){
    console.log("drawTooltip()");

    var margin = 5;

    metadataWidth = trip.map.width;
    metadataX = trip.map.x + metadataWidth;
    metadataY = trip.map.y;
    metadataHeight = trip.map.height;

    while ((tooltipItems.length * (font_size + margin) >= metadataHeight)
            || (getMaxTextWidth(trip) + margin * 2 >= metadataWidth))  {
        console.log("reducing font size...");
        font_size -= 5;
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

function objectContainsPoint(object, x, y){
    if(x >= object.x){
        if(x <= (object.x + object.width)){
            if(y >= object.y ){
                if(y <= (object.y + object.height)){
                    return true;
                }
            }
        }
    }
    return false
}

function clearSelectOptions(sel) {
    var l = sel.options.length - 1;

    for(var i = l; i >= 0; i--) {
        sel.remove(i);
    }
}

url = `${bucketURL}/web/graas-report-agency-dates.json`;
loadJSON(url, processDropdownJSON);