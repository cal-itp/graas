const fs = require('fs');

class Tee extends fs.WriteStream {
    constructor() {
        super(new Uint8Array());
    }

    redirect(fn) {
        if (fn) {
            this.stream = fs.createWriteStream(fn);
        } else {
            this.stream = null;
        }
    }

    write(chunk, encoding, callback) {
        if (this.stream) {
            this.stream.write(chunk, encoding, callback);
        }

        process.stdout.write(chunk, encoding, callback);
    }
}

module.exports = Tee;