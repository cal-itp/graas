class ShapePoint {
    constructor(lat, lon) {
        this.lat = lat;
        this.lon = lon;
    }

    getLat() {
        return this.lat;
    }
    setLatIfLess(lat){
        if (this.lat === null || lat < this.lat){
            this.lat = lat;
        }
    }

    setLatIfGreater(lat){
        if (this.lat === null || lat > this.lat){
            this.lat = lat;
        }
    }

    setLonIfLess(lon){
        if (this.lon === null || lon < this.lon){
            this.lon = lon;
        }
    }

    setLonIfGreater(lon){
        if (this.lon === null || lon > this.lon){
            this.lon = lon;
        }
    }
}