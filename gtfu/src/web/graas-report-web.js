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
var mapCanvasHeight;

var timelineCanvas;
var timelineCtx
var timelineHeight;
var timelineCanvasHeight;

var dropdownHeight;
var headerHeight;
var windowHeight;

var tripMapWidth;
var tripMapHeight;

var scrollTop;
var timelineScrollTop;
var mapScrollTop;
var scrollBottom;

const columnCount = 4;
var rowCount;

var activeAnimationCount = 0;
var mostRecentAnimation = null;
var isNewPageSelect = false;

var isZoomedIn = false;
var translateOffsetY;
var translateOffsetX;

var lastClick = null;

var instructionsVisible = false;

var font_size = 20;
const bucketURL = "https://storage.googleapis.com/graas-resources"
const font = "Arial";
const tooltipItems = ["trip_id", "vehicle_id", "agent", "device",
                     "os", "uuid_tail", "avg_update_interval",
                     "max_update_interval", "min_update_interval"];

 const OPACITY_MULTIPLIER = 0.98;
 const ALPHA_MIN = 0.4;
 const MARGIN = 5;

function initialize(){
    getRewriteArgs()
    url = `${bucketURL}/web/graas-report-agency-dates.json`;
    loadJSON(url, processDropdownJSON);
}

function loadJSON(url, callback){
    // "?nocache=" mostly forces the page to reload files each time
    fetch(url + "?nocache="  + (new Date()).getTime())
    .then(response => {
       return response.json();
    })
    .then(jsondata => callback(jsondata))
}

function processDropdownJSON(object){

    let p = document.getElementById("agency-select");
    let opt = document.createElement('option');
    opt.appendChild(document.createTextNode("Select agency-id..."));
    p.appendChild(opt);

    for (let key in object) {
        agencies.set(key, object[key])
        let opt = document.createElement('option');
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
    isNewPageSelect = true;
    if(utmAgency == null){
        selectedAgency = document.getElementById("agency-select").value;
    }
    let p = document.getElementById("date-select");
    clearSelectOptions(p);

    // loop counts down to get reverse-chronological dates
    for (let i = agencies.get(selectedAgency).length -1; i >= 0; i--) {
        let opt = document.createElement('option');
        opt.appendChild(document.createTextNode(agencies.get(selectedAgency)[i]));
        p.appendChild(opt);
    }
    handleDateChoice();
}

function handleDateChoice(){
    console.log("handleDateChoice()");
    isNewPageSelect = true;
    selectedDate = document.getElementById("date-select").value;
    // "?nocache=" mostly forces the page to reload files each time
    url = `${bucketURL}/graas-report-archive/${selectedAgency}/${selectedAgency}-${selectedDate}.json?nocache=${(new Date()).getTime()}`;
    loadJSON(url, processTripJSON);
}

function processTripJSON(object){
    trips.length = 0;
    for (let i = 0; i < object.trips.length; i++) {
        let map = {x: object["trips"][i]["boundaries"]["map-x"],
                   y: object["trips"][i]["boundaries"]["map-y"],
                   width: object["trips"][i]["boundaries"]["map-width"],
                   height: object["trips"][i]["boundaries"]["map-height"]
                };
        let timeline = {x: object["trips"][i]["boundaries"]["timeline-x"],
                       y: object["trips"][i]["boundaries"]["timeline-y"],
                       width: object["trips"][i]["boundaries"]["timeline-width"],
                       height: object["trips"][i]["boundaries"]["timeline-height"]
                };
        let points = []
        for (let j = 0; j < object["trips"][i]["trip-points"].length; j++) {
            points.push({x: object["trips"][i]["trip-points"][j]["x"],
                        y: object["trips"][i]["trip-points"][j]["y"],
                        secs: Math.round(object["trips"][i]["trip-points"][j]["millis"]/1000),
                        count: object["trips"][i]["trip-points"][j]["count"]
                        });
        }
        // Sort points
        points.sort(function compareFn(a, b) {
            if (a.secs <= b.secs) {
                return -1;
            }
            else return 1;
        });

        // Remove points with duplicate timestamps - this is rare but it happens.
        // A more robust approach would be to determine why duplicate timestamps are occuring.
        let x = 0;
        let y = 1;
        while(x < points.length && y < points.length){
            if(points[y].secs !== points[x].secs){
                x++;
                points[x] = points[y];
                y++;
            } else {
                y++;
            }
        }
        points.splice(x);

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
                    timeline: timeline,
                    points: points
                    });
    }
    timelineHeight = object["header-height"];
    console.log(trips);
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

    dropdownHeight = document.getElementById('top').offsetHeight;
    // ?nocache= prevents annoying caching...mostly for debugging purposes
    reportImageUrl = `${bucketURL}/graas-report-archive/${selectedAgency}/${selectedAgency}-${selectedDate}.png?nocache=${(new Date()).getTime()}`;
    img.src = reportImageUrl;

    p = document.getElementById('download');
    p.href = reportImageUrl;
    p.download = `${selectedAgency}-${selectedDate}.png`;
    p.style.display = "inline-block";

    showElement("click-here");
    rowCount = Math.ceil(trips.length / columnCount);

    img.onload = function() {
        imageWidth = this.naturalWidth
        imageHeight = this.naturalHeight;
        scaleRatio = window.innerWidth / imageWidth;
        // console.log("scaleRatio: " + scaleRatio);

        timelineCanvasHeight = timelineHeight * scaleRatio;
        headerHeight = timelineCanvasHeight + dropdownHeight
        timelineCtx.canvas.height = timelineCanvasHeight;

        mapHeight = imageHeight - timelineHeight;
        mapScaledHeight = mapHeight * scaleRatio;
        mapCanvasHeight = Math.max(mapScaledHeight, window.innerHeight - headerHeight);
        mapCtx.canvas.height = mapCanvasHeight;

        tripMapWidth = trips[0].map.width * scaleRatio;
        tripMapHeight = trips[0].map.height * scaleRatio;

        // Once image is loaded, coordinates and dimensions need to be scaled
        trips.forEach(function (item) {
            item.map.x *= scaleRatio;
            item.map.y *= scaleRatio;
            item.map.width *= scaleRatio;
            item.map.height *= scaleRatio;
            item.timeline.x *= scaleRatio;
            item.timeline.y *= scaleRatio;
            item.timeline.width *= scaleRatio;
            item.timeline.height *= scaleRatio;
            item.points.forEach(function (point) {
                point.x *= scaleRatio;
                point.y *= scaleRatio;
            });
        });
        drawReport();
    };

    document.body.addEventListener('click', function(event) {
        // Click event fires twice - this is a hacky way of preventing that
        let clickTime = new Date().getTime();
        if(clickTime - lastClick < 100){
            lastClick = clickTime;
            return
        }
        lastClick = clickTime;

        let searchX = event.pageX;
        let searchY = event.pageY;

        if(searchY > dropdownHeight && instructionsVisible){
            hideElement("box");
            instructionsVisible = false;
            return;
        }

        if (event.shiftKey && isZoomedIn) {
            zoomOut();
            return;
        }

        isNewPageSelect = false;

        scrollTop = document.documentElement.scrollTop; // Top of current screen
        timelineScrollTop = scrollTop + dropdownHeight; // Top of current timeline
        mapScrollTop = scrollTop + headerHeight;        // Top of current map
        scrollBottom = scrollTop + windowHeight;        // Bottom of current map

        let searchObject = null;

        // If the click occurs below the header, adjust y value and search map objects
        // If the click occurs within the header, adjust y value and search timeline objects
        if(searchY >= mapScrollTop){
            // console.log("Click is BELOW header");
            searchY -=headerHeight;
            searchObject = "map";
        } else {
            // console.log("Click is ABOVE header");
            searchY -=timelineScrollTop;
            searchObject = "timeline";
        }

        if (isZoomedIn){
            searchX += translateOffsetX;
            searchY += translateOffsetY;
            searchX /= 2;
            searchY /= 2;
        }

        for (let i = 0; i < trips.length; i++){

            if (objectContainsPoint(trips[i][searchObject], searchX, searchY)){
                let clickedTrip = trips[i];
                console.log("Found trip: " + clickedTrip.trip_name);
                if (event.shiftKey && !isZoomedIn) {
                    console.log("Zooming into this trip...");
                    zoomTo(clickedTrip);
                    scrollToTrip(clickedTrip)
                }
                if(mostRecentAnimation === trips[i]){
                    console.log("Animation for this trip is already in progress.");
                    return
                }
                else{
                    console.log("Drawing background, metadata, etc...");
                    drawReportExcept(clickedTrip);
                    drawMetadata(clickedTrip);
                    selectTrip(mapCtx, clickedTrip, "map");
                    selectTrip(timelineCtx, clickedTrip, "timeline");
                    scrollToTrip(clickedTrip)
                    mostRecentAnimation = clickedTrip;
                    let tripDuration = clickedTrip.points[clickedTrip.points.length - 1].secs - clickedTrip.points[0].secs;
                    let timeoutInterval = 1;
                    console.log("tripDuration: " + tripDuration);
                    animateTrip(clickedTrip, 0, 0, timeoutInterval);
                    return;
                }
            }
        }
        drawReportExcept(mostRecentAnimation);

    });
}

function objectContainsPoint(object, x, y){
    if(x >= object.x && x <= object.x + object.width && y >= object.y && y <= object.y + object.height){
        return true;
    }
    else return false;
}

function zoomTo(trip){
    // console.log("zooming in");
    let gridNum = Math.round(trip.map.x / trip.map.width);
    if(gridNum === 0){
        translateOffsetX = 0;
    } else if( gridNum <= 2){
        translateOffsetX = (trip.map.width * 2);
    } else {
        translateOffsetX = (trip.map.width * 4);
    }

    let rowNum = Math.round(trip.map.y / trip.map.height);
    if(rowNum === 0){
        translateOffsetY = 0;
    }
    else if(rowNum + 1 < rowCount){
        translateOffsetY = trip.map.height * rowNum;
    } else{
        translateOffsetY = trip.map.height * (rowNum + 1);
    }

    mapCtx.translate(-translateOffsetX, -translateOffsetY);

    mapCtx.scale(2,2);
    isZoomedIn = true;
    drawReportExcept(trip);
}

function zoomOut(){
    // console.log("zooming out");
    mapCtx.scale(.5, .5);
    mapCtx.translate(translateOffsetX, translateOffsetY);
    isZoomedIn = false;
    mostRecentAnimation = null;
    drawReport();
}

function drawReport(){
    console.log("Drawing report...");
    drawReportExcept(null);
}

function drawReportExcept(trip){
    if(trip !== null){
        console.log("...except for trip " + trip.trip_name);
    }
    console.log("isZoomedIn: " + isZoomedIn);
    drawReportBackground();
    drawReportTripsExcept(trip);
}

function drawReportBackground(){
    timelineCtx.drawImage(img, 0, 0, imageWidth, timelineHeight, 0, 0, canvasWidth, timelineCanvasHeight);
    // Clear map background to account for zooming in/out
    mapCtx.clearRect(0, timelineHeight, imageWidth, mapHeight);
    mapCtx.drawImage(img, 0, timelineHeight, imageWidth, mapHeight, 0, 0, canvasWidth, mapScaledHeight);
}

function drawReportTripsExcept(trip){
    for(let i = 0; i < trips.length; i++){
        if(trips[i] === trip){
            continue;
        }
        let mapX = trips[i].map.x;
        let mapY = trips[i].map.y;

        for(let j = 0; j < trips[i].points.length; j++){
            let pointSize = getPointSize(trips[i].points[j].count);
            let opacity = getOpacity(trips[i].points[j].count);
            drawPoint(mapX + trips[i].points[j].x, mapY + trips[i].points[j].y, pointSize, "green", opacity);
        }
    }
}

function getOpacity(count){
    let alpha = Math.pow(OPACITY_MULTIPLIER, (count - 1));
    return Math.max(alpha, ALPHA_MIN);
}

function getPointSize(count){
    return 1 + (count - 1) / 8;
}

function animateTrip(trip, indexIterator, secsIterator, timeoutInterval){
    // console.log("indexIterator: " + indexIterator);
    // console.log("secsIterator: " + secsIterator);
    if(indexIterator === 0){
        activeAnimationCount++;
        secsIterator = trip.points[0].secs;
        console.log("Animating trip " + trip.trip_name);
    }
    if(activeAnimationCount > 1){
        if(trip !== mostRecentAnimation){
            activeAnimationCount--;
            console.log("Stopping animation because another one started.");
            return;
        }
    }
    if(isNewPageSelect === true){
        console.log("Page change detected. Aborting.")
        return;
    }
    if(indexIterator < trip.points.length){
        // console.log("trip.points[indexIterator].secs: " + trip.points[indexIterator].secs);
        if(trip.points[indexIterator].secs == secsIterator){
            let count = trip.points[indexIterator].count;
            let pointSize = getPointSize(count);
            let opacity = getOpacity(count);
            drawPoint(trip.map.x + trip.points[indexIterator].x, trip.map.y + trip.points[indexIterator].y, pointSize, "green", opacity);

            // The animatePoint function creates an animated expanding circle, which unfortunately does not work with opaque points
            // animatePoint(trip.map.x + trip.points[indexIterator].x, trip.map.y + trip.points[indexIterator].y, 1, trip.points[indexIterator].count, "green");
            indexIterator++;
        }

        setTimeout(function(){
                    animateTrip(trip, indexIterator, secsIterator + 1, timeoutInterval);
                    }, timeoutInterval);
    } else {
        activeAnimationCount--;
        mostRecentAnimation = null;
        console.log("Animation complete.");
    }
}

// function animatePoint(x, y, i, count, color){
    // if(i > count){
    //     return;
    // }
    // let pointSize = getPointSize(i);
    // let opacity = getOpacity(i);
    // drawPoint(x, y, pointSize, color, opacity);
    // setTimeout(function(){
    //             animatePoint(x, y, ++i, count);
    //             }, 1);
// }

function drawPoint(x, y, radius, color, alpha){
    mapCtx.beginPath();
    mapCtx.arc(x, y, radius, 0, 2 * Math.PI, false);
    mapCtx.fillStyle = color;
    mapCtx.globalAlpha = alpha;
    mapCtx.fill();
    mapCtx.globalAlpha = 1;
}

function drawMetadata(trip){
    console.log("drawMetadata()");

    // Font size would only get to 0 as a result of very buggy behavior. If that occurs, simply don't display metadata.
    if (font_size <= 0) return;

    metadataWidth = trip.map.width;
    metadataX = trip.map.x + metadataWidth;
    metadataY = trip.map.y;
    metadataHeight = trip.map.height;

    while ((tooltipItems.length * (font_size + MARGIN) >= metadataHeight)
            || (getMaxTextWidth(trip) + MARGIN * 2 >= metadataWidth))  {
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

    for (let i = 0; i < tooltipItems.length; i++) {
        metadataY += (font_size + MARGIN);
        mapCtx.fillText(`${tooltipItems[i]}: ${trip[tooltipItems[i]]}`, metadataX + MARGIN , metadataY);
    }
}

function getMaxTextWidth(trip) {
    maxWidth = 0;
    mapCtx.font= font_size + "px " + font;
    for (let i = 0; i < tooltipItems.length; i++) {
        text = tooltipItems[i] + ": " + trip[tooltipItems[i]];
        length = mapCtx.measureText(text).width;
        if (length > maxWidth){
            maxWidth = length;
        }
    }
    return Math.round(maxWidth);
}

function selectTrip(ctx, trip, part){
    ctx.beginPath();
    ctx.lineWidth = 4;
    ctx.strokeStyle = "green";
    ctx.rect(trip[part].x, trip[part].y, trip[part].width, trip[part].height + 1);
    ctx.stroke();
}

function scrollToTrip(trip){
    let mapY = trip.map.y;
    let tripMapHeight = trip.map.height;

    // Center the mapview if it's overlapping top or bottom
    if(mapY < mapScrollTop || mapY + tripMapHeight + headerHeight > scrollBottom){
        document.documentElement.scrollTop = mapY  + tripMapHeight * 1/3 - headerHeight;
    }
}

function showInstructions(){
    instructionsVisible = true;
    showElement("box");
}

// Below 5 functions are copied from graas.js
// TODO: consolidate this type of html util functions into one file
function clearSelectOptions(sel) {
    let l = sel.options.length - 1;

    for(let i = l; i >= 0; i--) {
        sel.remove(i);
    }
}

function getRewriteArgs() {
    const s = window.location.search;
    const arg = s.split(/[&\?]/);

    for (let a of arg) {
        if (!a) continue;

        const t = a.split('=');
        const key = t[0];
        const value = t[1];

        if (key === 'agency') {
            utmAgency = value
        }
    }
}

function hideElement(id) {
    changeDisplay(id,"none");
}

function showElement(id) {
    changeDisplay(id,"inline-block");
}

function changeDisplay(id,display) {
    var p = document.getElementById(id);
    p.style.display = display;
}