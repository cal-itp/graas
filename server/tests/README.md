Running tests requires node and npm, tested with versions 16.1.0 and 7.11.2, respectively

node_modules is no longer checked in, but can be regenerated with 'npm install' issued from the node folder

Run local test:
NODE_PATH=../node/node_modules node post-position-update.js -u https://127.0.0.1:8080 -a test -e TEST_KEY
