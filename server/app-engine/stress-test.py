from subprocess import call
import sys
import shutil
import os
import requests
import json
import base64
import time
import datetime
import random
import threading
from google.cloud import datastore
from elliptic import elliptic_curve
from google.cloud import ndb

# local imports
import util
import keygen

class agency(ndb.Model):
    agencyid = ndb.StringProperty('agency-id')
    publickey = ndb.StringProperty('public-key')

class test_agency:
	def __init__(self, id, vehicleCount, client):
		self.id = id
		self.client = client
		print(f'creating agency {self.id}')

		self.vehicleList = []
		for i in range (vehicleCount):
			self.vehicleList.append(id + "-" + str(i))

	def keyGen(self):
		ecdsaPath = 'stress-test/' + self.id + '/id_ecdsa'
		self.key = keygen.generate(self.id,'stress-test/')

		id_ecdsa = ''
		with open(ecdsaPath, 'r') as file:
			# Convert to list
			lines = file.read().splitlines(True)
		line_count = len(lines)
		# Remove first 2 lines (agencyid & token start) and last line (token end)
		lines = lines[2:line_count-1]
		id_ecdsa = id_ecdsa.join(lines)
		id_ecdsa = id_ecdsa.replace("\n", "")
		print(f'id_ecdsa: {id_ecdsa}')
		self.id_ecdsa = id_ecdsa

	def startService(self, domain, intervalTime, intervalVariation, numRepeats):
		print(f'starting thread for agency {self.id}')
		global post_metadata_list

		for i in range(numRepeats):

			for vehicleid in self.vehicleList:
				post_metadata_list.append(self.post(domain, vehicleid))

			intervalFloor = intervalTime - intervalVariation
			intervalCeiling = intervalTime + intervalVariation
			sleepTime = random.uniform(intervalFloor, intervalCeiling)
			time.sleep(sleepTime)

	def post(self, domain, vehicleid):
		print('post()')
		endpoint = 'new-pos-sig'
		url = domain + endpoint
		startTime = util.get_current_time_millis()
		post_data = {
	        "uuid": "stresstest",
	        "agent":
	        [
	            "(Macintosh; Intel Mac OS X 10_15_7)"
	        ],
	        "timestamp": startTime,
	        "lat": 37.83915227205035,
	        "long": -122.28377128957112,
	        "speed": 0,
	        "heading": 0,
	        "accuracy": 65,
	        "version": "0.14 (11/09/21)",
	        "trip-id": "stresstest",
	        "agency-id": self.id,
	        "vehicle-id": vehicleid,
	        "pos-timestamp": startTime
		}
		post_data_string = json.dumps(post_data,separators=(',',':'))
		base64 = elliptic_curve.sign(post_data_string,self.id_ecdsa)

		message = {
			'data': post_data,
			'sig': base64
		}
		print(f'posting update for: {self.id}')
		response = requests.post(url, json = message, verify=False)
		responseTime = response.elapsed.total_seconds()
		statusCode = response.status_code
		responseJson = response.json()
		status = responseJson['status']
		print(f'status: {status}')
		print(f'response time: {str(responseTime)}')
		print(f'status code: {str(statusCode)}')
		return post_metadata(self.id, vehicleid, startTime, responseTime, statusCode, status)

	def clean(self):
		print(f'deleting entity and local files for {self.id}')
		shutil.rmtree('stress-test/'+self.id)
		with self.client.context():
			self.key.delete()

# Creating a class instance per post seems janky. A better data structure would allow for easier aggregation as well.
class post_metadata:
    def __init__(self, agencyid, vehicleid, startTime, responseTime, statusCode, status):
        self.agencyid = agencyid
        self.vehicleid = vehicleid
        self.startTime = startTime
        self.responseTime = responseTime
        self.statusCode = statusCode
        self.status = status

post_metadata_list = []

def main(argv):
	argc = len(sys.argv)
	# How many agencies to create
	agencyCount = 1
	# How many vehicles per agency
	vehicleCount = 5
	# Seconds between updates
	intervalTime = 3
	# Acceptable random variation in interval time
	intervalVariation = 1
	# How many updates per agency
	numRepeats = 1
	domain = None

	for i in range(argc):
	    arg = sys.argv[i]

	    if arg == '-l':
	        domain = 'https://127.0.0.1:8080/'

	    if arg == '-p':
	        domain = 'https://lat-long-prototype.wl.r.appspot.com/'

	    if arg == '-ac' and i < argc - 1:
	        i += 1
	        agencyCount = int(sys.argv[i])

	    if arg == '-vc' and i < argc - 1:
	        i += 1
	        vehicleCount = int(sys.argv[i])

	    if arg == '-i' and i < argc - 1:
	        i += 1
	        intervalTime = int(sys.argv[i])

	    if arg == '-iv' and i < argc - 1:
	        i += 1
	        intervalVariation = int(sys.argv[i])

	    if arg == '-r' and i < argc - 1:
	        i += 1
	        numRepeats = int(sys.argv[i])

	if domain == None:
		print('* usage: use -l for local server or -p for production server')
		exit(1)

	print(f'- domain: {domain}')
	print(f'- agencyCount: {agencyCount}')
	print(f'- vehicleCount: {vehicleCount}')
	print(f'- intervalTime: {intervalTime}')
	print(f'- intervalVariation: {intervalVariation}')
	print(f'- numRepeats: {numRepeats}')

	startTime = util.get_current_time_millis()
	agencyList = []
	dirName = 'stress-test'
	client = ndb.Client()

	# Create agencies & vehicles
	for i in range(agencyCount):
		agencyID = "stress-test-agency-" + str(i)
		agencyList.append(test_agency(agencyID, vehicleCount, client))


	# Generate keys for each agency
	dirExists = os.path.exists(dirName)
	if not dirExists:
		os.mkdir(dirName)

	for agency in agencyList:
		agency.keyGen()

	# Send initial post, to trigger key update. Without this post, a multi-agency test for N agencies has N-1 failures.
	# TODO: create more elegant solution
	agencyList[0].post(domain,agencyList[0].vehicleList[0])

	# Start service!!
	threads = list()
	for agency in agencyList:
		x = threading.Thread(target=agency.startService, args=(domain, intervalTime, intervalVariation, numRepeats))
		threads.append(x)
		x.start()

	# Wait for all of them to finish
	for x in threads:
		x.join()

	# Clean up file tree & remove keys from gcloud
	for agency in agencyList:
		agency.clean()
	shutil.rmtree(dirName)

	# Reporting from local logging. The process of looping through logs and incrementing values is a little janky. Could be improved with a better data stracture.
	totalResponseTime = 0
	maxResponseTime = None
	minResponseTime = None
	updates = 0
	successes = 0
	verificationIssues = 0
	httpIssues = 0
	for data in post_metadata_list:
		totalResponseTime += data.responseTime
		updates +=1

		if(data.status == 'ok' and data.statusCode == 200):
			successes += 1

		if data.status != 'ok':
			verificationIssues += 1

		if data.statusCode != 200:
			httpIssues += 1

		if(maxResponseTime == None or data.responseTime > maxResponseTime):
			maxResponseTime = data.responseTime

		if(minResponseTime == None or data.responseTime < minResponseTime):
			minResponseTime = data.responseTime

		# print("----------")
		# print(f'- agencyid: {data.agencyid}')
		# print(f'- vehicleid: {data.vehicleid}')
		# print(f'- startTime: {datetime.datetime.fromtimestamp(data.startTime).strftime("%Y-%m-%d %H:%M:%S")}')
		# print(f'- responseTime: {str(data.responseTime)}')
		# print(f'- statusCode: {str(data.statusCode)}')
		# print(f'- status: {str(data.status)}')

	print('Wrapping up...')
	gcloudUpdates = 0
	# Reporting from gcloud
	for agency in agencyList:
		datastore_client = datastore.Client()
		query = datastore_client.query(kind='position')
		query.add_filter('agency-id', '=', agency.id)
		query.add_filter('timestamp', '>=', startTime)
		positions = query.fetch()
		gcloudUpdates += len(list(positions))

	print("------Summary------")
	print(f'- Initiated: {str(len(post_metadata_list))}')
	print(f'- Accepted: {str(successes)}')
	print(f'- Verification issues: {str(verificationIssues)}')
	print(f'- HTTP issues: {str(httpIssues)}')
	print(f'- Avg response time: {str(round(totalResponseTime/updates,3))} secs')
	print(f'- Min response time: {str(round(minResponseTime,3))} secs')
	print(f'- Max response time: {str(round(maxResponseTime,3))} secs')

if __name__ == '__main__':
   main(sys.argv[1:])