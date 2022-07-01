class Grid {
    constructor(boundingBox, subdivisions){
        this.boundingBox = boundingBox;
        this.aspectRatio = boundingBox.getAspectRatio();
        this.subdivisions = subdivisions;
        this.table = {};
        util.log(`grid: top_left=${JSON.stringify(boundingBox.topLeft)} bottom_right=${JSON.stringify(boundingBox.bottomRight)} subdivisions=${subdivisions}`)
    }

    addSegment(segment, index){
        // util.log("addSegment()");
        let list = null;
        if (index in this.table){
            list = this.table[index];
        }
        else{
            list = [];
            this.table[index] = list;
        }
        list.push(segment);
    }

    getIndex(lat, lon){
        let latFrac = this.boundingBox.getLatFraction(lat, false)
        if (latFrac === null){
            return -1;
        }
        let lonFrac = this.boundingBox.getLongFraction(lon, false)
        if (lonFrac === null){
            return -1;
        }
        let index = (this.subdivisions * parseInt(Math.round(this.subdivisions * latFrac))
            + parseInt(Math.round(this.subdivisions * lonFrac)))
        return index;
    }

    getSegmentList(lat, lon){
        util.log("getSegmentList()");
        let index = this.getIndex(lat, lon);
        if (index in this.table){
            return this.table[index];
        } else return null;
    }
}