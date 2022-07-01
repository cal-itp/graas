class Area {
    constructor(tl = null, br = null){
        util.log("Area constructor");
        this.topLeft = (tl === null ? new ShapePoint() : new ShapePoint(tl.lat, tl.lon));
        this.bottomRight = (br === null ? new ShapePoint() : new ShapePoint(br.lat, br.lon));
    }

    copy(a){
        return new Area(a.topLeft, a.bottomRight);
    }

    extend(feet){
        // util.log("extend()");
        this.topLeft.lat += parseFloat(util.getFeetAsLatDegrees(feet / 2));
        this.topLeft.lon -= parseFloat(util.getFeetAsLongDegrees(feet / 2));

        this.bottomRight.lat -= parseFloat(util.getFeetAsLatDegrees(feet / 2));
        this.bottomRight.lon += parseFloat(util.getFeetAsLongDegrees(feet / 2));
    }

    update(lat, lon){
        // util.log("update()");
        this.topLeft.setLonIfLess(lon)
        this.topLeft.setLatIfGreater(lat)

        this.bottomRight.setLonIfGreater(lon)
        this.bottomRight.setLatIfLess(lat)
        if(lat.length > 20){
            util.log("lat length greater than 20: " + lat);
        }
        if(this.topLeft.lat.length > 20){
            util.log("topleft length greater than 20: " + this.topLeft.lat);
        }
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

    contains(lat, lon){
        return (lat >= this.bottomRight.lat && lat <= this.topLeft.lat
            && lon <= this.bottomRight.lon && lon >= this.topLeft.lon);
    }
}