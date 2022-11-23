if(typeof util === 'undefined'){
    var util = require('../static/gtfs-rt-util');
    var shapepoint = require('../static/shapepoint');
}

(function(exports) {
    exports.Area = class {
        constructor(tl = null, br = null){
            // util.log("Area constructor");
            this.topLeft = (tl === null ? new shapepoint.ShapePoint() : new shapepoint.ShapePoint(tl.lat, tl.lon));
            this.bottomRight = (br === null ? new shapepoint.ShapePoint() : new shapepoint.ShapePoint(br.lat, br.lon));
        }

        copy(a){
            return new area.Area(a.topLeft, a.bottomRight);
        }

        extend(feet){
            // util.log("extend()");
            this.topLeft.lat += util.getFeetAsLatDegrees(feet / 2);
            this.topLeft.lon -= util.getFeetAsLongDegrees(feet / 2);
            this.bottomRight.lat -= util.getFeetAsLatDegrees(feet / 2);
            this.bottomRight.lon += util.getFeetAsLongDegrees(feet / 2);
        }

        update(lat, lon){
            // util.log("update()");
            this.topLeft.setLonIfLess(lon)
            this.topLeft.setLatIfGreater(lat)
            this.bottomRight.setLonIfGreater(lon)
            this.bottomRight.setLatIfLess(lat)
        }

        getLatDelta(){
            return Math.abs(Math.abs(this.topLeft.lat) - Math.abs(this.bottomRight.lat))
        }

        getLongDelta(){
            return Math.abs(Math.abs(this.topLeft.lon) - Math.abs(this.bottomRight.lon))
        }

        getAspectRatio(){
            return this.getLongDelta() / this.getLatDelta();
        }

        getLatFraction(lat, showError=true){
            if (lat < this.bottomRight.lat || lat > this.topLeft.lat){
                util.log(` - error: lat of ${lat} is not between ${this.bottomRight.lat} and ${this.topLeft.lat}`);
                return null;
            }
            let delta = this.getLatDelta();
            return 1 - ((lat - this.bottomRight.lat) / delta);
        }

        getLongFraction(lon, showError=true){
            if (lon > this.bottomRight.lon || lon < this.topLeft.lon){
                util.log(` - error: lon of ${lon} is not between ${this.bottomRight.lon} and ${this.topLeft.lon}`);           return null;
            }
            let delta = this.getLongDelta();
            return 1 - ((Math.abs(lon) - Math.abs(this.bottomRight.lon)) / delta);
        }

        toString() {
            return `{topLeft: ${this.topLeft}, bottomRight: ${this.bottomRight}, width: ${util.getDistanceString(this.getWidthInFeet())}, ${util.getDistanceString(this.getHeightInFeet())}}`;
        }

        getWidthInFeet() {
            return Math.round(util.haversineDistance(0, this.topLeft.lon, 0, this.bottomRight.lon));
        }

        getHeightInFeet() {
            return Math.round(util.haversineDistance(this.topLeft.lat, 0, this.bottomRight.lat, 0));
        }

        contains(lat, lon){
            return (lat >= this.bottomRight.lat && lat <= this.topLeft.lat
                && lon <= this.bottomRight.lon && lon >= this.topLeft.lon);
        }
    }
}(typeof exports === 'undefined' ? this.area = {} : exports));