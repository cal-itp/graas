Running tests requires node and npm, tested with versions 16.1.0 and 7.11.2, respectively

node_modules is no longer checked in, but can be regenerated with 'npm install' issued from the node folder

Run tests:
`NODE_PATH=../node/node_modules node test-position-update.js -u <server-url> -a <agency-name> -e <agency-key-env-variable-name>`
`NODE_PATH=../node/node_modules node post-service-alerts.js -u <server-url> -a <agency-name> -e <agency-key-env-variable-name>`

`NODE_PATH=../node/node_modules node tee-test.js tee-test-1.txt tee-test-2.txt tee-test-3.txt`
