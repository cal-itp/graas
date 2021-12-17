#! /bin/sh
LINE_COUNT=`wc -l $1 | awk '{print $1}'`
echo "line count: " $LINE_COUNT
UNIQUES=`cat $1 | cut -d"," -f2,3 | sort -u | wc -l`
echo "uniques   : " $UNIQUES

