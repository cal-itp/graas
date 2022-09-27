let localStorage = this.localStorage;
let JSZip = this.JSZip;
let fs = null;
const memStorage = {};

if (typeof localStorage === 'undefined' || localStorage === null) {
    const LocalStorage = require('node-localstorage').LocalStorage;
    localStorage = new LocalStorage('./local-storage');
    JSZip = require('jszip');
    fetch = require('node-fetch');
    fs = require('fs');
}

(function(exports) {
    const MTIME_NAME = '__mtimes__';
    let mtimeMap = {};
    readMTimes();

    function readMTimes() {
        //console.log('readMTimes()');

        const content = localStorage.getItem(MTIME_NAME);
        if (!content) return;

        const lines = content.split('\n');

        for (let line of lines) {
            //console.log('-- line: ' + line);
            const i = line.indexOf(': ');
            const key = line.substring(0, i);
            //console.log('-- key: ' + key);
            const value = parseInt(line.substring(i + 2));
            //console.log('-- value: ' + new Date(value));

            mtimeMap[key] = value;
        }
    }

    function writeMTimes() {
        //console.log('writeMTimes()');

        let s = '';

        for (let key of Object.keys(mtimeMap)) {
            s += `${key}: ${mtimeMap[key]}\n`;
        }

        //console.log('- s: ' + s);
        localStorage.setItem(MTIME_NAME, s);
    }

    exports.getTextFileContents = function(path) {
        //console.log('platform.getTextFileContents()');
        //console.log('- path: ' + path);

        const content = localStorage.getItem(path);
        return content.split('\n');
    }

    exports.readFile = async function(path) {
        console.log('platform.readFile()');
        console.log('- path: ' + path);
        console.log('- util.isBrowser(): ' + util.isBrowser());

        if (util.isBrowser()) {
            if (path === 'gtfs.zip') {
                console.log('+ returning localStorage.getItem("gtfs.zip")');
                const content = localStorage.getItem(path);
                //console.log('- content: ' + JSON.stringify(content));
                return content;
            }

            if (!memStorage[path]) {
                const body = util.base642ab(localStorage.getItem('gtfs.zip'));
                const zip = await JSZip.loadAsync(body);

                memStorage[path] = await zip.file(path).async('string');
            }

            return memStorage[path];
        } else {
            return localStorage.getItem(path);
        }
    }

    exports.writeToFile = function(path, content) {
        console.log('platform.writeToFile()');
        console.log('- path: ' + path);
        console.log('- content.length: ' + content.length);
        //console.log('- content: ' + content);
        console.log('- Object.keys(localStorage): ' + Object.keys(localStorage));

        if (util.isBrowser()) {
            if (path === 'gtfs.zip') {
                localStorage.setItem(path, content);
                console.log('+ wrote gtfs.zip to local storage');
            } else {
                memStorage[path] = content;
            }
        } else {
            localStorage.setItem(path, content);
        }
        //const c = localStorage.getItem(path);
        //console.log('- c.length: ' + c.length);
        console.log('- Object.keys(localStorage): ' + Object.keys(localStorage));
        console.log('- Object.keys(memStorage): ' + Object.keys(memStorage));
        mtimeMap[path] = util.now();
        writeMTimes();
    }

    exports.getMTime = function(path) {
        //console.log('platform.getMTime()');
        //console.log('- path: ' + path);

        const value = mtimeMap[path];
        return value ? value : -1;
    }

    exports.resourceExists = function(path) {
        //console.log('platform.resourceExists()');
        //console.log('- path: ' + path);

        return localStorage.getItem(path) !== null;
    }

    exports.ensureResourcePath = function(path) {
        //console.log('platform.ensureResourcePath()');
        //console.log('- path: ' + path);

        // no op with this implementation
    }

    exports.copyFile = async function(srcPath, dstPath) {
        //console.log('platform.ensurcopyFileeResourcePath()');
        //console.log('- srcPath: ' + srcPath);
        //console.log('- dstPath: ' + dstPath);

        const content = await this.readFile(srcPath);
        this.writeFile(dstPath, content)
    }

    exports.unpackZip = async function(url, dstPath, files) {
        console.log('platform.unpackZip()');
        console.log('- url: ' + url);
        //console.log('- dstPath: ' + dstPath);
        //console.log('- files: ' + JSON.stringify(files));

        if (!util.isBrowser()) {
            let body = null;

            if (url.startsWith('http://') || url.startsWith('https://')) {
                body = await util.getResponseBody(url);
            } else {
                body = fs.readFileSync(url);
                //console.log('- typeof body:   ' + (typeof body));
                //console.log('- body.length:   ' + body.length);
            }

            const zip = await JSZip.loadAsync(body);
            //console.log('- files: ' + JSON.stringify(zip.files));

            const fnlist = Object.keys(zip.files);

            for (let f of fnlist) {
                //console.log(`-- ${f}`);
                const content = await zip.file(f).async('string');

                if (files.includes(f)) {
                    this.writeToFile(f, content + '\n');
                }
            }
        } else {
            //throw 'add browser implementation for unpackZip()';
            const b64 = await this.readFile(url);
            //console.log('- b64: ' + JSON.stringify(b64));
            console.log('- b64: ' + b64.substring(0, 64));
            const body = util.base642ab(b64);
            console.log('- body.byteLength: ' + body.byteLength);
            const zip = await JSZip.loadAsync(body);
            //console.log('- files: ' + JSON.stringify(zip.files));

            const fnlist = Object.keys(zip.files);
            console.log('- fnlist: ' + JSON.stringify(fnlist));

            for (let f of fnlist) {
                console.log(`-- ${f}`);
                const content = await zip.file(f).async('string');

                if (files.includes(f)) {
                    //this.writeToFile(f, util.ab2base64(util.str2ab(content + '\n')));
                    this.writeToFile(f, content + '\n');
                }
            }
        }
    }
}(typeof exports === 'undefined' ? this.platform = {} : exports));

