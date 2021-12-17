#! /bin/sh

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <path-to-file>" >&2
  exit 1
fi

FIRST_LINE=`cat $1 | head -1`
RESULT=`echo $FIRST_LINE | grep BEGIN`

SAVED_IFS=$IFS
IFS=$'\n'

if [ -z "$RESULT" ]; then
  # first line in token file is agency id, skip
  LINES=`tail -n +2 $1`
else
  # first line is token header, good to go
  LINES=`cat $1`
fi


for i in $LINES
do
  if [[ $i != -----* ]]; then
    printf "%s" $i
  fi
done
echo

IFS=$SAVED_IFS

