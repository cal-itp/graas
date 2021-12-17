#! /bin/sh

if [ "$#" -ne 1 ]; then
  echo "usage: shape-count.sh <agency-id>"
  exit 1
fi

cat /tmp/gtfu-cache/$1/shapes.txt | awk 'BEGIN { FS = "," } {print $1}' | grep -v shape_id | sort -u | wc -l

