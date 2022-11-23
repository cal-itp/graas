const fs = require('fs');

class TeeStream  {
    constructor(filename, savedStdOut) {
        if (filename) {
            this.stream = fs.createWriteStream(filename);
        }

        this.savedStdOut = savedStdOut;
    }

    write(chunk, encoding, callback) {
        if (this.stream) {
            this.stream.write(chunk, encoding, callback);
        }

        this.savedStdOut.write(chunk, encoding, callback);
    }
}

class Tee {
    constructor() {
        this.savedStdOut = process.stdout;
        this.stream = new TeeStream(null, this.savedStdOut);
    }

    redirect(filename) {
        this.stream = new TeeStream(filename, this.savedStdOut);
    }
}

module.exports = Tee;