#! /bin/sh
#
# usage: refresh-server-keys.sh [-local]
#   -local will set the target URL to localhost:8080/cmd
#
# description: this command triggers a reload of public keys from
# the data store into server memory. So the work flow for managing
# keys is to add/remove/edit keys using the cloud console, and
# then to run this command

URL="https://lat-long-prototype.wl.r.appspot.com/cmd"

if [ "$1" == "-local" ]; then
  URL="localhost:8080/cmd"
fi

echo "URL: $URL"
CMD="refresh-server-keys"
#echo "CMD: $CMD"
KEY=`keys/join-private-key-lines.sh keys/admin/id_ecdsa`
#echo "KEY: $KEY"
SIG=`python elliptic.py sign $CMD $KEY`
#echo "SIG: $SIG"
MSG="{\"cmd\": \"$CMD\", \"sig\": \"$SIG\"}"
#echo "MSG: $MSG"

RESULT=`curl -s -H 'Content-Type: application/json' --request POST --data "$MSG" $URL`
echo "server response: $RESULT"
