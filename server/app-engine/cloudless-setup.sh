#! /bin/sh

# cloudless setup script, for evaluation purposes only, not suitable for production
# - create key pair,
# - add public key to cloudless DB simulator
# - create QR code from private key
#
# see $REPOSITORY_ROOT/docs/new-instance-setup.md for details

rm -f ad-hoc ad-hoc.pub
ssh-keygen -t ecdsa -m PKCS8 -b 256 -f ad-hoc -N ""
cat /dev/null > ad-hoc.bak
echo "test" >> ad-hoc.bak
cat ad-hoc | sed 's/PRIVATE KEY/TOKEN/' >> ad-hoc.bak
openssl ec -in ad-hoc -pubout -out ad-hoc.pub
mv ad-hoc.bak ad-hoc
KEY=`cat ad-hoc.pub | grep -v PUBLIC | tr -d '\n'`
echo KEY: $KEY

cat /dev/null > db-sim.json
AGENCY_ENTRY="{\"key\": {\"kind\": \"agency\", \"id\": \"6eafccf63d138c3e83084d0f70371bf6\"}, \"agency-id\": \"test\", \"public-key\": \"$KEY\"}"
echo $AGENCY_ENTRY >> db-sim.json

pushd ../qr-gen
./run.sh ../app-engine/ad-hoc "AD HOC"
popd
mv ../qr-gen/qr.png ad-hoc.png
echo "created QR code file ad-hoc.png"
