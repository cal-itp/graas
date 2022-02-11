function draw(){
    var canvas = document.getElementById('canvas');
    var ctx = canvas.getContext('2d');
    const img = new Image();
    img.src = "./tcrta-2022-02-09.png";
    img.onload = () => {
      ctx.drawImage(img, 0, 0);
    }
}