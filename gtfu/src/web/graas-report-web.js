var scaleRatio;
const maps = new Map()
const timelines = new Map()

function draw(){
    console.log("drawing!");
    var canvas = document.getElementById('canvas');
    var ctx = canvas.getContext('2d');
    ctx.canvas.width  = window.innerWidth;
    const img = new Image();
    img.src = "./tcrta-2022-02-09.png";

    img.onload = function() {
        scaleRatio = window.innerWidth / this.naturalWidth;
        var scaledImageHeight = this.naturalHeight * scaleRatio;
        ctx.canvas.height = scaledImageHeight;
        ctx.drawImage(img,0,0,window.innerWidth,scaledImageHeight);
        };
}

var obj;

fetch("https://storage.googleapis.com/graas-resources/web/graas-report-web-6.json")
.then(response => {
   return response.json();
})
.then(jsondata => loadObject(jsondata))

function loadObject(object){
    for (var i = 0; i < object.trips.length; i++) {
        timelines.set(object.trips[i]["trip-id"], {timeline_start_x: object["trips"][i]["boundaries"]["timeline-start-x"],
                                                    timeline_start_y: object["trips"][i]["boundaries"]["timeline-start-y"],
                                                    timeline_end_x: object["trips"][i]["boundaries"]["timeline-end-x"],
                                                    timeline_end_y: object["trips"][i]["boundaries"]["timeline-end-y"]
                                                    })

        maps.set(object.trips[i]["trip-id"], {map_start_x: object["trips"][i]["boundaries"]["map-start-x"],
                                                map_start_y: object["trips"][i]["boundaries"]["map-start-y"],
                                                map_end_x: object["trips"][i]["boundaries"]["map-end-x"],
                                                map_end_y: object["trips"][i]["boundaries"]["map-end-y"]
                                                })
    }
}
