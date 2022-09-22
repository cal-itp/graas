let localStorage = this.localStorage;
let JSZip = null;
let fs = null;

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

    exports.readFile = function(path) {
        //console.log('platform.readFile()');
        //console.log('- path: ' + path);

        return localStorage.getItem(path);
    }

    exports.writeToFile = function(path, content) {
        //console.log('platform.writeToFile()');
        //console.log('- path: ' + path);
        //console.log('- content.length: ' + content.length);

        localStorage.setItem(path, content);
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

    exports.copyFile = function(srcPath, dstPath) {
        //console.log('platform.ensurcopyFileeResourcePath()');
        //console.log('- srcPath: ' + srcPath);
        //console.log('- dstPath: ' + dstPath);

        const content = this.readFile(srcPath);
        this.writeFile(dstPath, content)
    }

    exports.unpackZip = async function(url, dstPath, files) {
        //console.log('platform.unpackZip()');
        //console.log('- url: ' + url);
        //console.log('- dstPath: ' + dstPath);
        //console.log('- files: ' + JSON.stringify(files));

        if (/*util.runningInNode()*/ typeof window === 'undefined') {
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
            throw 'add browser implementation for unpackZip()';
        }
    }
}(typeof exports === 'undefined' ? this.platform = {} : exports));

