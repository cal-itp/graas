import ecdsa
from google.cloud import ndb
import sys
import os

# local imports
import util

class agency(ndb.Model):
    agencyid = ndb.StringProperty('agency-id')
    publickey = ndb.StringProperty('public-key')

def generate(agencyid):
	print(f'- ecdsa.__version__: {ecdsa.__version__}')

	sk = ecdsa.SigningKey.generate(curve=ecdsa.NIST256p)
	token = sk.to_pem(format='pkcs8').decode('utf-8')
	token = token.replace("PRIVATE KEY", "TOKEN")
	print(f'token:\n{agencyid}\n{token}')

	vk = sk.get_verifying_key()
	vkpem = vk.to_pem().decode('utf-8')
	vkpem = vkpem.replace("\n", "")
	vkpem = vkpem.replace("-----BEGIN PUBLIC KEY-----", "")
	vkpem = vkpem.replace("-----END PUBLIC KEY-----", "")
	print(f'public key:\n{vkpem}')

	print()
	print('++++++++++++++++++++++++++++++++++++++')
	print('+++ make sure to add keys to vault +++')
	print('++++++++++++++++++++++++++++++++++++++')

	# Create gcloud entity and add public key
	client = ndb.Client()

	with client.context():
		print(f'creating entity for {agencyid} and adding public key')
		newAgency = agency()
		newAgency.populate(
			agencyid=agencyid,
			publickey=vkpem
			)
		key = newAgency.put()

	util.update_bucket_timestamp()

	return key

def usage(argv):
	print('*\n* usage: keygen -a <agency-id>\n*')
	exit(1)

def main(argv):
	argc = len(sys.argv)
	agencyid = None

	for i in range(argc):
		arg = sys.argv[i]

		if arg == '-a' and i < argc - 1:
			i += 1
			agencyid = sys.argv[i]

	if agencyid == None:
		usage(argv)

	print('retrieving list of existing public keys...', end='')
	key_map = util.read_public_keys(verbose=False)
	print('done')
	key = key_map.get(agencyid, None)

	if key:
		print(f'*\n* keys for agency "{agencyid}" already exist, aborting\n*')
		exit(1)

	generate(agencyid)

if __name__ == '__main__':
	main(sys.argv[1:])