class ShapePoint {
    constructor(lat = null, lon = null) {
        util.log("ShapePoint constructor");
        this.lat = lat;
        this.lon = lon;
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

    get_distance(p){
        return util.getHaversineDistance(this.lat, this.lon, p.lat, p.lon)
    }

    get_distance_from_lat_long(plat, plon){
        return util.getHaversineDistance(this.lat, this.lon, plat, plon)
    }

    __str__(){
        return `(${this.lat}, ${this.lon})`;
    }
    getLat() {
        return this.lat;
    }
}