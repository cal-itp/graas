var scaleRatio;
const trips = new Map();
var canvas
var ctx;
const img = new Image();

function drawMap(){
    canvas = document.getElementById('canvas');
    ctx = canvas.getContext('2d');
    ctx.canvas.width  = window.innerWidth;
    img.src = "https://storage.googleapis.com/graas-resources/graas-report-archive/tcrta/tcrta-2022-02-14.png";

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
    // ?nocache= prevents annoying json caching...mostly for debugging purposes
    fetch("https://storage.googleapis.com/graas-resources/graas-report-archive/tcrta/tcrta-2022-02-14.json?nocache="  + (new Date()).getTime());
    .then(response => {
       return response.json();
    })
    .then(jsondata => processJSON(jsondata))
}

function processJSON(object){
    console.log("object: " + object["trips"][0]["boundaries"]["timeline-x"]);
    if(scaleRatio == null) {
        setTimeout(function() {processJSON(object)}, 1000);
    }
    for (var i = 0; i < object.trips.length; i++) {
        trips.set(object.trips[i]["trip-id"], {trip_name: object["trips"][i]["trip-name"],
                                                timeline_x: object["trips"][i]["boundaries"]["timeline-x"] * scaleRatio,
                                                timeline_y: object["trips"][i]["boundaries"]["timeline-y"] * scaleRatio,
                                                timeline_width: object["trips"][i]["boundaries"]["timeline-width"] * scaleRatio,
                                                timeline_height: object["trips"][i]["boundaries"]["timeline-height"] * scaleRatio,
                                                map_x: object["trips"][i]["boundaries"]["map-x"] * scaleRatio,
                                                map_y: object["trips"][i]["boundaries"]["map-y"] * scaleRatio,
                                                map_width: object["trips"][i]["boundaries"]["map-width"] * scaleRatio,
                                                map_height: object["trips"][i]["boundaries"]["map-height"] * scaleRatio,
                                                })
    }
    console.log(trips);
    return trips
}

function mapTrip(trip){
    console.log("mapTrip()");
    console.log("trip to map:" + trip);
    ctx.beginPath();
    ctx.lineWidth = "4";
    ctx.strokeStyle = "green";
    ctx.rect(trip.map_x, trip.map_y, trip.map_width, trip.map_height);
    ctx.rect(trip.timeline_x, trip.timeline_y, trip.timeline_width, trip.timeline_height);
    ctx.stroke();
}

function objectContainsPoint(object, x, y){
    console.log("name: " + object.trip_name);
    console.log("mapstartx: " + object.map_x);
    console.log("mapstarty: " + object.map_y);
    console.log("timelinestartx: " + object.map_x);
    console.log("timelinestarty: " + object.map_y);
    if(x >= object.map_x){
        if(x <= object.map_x + object.map_width){
            if(y >= object.map_y){
                if(y <= object.map_y + object.map_height){
                    return true;
                }
            }
        }
    }
    if(x >= object.timeline_x){
        if(x <= object.timeline_x + object.timeline_width){
            if(y >= object.timeline_y){
                if(y <= object.timeline_y + object.timeline_height){
                    return true;
                }
            }
        }
    }
    return false;
}

loadJSON();
