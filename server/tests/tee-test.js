const Tee = require('./tee')
const tee = new Tee();

function log(s) {
    tee.stream.write(s + '\n');
}

const args = process.argv.slice(2);
log(`- args: ${args}`);

for (a of args) {
    tee.redirect(a)
    log(`- a: ${a}`);
}