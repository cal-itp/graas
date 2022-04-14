#! /bin/sh

FILES=`find ../../app-engine/ -name "*.js" ! -iname "html5-qrcode.min.js"`
NODE_PATH=../../node/node_modules npx eslint -c config.js $FILES
