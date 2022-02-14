var scaleRatio;
const trips = new Map();
var canvas
var ctx;
const img = new Image();

function drawMap(){
    canvas = document.getElementById('canvas');
    ctx = canvas.getContext('2d');
    ctx.canvas.width  = window.innerWidth;
    img.src = "./tcrta-2022-02-09.png";

    img.onload = function() {
        scaleRatio = window.innerWidth / this.naturalWidth;
        console.log("Scaling ratio..." + scaleRatio);
        var scaledImageHeight = this.naturalHeight * scaleRatio;
        ctx.canvas.height = scaledImageHeight;
        console.log("Drawing map...");
        ctx.drawImage(img,0,0,window.innerWidth,scaledImageHeight);
        console.log("Canvas width: " + window.innerWidth);
        console.log("Canvas height: " + scaledImageHeight);
        };

    // Add event listener for `click` events.
    canvas.addEventListener('click', function(event) {
        var x = event.pageX;
        var y = event.pageY;
        console.log("x:" + x);
        console.log("y:" + y);
        var foundTrip = false;
        for (const [key, value] of trips.entries()) {
            if (objectContainsPoint(value, x, y)){
                mapTrip(value);
                foundTrip = true;
                break;
            }
        }
        if (!foundTrip){
            // Since we can't simply remove rectangle, reload the map
            drawMap();
        }
    }, false);
}

function loadJSON(){
    fetch("https://storage.googleapis.com/graas-resources/web/graas-report-web-8.json")
    .then(response => {
       return response.json();
    })
    .then(jsondata => processJSON(jsondata))
}

function processJSON(object){
    if(scaleRatio == null) {
        setTimeout(function() {processJSON(object)}, 1000);
    }
    for (var i = 0; i < object.trips.length; i++) {
        trips.set(object.trips[i]["trip-id"], {timeline_start_x: object["trips"][i]["boundaries"]["timeline-start-x"] * scaleRatio,
                                                timeline_end_x: object["trips"][i]["boundaries"]["timeline-end-x"] * scaleRatio,
                                                timeline_width: (object["trips"][i]["boundaries"]["timeline-end-x"] - object["trips"][i]["boundaries"]["timeline-start-x"]) * scaleRatio,
                                                timeline_start_y: object["trips"][i]["boundaries"]["timeline-start-y"] * scaleRatio,
                                                timeline_end_y: object["trips"][i]["boundaries"]["timeline-end-y"] * scaleRatio,
                                                timeline_height: (object["trips"][i]["boundaries"]["timeline-end-y"] - object["trips"][i]["boundaries"]["timeline-start-y"]) * scaleRatio,
                                                map_start_x: object["trips"][i]["boundaries"]["map-start-x"] * scaleRatio,
                                                map_end_x: object["trips"][i]["boundaries"]["map-end-x"] * scaleRatio,
                                                map_width: (object["trips"][i]["boundaries"]["map-end-x"] - object["trips"][i]["boundaries"]["map-start-x"]) * scaleRatio,
                                                map_start_y: object["trips"][i]["boundaries"]["map-start-y"] * scaleRatio,
                                                map_end_y: object["trips"][i]["boundaries"]["map-end-y"] * scaleRatio,
                                                map_height: (object["trips"][i]["boundaries"]["map-end-y"] - object["trips"][i]["boundaries"]["map-start-y"]) * scaleRatio,
                                                })
    }
    return trips
}

function mapTrip(trip){
    console.log("mapTrip()");
    console.log("trip to map:" + trip);
    ctx.beginPath();
    ctx.lineWidth = "6";
    ctx.strokeStyle = "green";
    ctx.rect(trip.map_start_x, trip.map_start_y, trip.map_width, trip.map_height);
    ctx.rect(trip.timeline_start_x, trip.timeline_start_y, trip.timeline_width, trip.timeline_height);
    ctx.stroke();
}

function objectContainsPoint(object, x, y){
    console.log("startx: " + object.map_start_x);
    console.log("endx: " + object.map_end_x);
    console.log("starty: " + object.map_start_y);
    console.log("endy: " + object.map_end_y);
    if(x >= object.map_start_x){
        if(x <= object.map_end_x){
            if(y >= object.map_start_y){
                if(y <= object.map_end_y){
                    return true;
                }
            }
        }
    }
    if(x >= object.timeline_start_x){
        if(x <= object.timeline_end_x){
            if(y >= object.timeline_start_y){
                if(y <= object.timeline_end_y){
                    return true;
                }
            }
        }
    }
    return false;
}

loadJSON();
