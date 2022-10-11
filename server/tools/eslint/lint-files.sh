#! /bin/sh

# Omitting html5-qrcode as we don't want to make changes to this outside library.
# TODO: create directory of external libraries to omit from linting
FILES=`find ../../app-engine/ -name "*.js" ! -iname "html5-qrcode.min.js" ! -name "jszip.js"`
NODE_PATH=../../node/node_modules npx eslint -c config.js $FILES
