#! /bin/sh

copy_folder() {
  echo "copy_folder()"
  echo "- \$1: $1"
  echo "- \$2: $2"
  FULLPATH=$2/$1

  if [ ! -f $FULLPATH/route-names.json ]; then
    echo "* fatal error: $FULLPATH must contain 'route-names.json'"
    exit 1
  fi

  RET=`cat $FULLPATH/route-names.json | python -m json.tool > /dev/null`

  if [ $? -ne 0 ]; then
    echo '* fatal error: route-names.json is not proper JSON'
    exit 1
  fi

# Filter-params is optional since the app will use default values if it's not included
if [ -f $FULLPATH/filter-params.json ]; then
    RET=`cat $FULLPATH/filter-params.json | python -m json.tool > /dev/null`

    if [ $? -ne 0 ]; then
      echo '* fatal error: filter-params.json is not proper JSON'
      exit 1
    fi
  fi

  if [ ! -f $FULLPATH/vehicle-ids.json ]; then
    echo "* fatal error: $FULLPATH must contain 'vehicle-ids.json'"
    exit 1
  fi

  RET=`cat $FULLPATH/vehicle-ids.json | python -m json.tool > /dev/null`

  if [ $? -ne 0 ]; then
    echo '* fatal error: vehicle-ids.json is not proper JSON'
    exit 1
  fi

  if [ -f $FULLPATH/driver-names.json ]; then
    RET=`cat $FULLPATH/driver-names.json | python -m json.tool > /dev/null`

    if [ $? -ne 0 ]; then
      echo '* fatal error: driver-names.json is not proper JSON'
      exit 1
    fi
  fi

  # For new instances of GRaaS, replace 'graas-resources' with a globally unique directory name
  gsutil -m cp -r /$FULLPATH gs://graas-resources/gtfs-aux
}

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <agency-id>|all-operators" >&2
  exit 1
fi

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

if [ "$1" == "all-operators" ]; then
  for i in `ls $SCRIPTPATH/gtfs-aux`
  do
    copy_folder $i $SCRIPTPATH/gtfs-aux
  done
else
  if [ ! -d $SCRIPTPATH/gtfs-aux/$1 ]; then
    echo "* fatal error: operator folder $SCRIPTPATH/gtfs-aux/$1 does not exist"
    exit 1
  fi
  copy_folder $1 $SCRIPTPATH/gtfs-aux
fi

