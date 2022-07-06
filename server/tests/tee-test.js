const Tee = require('./tee');
const { Console } = require('node:console');

const tee = new Tee();
const console = new Console(tee, process.stderr);

const args = process.argv.slice(2);
console.log(`- args: ${args}`);

for (a of args) {
    tee.redirect(a)
    console.log(`- a: ${a}`);
}

tee.redirect();
console.log('the end?');

