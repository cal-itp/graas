/*
This is a quick and dirty refactoring of web app untility code to also be usable by tests.

The module has references to the DOM 'document' attribute, which is bypassed under node, but
should really not be in here at all. For log(), a callback function could be set with an
initialize() call or similar, which would then do the DOM updates. For apiCall() and signAndPost(),
a single calback function can be passed in that takes 'response' as an argument and updates the
DOM based on the response.ok value

Finally, fetch() timeouts are currently not implemented under node.
*/

//console.log('- this.crypto: ' + this.crypto);
//console.log('- this.fetch: ' + this.fetch);

var crypto = this.crypto
var fetch = this.fetch
const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

if (!crypto) {
    crypto = require('crypto').webcrypto
}

if (!fetch) {
    fetch = require('node-fetch')
}

(function(exports) {
    exports.SECONDS_PER_MINUTE = 60;
    exports.SECONDS_PER_HOUR   = 60 * exports.SECONDS_PER_MINUTE;
    exports.SECONDS_PER_DAY    = 24 * exports.SECONDS_PER_HOUR;
    exports.SECONDS_PER_WEEK   =  7 * exports.SECONDS_PER_DAY;
    exports.SECONDS_PER_YEAR   =  365 * exports.SECONDS_PER_DAY;

    exports.MILLIS_PER_SECOND = 1000;
    exports.MILLIS_PER_MINUTE =   60 * exports.MILLIS_PER_SECOND;
    exports.MILLIS_PER_HOUR   =   60 * exports.MILLIS_PER_MINUTE;
    exports.MILLIS_PER_DAY    =   24 * exports.MILLIS_PER_HOUR;

    exports.EARTH_RADIUS_IN_FEET = 20902231;
    exports.FEET_PER_LAT_DEGREE = 364000;
    exports.FEET_PER_LONG_DEGREE = 288200;

    exports.log = function(s) {
        console.log(s);

        if (typeof document !== 'undefined') {
            try {
                let p = document.getElementById('console');
                if (p) p.innerHTML += s + "<br/>";
            } catch(e){
                console.log(e.message)
            }
        }
    };

    exports.now = function() {
        return (new Date()).getTime();
    }

    exports.getYYYYMMDD = function(date) {
        if (date === null) {
            date = new Date();
        }

        let month = ('' + (date.getMonth() + 1)).padStart(2, '0');
        let day = ('' + date.getDate()).padStart(2, '0');
        return '' + date.getFullYear() + '-' + month + '-' + day;
    }

    exports.getEpochSeconds = function(date) {
        if(date === null){
            return Date.now();
        } else {
            let year = date.substring(0, 4);
            let month = date.substring(4, 6);
            let day = date.substring(6, 8);
            let d = new Date(year, month - 1, day);
            return d.getTime()/1000;
        }
    }
    exports.getShortDate = function(date) {
        if (date === null) {
            date = new Date();
        }

        return DAY_NAMES[date.getDay()] + ', ' + MONTH_NAMES[date.getMonth()] + ' ' + date.getDate();
    }

    // returns a date object set to midnight of
    // the date described by 's', which is expected
    // to be of the form mm/dd/yy. yy is assumed to
    // refer to years of the 21st century.
    exports.getDate = function(s) {
        let args = s.split('/');
        if (args.length !== 3) return null;
        let date = new Date();
        date.setFullYear(2000 + parseInt(args[2]));
        date.setMonth(parseInt(args[0]) - 1);
        date.setDate(parseInt(args[1]));
        date.setHours(0, 0, 0);
        return date;
    }

    exports.getMidnightDate = function(date) {
        if (!date) date = new Date();
        let d = new Date();
        d.setTime(date.getTime());
        d.setHours(0, 0, 0);
        return d;
    }

    exports.nextDay = function(date) {
        let d = new Date();
        d.setTime(date.getTime() + this.MILLIS_PER_DAY);
        return d;
    }

    // returns 0 for Monday...and 6 for Sunday
    exports.getDayOfWeek = function() {
        return ((new Date()).getDay() + 6) % 7;
    }

    // returns date as an 8-character string (ie 20220317 for 3/17/22)
    exports.getTodayYYYYMMDD = function() {
        if (testDate) {
            return testDate;
        } else {
            let today = new Date();
            let year = today.getFullYear();
            let month = today.getMonth() + 1;
            let date = today.getDate();
            return (year * 10000) + (month * 100) + date;
        }
    }

    // 's' is assumed to be a time string like '8:23 am'
    exports.getTimeFromString = function(s) {
        if (s == null){
            this.log("* Time is null")
            return null;
        }
        let cap = s.match(/([0-9]+):([0-9]+) ([ap]m)/);

        let hour = parseInt(cap[1]);
        let min = parseInt(cap[2]);
        let ampm = cap[3];

        if (ampm === 'pm'){
            // 2:00pm becomes 14:00
            if (hour < 12) hour += 12;
            // note that 12:00pm stays 12:00
        }
        // 12:00am becomes 0:00
        else if (hour === 12) hour = 0;

        let date = new Date();
        date.setHours(hour, min);
        // this.log(" - date: " + date);
        return date;
    }

    // create H:MM string from day seconds, optionally include am/pm indicator
    exports.getHMForSeconds = function(daySeconds, includeAMPM) {
        let hour = Math.floor(daySeconds / this.SECONDS_PER_HOUR);
        daySeconds -= hour * this.SECONDS_PER_HOUR;
        const min = Math.floor(daySeconds / this.SECONDS_PER_MINUTE);

        const amPm = hour >= 12 ? ' pm' : ' am';

        if (hour == 0) hour = 12;
        else if (hour > 12) hour -= 12;

        const ms = ('' + min).padStart(2, '0');
        const aps = includeAMPM ? amPm : '';
        return `${hour}:${ms}${aps}`
    }

    // return the difference in minutes between 's' and the current time.
    exports.getTimeDelta = function(s) {
        let now = new Date();
        if (testHour && testMin) {
            now.setHours(testHour, testMin);
        }
        let tripTime = this.getTimeFromString(s);
        return Math.abs((tripTime - now) / 60000);
    }

    exports.millisToSeconds = function(millis) {
        return Math.floor(millis / 1000);
    }

    exports.isNullOrUndefined = function(object) {
        return object === null || typeof object === 'undefined';
    }

    exports.isNullUndefinedOrBlank = function(object) {
        return object === null || typeof object === 'undefined' || object === '';
    }

    exports.sign = function(msg, signatureKey) {
        //this.log("sign()");
        //this.log("- msg: " + msg);
        //this.log("- signatureKey: " + signatureKey);

        let keyType = 'unknown';

        if (signatureKey.length < 256) {
            keyType = "ECDSA";
        } else if (signatureKey.length < 3000) {
            keyType = "RSA";
        }

        return crypto.subtle.sign(keyType === "RSA"
            ? "RSASSA-PKCS1-v1_5"
            : {
                name: "ECDSA",
                hash: {name: "SHA-256"},
            },
            signatureKey,
            this.str2ab(msg)
        );
    };

    exports.str2ab = function(str) {
        const buf = new ArrayBuffer(str.length);
        const bufView = new Uint8Array(buf);

        for (let i = 0, strLen = str.length; i < strLen; i++) {
            bufView[i] = str.charCodeAt(i);
        }

        return buf;
    };

    exports.ab2str = function(ab) {
        let binary = '';
        let bytes = new Uint8Array(ab);
        let len = bytes.byteLength;

        for (let i = 0; i < len; i++) {
            binary += String.fromCharCode(bytes[i]);
        }

        return binary;
    };

    exports.timedFetch = function(url, opts, window) {
        //this.log('timedFetch()');
        //this.log('- url: ' + url);

        if (window && window.AbortController) {
            const controller = new AbortController();
            opts.signal = controller.signal;
            setTimeout(() => controller.abort(), 15000);
        }

        return fetch(url, opts);
    };

    exports.getJSONResponse = async function(url, data, key) {
        let args = {method: 'GET'};

        if (key) {
            let data_str = JSON.stringify(data);
            let buf = await this.sign(data_str, key);
            let sig = btoa(this.ab2str(buf));
            this.log('- sig: ' + sig);

            data = {
                data: data,
                sig: sig
            };
        }

        if (data) {
            args.method = 'POST';
            args.headers = {'Content-Type': 'application/json'};
            args.body = JSON.stringify(data);
        }

        let response = await this.timedFetch(url, args);
        this.log('- response: ' + JSON.stringify(response));
        let json = await response.json();
        this.log('- json: ' + JSON.stringify(json));

        return json;
    }

    exports.apiCall = function(data, url, callback, document) {
        //this.log("apiCall()");

        let body = JSON.stringify(data);
        // this.log("- body: " + body);

        let that = this;

        this.timedFetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: body
        })
        .then(function(response) {
            if (!response.ok) {
                if (document) {
                    try {
                        let p = document.getElementById('server-response');
                        p.innerHTML = 'Server response: ' + response.status + ' ' + response.statusText;
                    } catch(e) {
                        console.log(e.message);
                    }
                }
                that.log('server response: ' + response.status + ' ' + response.statusText);
            } else {
                response.json().then(callback);
            }
        })
        .catch((error) => {
            that.log('*** fetch() error: ' + error);
        });
    };

    exports.signAndPost = function(data, signatureKey, url, document) {
        this.log("util.signAndPost()");
        let data_str = JSON.stringify(data);
        // this.log('- data_str: ' + data_str);
        // this.log('- document: ' + document);

        let that = this;

        this.sign(data_str, signatureKey).then(function(buf) {
            let sig = btoa(that.ab2str(buf));
            // that.log('- sig: ' + sig);

            let msg = {
                data: data,
                sig: sig
            };

            that.log('- msg: ' + JSON.stringify(msg));

            const then = that.now();
            that.apiCall(msg, url, function(response) {
                const millis = that.now() - then;
                that.log('- millis: ' + millis);
                if (document) {
                    try {
                        let p = document.getElementById('last-update');
                        let now = new Date();
                        let hour = now.getHours();
                        let ampm = hour >= 12 ? "PM" : "AM";

                        if (hour > 12) hour -= 12;
                        if (hour === 0) hour = 12;

                        let time = hour + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds()) + " " + ampm;

                        p.innerHTML = 'Last update: ' + time;

                        p = document.getElementById('server-response');
                        p.innerHTML = 'Server response: ok';
                    } catch(e) {
                        console.log(e.message);
                    }
                }

                that.log('server response: ok');
                //that.log(`- response: ${JSON.stringify(response)}`);
            });
        });
    };
    exports.hhmmssToSeconds = function(str){
        arr = str.split(':');
        seconds = arr[0] * 60 * 60;
        seconds += arr[1] * 60;
        seconds += arr[2] * 1;
        return seconds;
    }

    exports.padIfShort = function(s){
        if(s.toString().length === 1){
            return "0" + s;
        } else return s;
    }

    exports.secondsToHhmmss = function(seconds){
        let hours = parseInt(seconds / 60 / 60);
        seconds -= hours * 60 * 60;
        let minutes = parseInt(seconds / 60);
        seconds -= minutes * 60;
        return `${hours}:${this.padIfShort(minutes)}:${this.padIfShort(seconds)}`;
    }

    exports.degreesToRadians = function(degrees){
        return degrees * (Math.PI/180);
    }

    exports.haversineDistance = function(lat1, lon1, lat2, lon2){
        let phi1 = this.degreesToRadians(lat1)
        let phi2 = this.degreesToRadians(lat2)
        let delta_phi = this.degreesToRadians(lat2 - lat1)
        let delta_lam = this.degreesToRadians(lon2 - lon1)
        let a = (Math.sin(delta_phi / 2) * Math.sin(delta_phi / 2)
            + Math.cos(phi1) * Math.cos(phi2)
            * Math.sin(delta_lam / 2) * Math.sin(delta_lam / 2))
        let c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return c * this.EARTH_RADIUS_IN_FEET;
    }

    exports.getFeetAsLatDegrees = function(feet){
        return feet / this.FEET_PER_LAT_DEGREE;
    }

    exports.getFeetAsLongDegrees = function(feet){
        return feet / this.FEET_PER_LONG_DEGREE;
    }

// Thanks to: https://www.bennadel.com/blog/1504-ask-ben-parsing-csv-strings-with-javascript-exec-regular-expression-command.htm
    exports.csvToArray = function(str, delimiter = ",") {
      //  replace \r\n with \n, to ensure consistent newline characters
        str = str.replace(/\r\n/g,"\n");
      // slice from start of text to the first \n index
      // use split to create an array from string by delimiter
        const headers = str.slice(0, str.indexOf("\n")).split(delimiter);

      // slice from \n index + 1 to the end of the text
      // use split to create an array of each csv value row
        const rows = str.slice(str.indexOf("\n") + 1).split("\n");
      // Map the rows
      // split values from each row into an array
      // use headers.reduce to create an object
      // object properties derived from headers:values
      // the object passed as an element of the array
        const arr = rows.map(function (row) {
        const values = row.split(delimiter);
        const el = headers.reduce(function (object, header, index) {
          object[header] = values[index];
          return object;
        }, {});
        return el;
      });

      // return the array (hacky fix for null last row added)
      return arr.slice(0,-1);
    }

}(typeof exports === 'undefined' ? this.util = {} : exports));
