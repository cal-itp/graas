class ShapePoint {
    constructor(lat = null, lon = null) {
        util.log("ShapePoint constructor");
        if(lat !== null){
            this.lat = parseFloat(lat);
        } else this.lat = lat;
        if(lon !== null){
            this.lon = parseFloat(lon);
        } else this.lon = lon;
    }

    setLatIfLess(lat){
        let latFloat = parseFloat(lat);
        if (this.lat === null || latFloat < this.lat){
            this.lat = latFloat;
        }
    }

    setLatIfGreater(lat){
        let latFloat = parseFloat(lat);
        if (this.lat === null || latFloat > this.lat){
            this.lat = latFloat;
        }
    }

    setLonIfLess(lon){
        let lonFloat = parseFloat(lon);
        if (this.lon === null || lonFloat < this.lon){
            this.lon = lonFloat;
        }
    }

    setLonIfGreater(lon){
        let lonFloat = parseFloat(lon);
        if (this.lon === null || lonFloat > this.lon){
            this.lon = lonFloat;
        }
    }

    get_distance(p){
        return util.getHaversineDistance(this.lat, this.lon, p.lat, p.lon)
    }

    get_distance_from_lat_long(plat, plon){
        return util.getHaversineDistance(this.lat, this.lon, plat, plon)
    }

    getLat() {
        return this.lat;
    }
}