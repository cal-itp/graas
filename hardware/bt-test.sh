#! /bin/sh

sudo bluetoothctl power on
sleep 1
sudo bluetoothctl discoverable on
sleep 1
sudo bluetoothctl pairable on
sleep 1
sudo hciconfig hci0 piscan
sleep 1

