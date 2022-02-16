var scaleRatio;
const trips = new Map();
var mapCanvas
var mapCtx;
var headerCanvas;
var headerCtx
const img = new Image();
var canvasHeight;
var canvasWidth;
var windowHeight;
var headerHeight;
var imageWidth;
var imageHeight;
var headerCanvasHeight;
var mapCanvasHeight;
const fontSize = 50;
const font = "Arial";
const tooltipItems = ["trip_id", "vehicle_id", "agent", "device",
                     "os", "uuid_tail", "avg_update_interval",
                     "max_update_interval", "min_update_interval"];

function load(){
    console.log("trips.size: " + trips.size);
    if(trips.size == 0) {
        setTimeout(load, 500);
    }
    console.log("trips.size: " + trips.size);
    mapCanvas = document.getElementById('maps');
    mapCtx = mapCanvas.getContext('2d');
    headerCanvas = document.getElementById('header');
    headerCtx = headerCanvas.getContext('2d');
    canvasWidth = window.innerWidth;
    windowHeight = window.innerHeight;
    mapCtx.canvas.width  = canvasWidth;
    headerCtx.canvas.width  = canvasWidth;
    console.log("headerHeight: " + headerHeight);
    img.src = "https://storage.googleapis.com/graas-resources/graas-report-archive/tcrta/tcrta-2022-02-14-dev.png?nocache=123";

    img.onload = function() {
    console.log("trips.size: " + trips.size);
        imageWidth = this.naturalWidth
        imageHeight = this.naturalHeight;
        scaleRatio = window.innerWidth / imageWidth;
        headerCanvasHeight = headerHeight * scaleRatio;
        mapCanvasHeight = (imageHeight - headerHeight) * scaleRatio;
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
        var foundTrip = false;
        if(y >= headerCanvasHeight){
            for (const [key, value] of trips.entries()) {
                if (mapContainsPoint(value, x, y - headerCanvasHeight)){
                    selectTrip(value);
                    drawToolTip(value, x, y - headerCanvasHeight);
                    foundTrip = true;
                    break;
                }
            }
        } else {
            for (const [key, value] of trips.entries()) {
                if (headerContainsPoint(value, x, y)){
                    selectTrip(value);
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
    // console.log("imageWidth: " + imageWidth);
    // console.log("imageHeight: " + imageHeight);
    // console.log("headerHeight: " + headerHeight);
    // console.log("canvasWidth: " + canvasWidth);
    // console.log("canvasHeight: " + canvasHeight);
    // console.log("headerCanvasHeight: " + headerCanvasHeight);
    headerCtx.drawImage(img, 0, 0, imageWidth, headerHeight, 0, 0, canvasWidth, headerCanvasHeight);
    mapCtx.drawImage(img, 0, headerHeight, imageWidth, imageHeight - headerHeight, 0, 0, canvasWidth, mapCanvasHeight);
}

function loadJSON(){
    // ?nocache= prevents annoying json caching...mostly for debugging purposes
    fetch("https://storage.googleapis.com/graas-resources/graas-report-archive/tcrta/tcrta-2022-02-14.json?nocache="  + (new Date()).getTime())
    .then(response => {
       return response.json();
    })
    .then(jsondata => processJSON(jsondata))
}

function processJSON(object){
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
    headerHeight = object["header-height"];
}

function selectTrip(trip){
    console.log("selectTrip()");
    drawReport();
    mapCtx.beginPath();
    mapCtx.lineWidth = 4;
    mapCtx.strokeStyle = "green";
    mapCtx.rect(trip.map_x * scaleRatio, trip.map_y * scaleRatio, trip.map_width * scaleRatio, trip.map_height * scaleRatio);
    mapCtx.stroke();

    headerCtx.beginPath();
    headerCtx.lineWidth = 4;
    headerCtx.strokeStyle = "green";
    headerCtx.rect(trip.timeline_x * scaleRatio, trip.timeline_y * scaleRatio, trip.timeline_width * scaleRatio, trip.timeline_height * scaleRatio);
    headerCtx.stroke();
}

function drawToolTip(trip, x, y){
    console.log("drawTooltip()");
    var startX = x;
    var startY = y;
    var margin = 15;
    var toolTipWidth = getMaxTextWidth(trip) + margin * 2;
    var toolTipHeight = (fontSize + margin + 1) * (tooltipItems.length );

    // Prevent tooltip from hanging over edge of screen:
    if(startX > canvasWidth / 2){
        startX = x - toolTipWidth;
    }
    if(startY > canvasHeight * 2/3){
        startY = y - toolTipHeight;
    }

    // Draw tooltip box outline
    mapCtx.beginPath();
    mapCtx.lineWidth = 4
    mapCtx.strokeStyle = "black";
    mapCtx.fillStyle = "white";
    mapCtx.fillRect(startX, startY, toolTipWidth, toolTipHeight);
    mapCtx.rect(startX, startY, toolTipWidth, toolTipHeight);

    // Draw tooltip text
    mapCtx.font= fontSize + "px " + font;
    mapCtx.stroke();
    mapCtx.fillStyle = "black";
    for (var i = 0; i < tooltipItems.length; i++) {
        startY += (fontSize + margin);
        mapCtx.fillText(tooltipItems[i] + ": " + trip[tooltipItems[i]], startX + margin , startY);
    }
}

function getMaxTextWidth(trip) {
    maxWidth = 0;
    mapCtx.font= fontSize + "px " + font;
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

function headerContainsPoint(object, x, y){
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

loadJSON();
