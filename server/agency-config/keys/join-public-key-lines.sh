#! /bin/sh

if [ "$#" -ne 1 ]; then
  echo "usage: join-public-key-lines.sh <path-to-file>" >&2
  exit 1
fi

SAVED_IFS=$IFS
IFS=$'\n'

for i in `cat $1`
do
  if [[ $i != -----* ]]; then
    printf "%s" $i
  fi
done
echo

IFS=$SAVED_IFS

