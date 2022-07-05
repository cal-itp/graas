if(typeof util === 'undefined'){
    var util = require('../static/gtfs-rt-util');
}
  //   If the part of the surface of the earth which you want to draw is relatively small, then you can use a very simple approximation. You can simply use the horizontal axis x to denote longitude λ, the vertical axis y to denote latitude φ. The ratio between these should not be 1:1, though. Instead you should use cos(φ0) as the aspect ratio, where φ0 denotes a latitude close to the center of your map. Furthermore, to convert from angles (measured in radians) to lengths, you multiply by the radius of the earth (which in this model is assumed to be a sphere).

  //   x = r λ cos(φ0)
  //   y = r φ


  // - line equation from two points:
  //   m = (y1 - y0) / (x1 - x0)
  //   b = y0 - m * x0
  //   edge cases line horizontal or vertical: can probably set m to (-)Double.MAX_VALUE or 1 / (-)Double.MAX_VALUE

  // - check if intersection between vector s (SP1 to SP2) and u is on s
  //   + invert slope of s: m' = -(1/m)
  //   + find equation for u:
  //     f(x) = m'x + b'
  //     uy = m' * ux + b'
  //     b' = uy - (m' * ux)

  //   + derive x of intersection by setting m * x + b = m' * x + b'
  //       m * x + b - m' * x = b'
  //       m * x - m' * x = b' - b
  //       x (m - m') = b' - b
  //       x = (b' - b) / (m - m')
  //   + use x in either equation to get y
  //       y = m * x + b
  //   + check if (x, y) is on s:
  //     create vector s' from SP1 to (x, y)
  //     check that len(s') >= 0 && len(s') < len(s)

(function(exports) {

    exports.getMinDistance = function(sp1, sp2, latu, lonu, seconds){

        // Get the minimal distance of a GPS update U from a trip segment S
        // defined by endpoints SP1 and SP2.

        // Args:
        //     sp1 (obj): object with 'lat', 'lon' and 'time' attributes
        //     sp2 (obj): object with 'lat', 'lon' and 'time' attributes
        //     latu (float): fractional lat value of U
        //     lonu (float): fractional long value of U
        //     seconds (int): seconds since midnight


        // util.log('++++++++');
        // util.log(`- sp1 : ${sp1}`);
        // util.log(`- sp2 : ${sp2}`);
        // util.log(`-   u : (${latu}, ${lonu})`);
        // util.log(`+ secs: ${seconds}`);

        let lat0 = sp1['lat'];
        let lon0 = sp1['lon'];

        let lat1 = sp2['lat'];
        let lon1 = sp2['lon'];

        // find center lat

        let lmin = Math.min(lat0, lat1, latu);
        let lmax = Math.max(lat0, lat1, latu);
        let latc = (lmax + lmin) / 2;

        // util.log(`--------`);
        // util.log(`- lmin: ${lmin}`);
        // util.log(`- lmax: ${lmax}`);
        // util.log(`- latc: ${latc}`);

        // convert lat/long to cartesian
        // cl = math.cos(math.radians(latc))
        // x0 = util.EARTH_RADIUS_IN_FEET * lon0 * cl
        // y0 = util.EARTH_RADIUS_IN_FEET * lat0
        // x1 = util.EARTH_RADIUS_IN_FEET * lon1 * cl
        // y1 = util.EARTH_RADIUS_IN_FEET * lat1
        // x2 = util.EARTH_RADIUS_IN_FEET * lonu * cl
        // y2 = util.EARTH_RADIUS_IN_FEET * latu


        // util.log(`--------`);
        // util.log(`-  cl: ${cl}`);
        // util.log(`- sp1: (${x0}, ${y0})`);
        // util.log(`- sp1: (${x1}, ${y1})`);
        // util.log(`-   u: (${x2}, ${y2})`);

        let h1 = util.haversineDistance(lat0, lon0, latu, lonu);
        let h2 = util.haversineDistance(lat1, lon1, latu, lonu);

        // util.log(`--------`);
        // util.log(`- h1: ${h1}`);
        // util.log(`- h2: ${h2}`);

        // check proximity to segment end points
        // d1 = util.distance(x0, y0, x2, y2)
        // d2 = util.distance(x1, y1, x2, y2)

        // t1 = abs(sp1['time'] - seconds)
        // t2 = abs(sp2['time'] - seconds)

        // util.log(`--------`);
        // util.log(`- d1: ${d1}`);
        // util.log(`- d2: ${d2}`);

        let m = null;
        let m_ = null;

        // find segment slope and intersect
        if (lon0 === lon1){
            // vertical segment edge case
            m = Math.sign(lat1 - lat0) * Number.MAX_VALUE;
        } else{
            m = (lat1 - lat0) / (lon1 - lon0);
        }
        let b = lat0 - m * lon0;

        // find orthogonal slope and intersect
        if (m === 0){
            m_ = 0;
        }
        else{
            m_ = -(1.0 / m);
        }
        let b_ = latu - m_ * lonu;

        // find INTERSECTION_POINT of segment and orthogonal
        if (m === 0){
            lon = Number.MAX_VALUE;
        }
        else{
            lon = (b_ - b) / (m - m_);
        }
        lat = m * lon + b;

        // d3 = util.distance(x0, y0, x1, y1) # length of vector(SP1, SP2)
        // d4 = util.distance(x0, y0, x, y)   # length of vector(SP1, INTERSECTION_POINT)
        // d5 = util.distance(x, y, x2, y2)   # distance of U from INTERSECTION_POINT

        let h3 = util.haversineDistance(lat0, lon0, lat1, lon1); //length of vector(SP1, SP2);

        let h4 = Number.MAX_VALUE;
        if (lon !== Number.MAX_VALUE){
            h4 = util.haversineDistance(lat0, lon0, lat, lon);   // length of vector(SP1, INTERSECTION_POINT)
        }

        let h5 = Number.MAX_VALUE;
        if (lon !== Number.MAX_VALUE){
             h5 = util.haversineDistance(lon, lat, latu, lonu);   // distance of U from INTERSECTION_POINT
        }
        // util.log(`- d3: ${d3}`);
        // util.log(`- d4: ${d4}`);
        // util.log(`- d5: ${d5}`);

         // if intersection of orthogonal through U lies on S
        if (h4 < h3){
            return Math.min(h1, h2, h5);
        } else{
            return Math.min(h1, h2);
        }
    }
}(typeof exports === 'undefined' ? this.geo_util = {} : exports));
