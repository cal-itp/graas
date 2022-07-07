#! /bin/sh

rm -f ad-hoc ad-hoc.pub
ssh-keygen -t ecdsa -m PKCS8 -b 256 -f ad-hoc -N ""
cat /dev/null > ad-hoc.bak
echo "test" >> ad-hoc.bak
cat ad-hoc | sed 's/PRIVATE KEY/TOKEN/' >> ad-hoc.bak
openssl ec -in ad-hoc -pubout -out ad-hoc.pub
mv ad-hoc.bak ad-hoc
KEY=`cat ad-hoc.pub | grep -v PUBLIC | tr -d '\n'`
echo KEY: $KEY
AGENCY_ENTRY="{\"key\": {\"kind\": \"agency\", \"id\": \"6eafccf63d138c3e83084d0f70371bf6\"}, \"agency-id\": \"test\", \"public-key\": \"$KEY\"}"
echo AGENCY_ENTRY: $AGENCY_ENTRY
BLOCK_DATA=`cat dummy-block-assigments.json | jq -c | sed 's/"/\\\"/g'`
echo BLOCK_DATA: $BLOCK_DATA
ASSIGNMENT_SUMMARY="{\"key\": {\"kind\": \"assignment-summary\", \"id\": \"939fb2f84f753bb07107a33035216135\"}, \"data\": \"[{\\\"block_id\\\": \\\"6\\\", \\\"vehicle_id\\\": \\\"4001\\\"}]\"}"
echo ASSIGNMENT_SUMMARY: $ASSIGNMENT_SUMMARY
BLOCK_LIST="{\"key\": {\"kind\": \"block-list\", \"id\": \"35b648d0320e6a3568016d48a7395702\"}, \"data\": \"$BLOCK_DATA\"}"
echo BLOCK_LIST: $BLOCK_LIST
VALID_DATE=`date +%Y-%m-%d`
echo VALID_DATE: $VALID_DATE
BLOCK_METADATA="{\"key\": {\"kind\": \"block-metadata\", \"id\": \"f1b0d20ed9a9b34ae5c6f6b765027726\"}, \"created\": 1656530573026, \"agency_id\": \"test\", \"valid_date\": \"$VALID_DATE\", \"assignment_summary_key\": {\"kind\": \"assignment-summary\", \"id\": \"939fb2f84f753bb07107a33035216135\"}, \"block_list_key\": {\"kind\": \"block-list\", \"id\": \"35b648d0320e6a3568016d48a7395702\"}}"
echo BLOCK_METADATA: $BLOCK_METADATA
