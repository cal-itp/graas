#! /bin/sh

### KEY_PATH contains folders that aren't agencies, like 'admin'. Enumerate storage bucket gtfs-aux folder
### instead to get a list of active agencies

KEY_PATH="../agency-config/keys"
for i in `ls $KEY_PATH`
do
  #echo $i
  KEY_FILE=$KEY_PATH/$i/id_ecdsa
  #echo KEY_FILE: $KEY_FILE
  if [ -f $KEY_FILE ]
  then
    #echo found key file $KEY_FILE
    LOCAL_STORAGE_STR=`$KEY_PATH/make-local-storage-string.sh $KEY_FILE`
    echo $LOCAL_STORAGE_STR
  fi
done
