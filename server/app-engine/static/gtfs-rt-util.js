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

    exports.MILLIS_PER_SECOND = 1000;
    exports.MILLIS_PER_MINUTE =   60 * exports.MILLIS_PER_SECOND;
    exports.MILLIS_PER_HOUR   =   60 * exports.MILLIS_PER_MINUTE;
    exports.MILLIS_PER_DAY    =   24 * exports.MILLIS_PER_HOUR;

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

    exports.getYYYYMMDD = function(date) {
        if (date === null) {
            date = new Date();
        }

        let month = ('' + (date.getMonth() + 1)).padStart(2, '0');
        let day = ('' + date.getDate()).padStart(2, '0');
        return '' + date.getFullYear() + '-' + month + '-' + day;
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
        if (testDow) {
            return testDow;
        } else return ((new Date()).getDay() + 6) % 7;
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
        let min = Math.floor(daySeconds / this.SECONDS_PER_MINUTE);

        let amPm = hour >= 12 ? ' pm' : ' am';

        if (hour == 0) hour = 12;
        else if (hour > 12) hour -= 12;

        let ms = ('' + min).padStart(2, '0');
        let aps = includeAMPM ? amPm : '';
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
        let buf = new ArrayBuffer(str.length);
        let bufView = new Uint8Array(buf);

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
                this.log('server response: ' + response.status + ' ' + response.statusText);
            } else {
                response.json().then(callback);
            }
        })
        .catch((error) => {
            this.log('*** fetch() error: ' + error);
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

            that.apiCall(msg, url, function(response) {
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
            });
        });
    };
}(typeof exports === 'undefined' ? this.util = {} : exports));
