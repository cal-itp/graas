#! /bin/sh

RANDOM=$(date +%s)
N=$RANDOM
curl http://storage.googleapis.com/graas-resources/debugging/debuggee.txt?foo=$N
