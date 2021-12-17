#! /bin/bash

EPOCH=`date "+%s"`
exec 2> /home/pi/logs/log-debuggee-$EPOCH.txt
exec 1>&2
set -x

echo "waiting for system to come online..."
sleep 10

. /etc/environment
cd $GRASS_ROOT
GRASS_TUNNEL=$GRASS_TUNNEL python debuggee.py

