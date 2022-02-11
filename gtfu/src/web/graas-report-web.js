var scaleRatio;

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

fetch("https://storage.googleapis.com/graas-resources/web/graas-report-web-5.json")
.then(response => {
   return response.json();
})
.then(jsondata => loadObject(jsondata))

function loadObject(object){
    console.log(object.trips[0].boundaries);
}
