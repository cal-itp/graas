from subprocess import call
from google.cloud import ndb
import sys
import os

# local imports
import util

class agency(ndb.Model):
    agencyid = ndb.StringProperty('agency-id')
    publickey = ndb.StringProperty('public-key')

def generate(agencyid, path):
	client = ndb.Client()

	dirPath = path + agencyid
	tmp = dirPath + '/id_ecdsa.tmp'
	ecdsaPath = dirPath + '/id_ecdsa'
	pubpem = dirPath + '/id_ecdsa.pub.pem'
	pub = dirPath + '/id_ecdsa.pub'

	dirExists = os.path.exists(dirPath)
	if not dirExists:
		os.mkdir(dirPath)

	# These subprocess/calls should ideally be written in python. There are limitations to the python OpenSSL library but it may still be possible.
	# A better version of this section wouldn't create actual files - it could read the values straight into python variables
	call(['openssl','ecparam','-name','prime256v1','-genkey','-noout','-out',tmp])
	call(['openssl','pkcs8','-topk8','-nocrypt','-in',tmp,'-out',ecdsaPath])
	os.remove(tmp)
	call(['openssl','ec','-in',ecdsaPath,'-pubout','-out',pubpem])
	os.rename(pubpem, pub)

	# Edit private key title
	with open(ecdsaPath,'r') as file:
		temp = file.read()
	temp = temp.replace("PRIVATE KEY", "TOKEN")
	with open(ecdsaPath,'w') as file:
		file.write(agencyid  + '\n')
		file.write(temp)

	# Get public key text from file
	f = open(pub,'r')
	temp = f.read()
	temp = temp.replace("\n", "")
	temp = temp.replace("-----BEGIN PUBLIC KEY-----", "")
	id_ecdsa_pub = temp.replace("-----END PUBLIC KEY-----", "")

	# Create glcoud entity and add public key
	with client.context():
		print(f'creating entity for {agencyid} and adding public key')
		newAgency = agency()
		newAgency.populate(
			agencyid=agencyid,
			publickey=id_ecdsa_pub
			)
		key = newAgency.put()

	util.update_bucket_timestamp()

	return key

def main(argv):
	argc = len(sys.argv)
	path = ''
	agencyid = None

	for i in range(argc):
		arg = sys.argv[i]
		if arg == '-a' and i < argc - 1:
			i += 1
			agencyid = sys.argv[i]

		if arg == '-p' and i < argc - 1:
			i += 1
			path = sys.argv[i]

	if agencyid == None:
		print('* agencyid is required')
		exit(1)

	generate(agencyid,path)

if __name__ == '__main__':
	main(sys.argv[1:])