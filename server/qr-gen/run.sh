#! /bin/sh

if [ $# -ne 2 ]; then
  echo "usage: run.sh <path-to-qr-text> <caption>"
  exit 0
fi

gradle run --args="$1 \"$2\""
