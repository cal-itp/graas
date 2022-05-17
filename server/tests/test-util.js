const crypto = require('crypto').webcrypto
const util = require('../app-engine/static/gtfs-rt-util');
const fetch = require('node-fetch');

(function(exports) {
    exports.getBaseName = function(s) {
        const index = s.lastIndexOf('/');

        if (index < 0) return s;
        else return s.substring(index + 1);
    }

    exports.getResponseBody = async function(url) {
        const requestSettings = {
            method: 'GET'
        };

        const response = await fetch(url, requestSettings);

        const blob = await response.blob();
        //util.log(`- blob: ${blob}`);

        const arrayBuf = await blob.arrayBuffer();
        //util.log(`- arrayBuf.byteLength: ${arrayBuf.byteLength}`);

        const body = Buffer.from(arrayBuf);
        //util.log(`- body: ${JSON.stringify(body)}`);

        return body;
    }

    exports.getSignatureKey = async function(ecdsaVarName) {
        const base64 = process.env[ecdsaVarName];
        console.log(base64);
        if (!base64) throw `unset environment variable ${ecdsaVarName}`;

        const key = atob(base64);
        util.log("- key.length: " + key.length);

        const binaryDer = util.str2ab(key);

        return await crypto.subtle.importKey(
            "pkcs8",
            binaryDer,
            {
                name: "ECDSA",
                namedCurve: "P-256"
            },
            false,
            ["sign"]
        );
    }

    exports.sleep = function(ms) {
        return new Promise((resolve) => {
            setTimeout(resolve, ms);
        });
    }

    exports.getEpochSeconds = function() {
        return Math.floor(Date.now() / 1000);
    }
}(typeof exports === 'undefined' ? this.testutil = {} : exports));

