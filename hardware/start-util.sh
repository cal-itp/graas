#! /bin/bash

EPOCH=`date "+%s"`
exec 2> /home/pi/logs/log-util-$EPOCH.txt
exec 1>&2
set -x

### start ssh agent and add repo key
eval "$(ssh-agent -s)"
ssh-add /home/pi/.ssh/id_ed25519

### remove all log files older than one week
find /home/pi/logs -mtime +7 -exec sudo rm -f {} \;

