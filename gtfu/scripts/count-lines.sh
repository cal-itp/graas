#! /bin/sh

if [ "$#" -ne 1 ]
then
  echo "usage: count-lines.sh <folder>"
  exit 0
fi

FILE=$1
COUNT=`wc -l $FILE | tail -1 | awk '{print $1}'`
echo $COUNT

