#! /bin/sh

FILES=`find ../../app-engine/ -name "*.js"`
NODE_PATH=../../node/node_modules npx eslint -c config.js $FILES
