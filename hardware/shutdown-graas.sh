#! /bin/sh

sleep_with_counter() {
  max_count=$1
  count=0

  while [ $count -lt $max_count ]
  do
    sleep 1
    count=$(( $count + 1))
    echo $count
  done
}

# shut down client
CLIENT_PID=`ps -ef | grep graas.cfg | grep -v grep | awk '{print $2}'`
echo CLIENT_PID: $CLIENT_PID
if [ "$CLIENT_PID" != "" ]
then
  sudo kill $CLIENT_PID
fi

. /etc/environment
. /home/pi/venv/graas/bin/activate

python $GRASS_ROOT/set-gpio-pin.py 6 1
sleep_with_counter 13
python $GRASS_ROOT/set-gpio-pin.py 6 0
sleep_with_counter 25

python $GRASS_ROOT/set-gpio-pin.py 6 1
sleep_with_counter 13
python $GRASS_ROOT/set-gpio-pin.py 6 0
sleep_with_counter 25

sudo reboot
