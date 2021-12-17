#! /bin/sh
# this script lists subnet-local hosts with IP addresses and host names. It is unlikely to work on anything but certain ATT uverse routers in its current state


DEVICE_LIST=`curl -s http://192.168.1.254/cgi-bin/devices.ha | grep -A 1 "^192.168.1" | grep -v "^--$"`
CANDIDATES=()
for i in $DEVICE_LIST
do
  if [ "$i" != "/" ]
  then
    if [[ $i == 192.168* ]]
    then
      #printf "$i\t"
      LINE="$i"
    else
      #printf "$i\n"
      LINE+=" $i"
      #echo LINE: $LINE
      CANDIDATES+=("$LINE")
    fi
  fi
done

COUNT=0
for i in "${CANDIDATES[@]}"
do
  echo "$COUNT) $i"
  COUNT=$((COUNT+1))
done
printf "> "
read LINE
#echo LINE: $LINE
IP=`echo "${CANDIDATES[$LINE]}" | awk '{print $1}'`
ssh pi@$IP
