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

pushd ../qr-gen
./run.sh ../app-engine/ad-hoc "AD HOC"
popd
mv ../qr-gen/qr.png ad-hoc.png
echo "created QR code file ad-hoc.png"
