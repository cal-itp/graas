#! /bin/sh

EPOCH=`date "+%s"`
exec 2> /home/pi/logs/log-bt-$EPOCH.txt
exec 1>&2
set -x

echo "sleeping..."
sleep 10
echo "done"

sudo bluetoothctl power on
sleep 1
sudo bluetoothctl discoverable on
sleep 1
sudo bluetoothctl pairable on
sleep 1
sudo hciconfig hci0 piscan
sleep 1
cd /home/pi/projects/bt
python rcanvas_server.py

