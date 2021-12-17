#! /bin/sh

if [ $# -ne 1 ]
then
  echo "usage: $0 <debuggee-name>"
  exit 1
fi

NAME=$1
echo NAME: $NAME

echo $NAME > /tmp/debuggee.txt
gsutil -m cp /tmp/debuggee.txt gs://graas-resources/debugging
