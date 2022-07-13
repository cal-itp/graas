let localStorage = this.localStorage;
let Zip = null;
let fs = null;

if (typeof localStorage === "undefined" || localStorage === null) {
    const LocalStorage = require('node-localstorage').LocalStorage;
    localStorage = new LocalStorage('./local-storage');
    Zip = require('adm-zip');
    fetch = require('node-fetch');
    fs = require('fs');
}

if (typeof util === "undefined" || util === null) {
    var util = require('./gtfs-rt-util');
}

(function(exports) {
    const MTIME_NAME = '__mtimes__';
    let mtimeMap = {};
    readMTimes();

    function readMTimes() {
        util.log('readMTimes()');

        const content = localStorage.getItem(MTIME_NAME);
        const lines = content.split('\n');

        for (let line of lines) {
            util.log('-- line: ' + line);
            const i = line.indexOf(': ');
            const key = line.substring(0, i);
            util.log('-- key: ' + key);
            const value = parseInt(line.substring(i + 2));
            util.log('-- value: ' + new Date(value));

            mtimeMap[key] = value;
        }
    }

    function writeMTimes() {
        util.log('writeMTimes()');

        let s = '';

        for (let key of Object.keys(mtimeMap)) {
            s += `${key}: ${mtimeMap[key]}`;
        }

        util.log('- s: ' + s);
        localStorage.putItemd(MTIME_NAME, s);
    }

    exports.getTextFileContents = function(path) {
        util.log('platform.getTextFileContents()');
        util.log('- path: ' + path);

        const content = localStorage.getItem(path);
        return content.split('\n');
    }

    exports.readFile = function(path) {
        util.log('platform.readFile()');
        util.log('- path: ' + path);

        return localStorage.getItem(path);
    }

    exports.writeToFile = function(path, content) {
        util.log('platform.writeToFile()');
        util.log('- path: ' + path);
        util.log('- content.length: ' + content.length);

        localStorage.putItem(path, content);
        mtimeMap[path] = util.now();
        writeMTimes();
    }

    exports.getMTime = function(path) {
        util.log('platform.getMTime()');
        util.log('- path: ' + path);

        const value = mtimeMap[path];
        return value ? value : -1;
    }

    exports.resourceExists = function(path) {
        util.log('platform.resourceExists()');
        util.log('- path: ' + path);

        return localStorage.getItem(path) !== null;
    }

    exports.ensureResourcePath = function(path) {
        util.log('platform.ensureResourcePath()');
        util.log('- path: ' + path);

        // no op with this implementation
    }

    exports.copyFile = function(srcPath, dstPath) {
        util.log('platform.ensurcopyFileeResourcePath()');
        util.log('- srcPath: ' + srcPath);
        util.log('- dstPath: ' + dstPath);

        const content = this.readFile(srcPath);
        this.writeFile(dstPath, content)
    }

    exports.unpackZip = async function(url, dstPath, files) {
        util.log('platform.unpackZip()');
        util.log('- url: ' + url);
        util.log('- dstPath: ' + dstPath);
        util.log('- files: ' + files);

        if (util.runningInNode()) {
            let body = null;

            if (url.startsWith('http://') || url.startsWith('https://')) {
                body = await util.getResponseBody(url);
            } else {
                body = fs.readFileSync(url, 'utf8');
            }

            const zipFile = new Zip(body);
            const zipEntries = zipFile.getEntries();
            util.log('- zipEntries.length: ' + zipEntries.length);

            zipEntries.forEach((entry) => {
                util.log('-- entry.entryName: ' + entry.entryName);

                if (files.includes(entry.entryName)) {
                    this.writeFile(entry.entryName, zipFile.readAsText(entry));
                }
            });
        } else {
            throw 'add browser implementation for unpackZip()';
        }
    }
}(typeof exports === 'undefined' ? this.platform = {} : exports));

