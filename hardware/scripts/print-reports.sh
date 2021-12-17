#! /bin/sh

DAYS=7

if [ $# -eq 1 ]
then
  DAYS=$1
fi

echo DAYS: $DAYS
#exit 1

. /etc/environment
HOSTNAME=`hostname`
echo HOSTNAME: $HOSTNAME
for i in `find ~/logs/$HOSTNAME* -mtime -${DAYS}`
do
  python $GRASS_ROOT/log-check.py -csv $i
done
