#! /bin/bash

FILENAME="/home/pi/logs/`hostname`-`date '+%y-%m-%d-%H-%M'`.txt"
exec 2> $FILENAME
exec 1>&2
set -x

echo "waiting for system to come online..."
sleep 10
sudo chgrp bluetooth /var/run/sdp

source /home/pi/venv/graas/bin/activate
. /etc/environment
cd $GRASS_ROOT
python graas-bt.py $GRASS_NETWORK_ARG -c /home/pi/doc/graas.cfg

