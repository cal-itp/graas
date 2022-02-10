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

if (!crypto) {
    crypto = require('crypto').webcrypto
}

if (!fetch) {
    fetch = require('node-fetch')
}

(function(exports) {
    exports.log = function(s) {
        console.log(s);

        if (document) {
            try {
                var p = document.getElementById('console');
                p.innerHTML += s + "<br/>";
            } catch(e){
                console.log(e.message)
            }
        }
    };

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

                //that.log('server response: ok');
            });
        });
    };
}(typeof exports === 'undefined' ? this.util = {} : exports));
