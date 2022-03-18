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
    exports.MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

    exports.log = function(s) {
        console.log(s);

        if (typeof document !== 'undefined') {
            try {
                var p = document.getElementById('console');
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

        var month = ('' + (date.getMonth() + 1)).padStart(2, '0');
        var day = ('' + date.getDate()).padStart(2, '0');
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
        var args = s.split('/');
        if (args.length !== 3) return null;
        var date = new Date();
        date.setFullYear(2000 + parseInt(args[2]));
        date.setMonth(parseInt(args[0]) - 1);
        date.setDate(parseInt(args[1]));
        date.setHours(0, 0, 0);
        return date;
    }

    exports.getMidnightDate = function(date) {
        if (!date) date = new Date();
        var d = new Date();
        d.setTime(date.getTime());
        d.setHours(0, 0, 0);
        return d;
    }

    exports.nextDay = function(date) {
        var d = new Date();
        d.setTime(date.getTime() + util.MILLIS_PER_DAY);
        return d;
    }


    // returns 0 for Monday...and 6 for Sunday
    exports.getDayOfWeek = function() {
        if (testDow) {
            return testDow;
        } else return ((new Date()).getDay() + 6) % 7;
    }

    // returns date as an 8-character string (ie 20220317 for 3/17/22)
    exports.getDate = function() {
        if (testDate) {
            return testDate;
        } else {
            var today = new Date();
            var year = today.getFullYear();
            var month = today.getMonth() + 1;
            var date = today.getDate();
            return (year * 10000) + (month * 100) + date;
        }
    }

    // 's' is assumed to be a time string like '8:23 am'
    function getTimeFromString(s) {
        if (s == null){
            util.log("* Time is null")
            return null;
        }
        var cap = s.match(/([0-9]+):([0-9]+) ([ap]m)/);

        var hour = parseInt(cap[1]);
        var min = parseInt(cap[2]);
        var ampm = cap[3];

        if (ampm === 'pm'){
            // 2:00pm becomes 14:00
            if (hour < 12) hour += 12;
            // note that 12:00pm stays 12:00
        }
        // 12:00am becomes 0:00
        else if (hour === 12) hour = 0;

        var date = new Date();
        date.setHours(hour, min);
        // util.log(" - date: " + date);
        return date;
    }

    // return the difference in minutes between 's' and the current time.
    exports.getTimeDelta = function(s) {
        var now = new Date();
        if (testHour && testMin) {
            now.setHours(testHour, testMin);
        }
        var tripTime = getTimeFromString(s);
        return Math.abs((tripTime - now) / 60000);
    }

    exports.millisToSeconds = function(millis) {
        return Math.floor(millis / 1000);
    }

    exports.sign = function(msg, signatureKey) {
        //this.log("sign()");
        //this.log("- msg: " + msg);
        //this.log("- signatureKey: " + signatureKey);

        var keyType = 'unknown';

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
        var binary = '';
        var bytes = new Uint8Array(ab);
        var len = bytes.byteLength;

        for (var i = 0; i < len; i++) {
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
        var args = {method: 'GET'};

        if (key) {
            var data_str = JSON.stringify(data);
            var buf = await this.sign(data_str, key);
            var sig = btoa(this.ab2str(buf));
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

        var response = await this.timedFetch(url, args);
        this.log('- response: ' + JSON.stringify(response));
        var json = await response.json();
        this.log('- json: ' + JSON.stringify(json));

        return json;
    }

    exports.apiCall = function(data, url, callback, document) {
        //this.log("apiCall()");

        var body = JSON.stringify(data);
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
                        var p = document.getElementById('server-response');
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
        var data_str = JSON.stringify(data);
        // this.log('- data_str: ' + data_str);
        // this.log('- document: ' + document);

        var that = this;

        this.sign(data_str, signatureKey).then(function(buf) {
            var sig = btoa(that.ab2str(buf));
            // that.log('- sig: ' + sig);

            var msg = {
                data: data,
                sig: sig
            };

            that.log('- msg: ' + JSON.stringify(msg));

            that.apiCall(msg, url, function(response) {
                if (document) {
                    try {
                        var p = document.getElementById('last-update');
                        var now = new Date();
                        var hour = now.getHours();
                        var ampm = hour >= 12 ? "PM" : "AM";

                        if (hour > 12) hour -= 12;
                        if (hour === 0) hour = 12;

                        var time = hour + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds()) + " " + ampm;

                        p.innerHTML = 'Last update: ' + time;

                        var p = document.getElementById('server-response');
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
