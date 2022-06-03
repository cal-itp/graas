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

import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# local imports
import util
import keygen

LOCAL_SERVER_URL = 'https://127.0.0.1:8080/'
thread_lock = threading.Lock()
datastore_client = datastore.Client()

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

	def variable_sleep(self, intervalTime, intervalVariation):
		intervalFloor = intervalTime - intervalVariation
		intervalCeiling = intervalTime + intervalVariation
		sleepTime = random.uniform(intervalFloor, intervalCeiling)
		time.sleep(sleepTime)

	def startVehicle(self, domain, intervalTime, intervalVariation, numRepeats, vehicleid):
		global post_metadata_list

		time.sleep(intervalTime * random.uniform(0, 1))
		session = requests.Session()

		for i in range(numRepeats):
			data = self.post(session, domain, vehicleid, util.get_current_time_millis())

			if data.startTime != 0:
				thread_lock.acquire()
				post_metadata_list.append(data)
				thread_lock.release()

			self.variable_sleep(intervalTime, intervalVariation)

	def startService(self, domain, intervalTime, intervalVariation, numRepeats):
		print(f'starting thread for agency {self.id}')

		threads = list()

		for vehicleid in self.vehicleList:
			t = threading.Thread(target=self.startVehicle, args=(domain, intervalTime, intervalVariation, numRepeats, vehicleid))
			t.start()
			threads.append(t)

		for t in threads:
			t.join()

	def post(self, session, domain, vehicleid, timestamp):
		print('post()')
		endpoint = 'new-pos-sig'
		url = domain + endpoint
		postTime = timestamp
		post_data = {
	        "uuid": "stresstest",
	        "agent":
	        [
	            "(Macintosh; Intel Mac OS X 10_15_7)"
	        ],
	        "timestamp": postTime,
	        "lat": 37.83915227205035,
	        "long": -122.28377128957112,
	        "speed": 0,
	        "heading": 0,
	        "accuracy": 65,
	        "version": "0.14 (11/09/21)",
	        "trip-id": "stresstest",
	        "agency-id": self.id,
	        "vehicle-id": vehicleid,
	        "pos-timestamp": postTime
		}
		post_data_string = json.dumps(post_data,separators=(',',':'))
		base64 = elliptic_curve.sign(post_data_string,self.id_ecdsa)

		message = {
			'data': post_data,
			'sig': base64
		}
		print(f'posting update for: {self.id}')
		then = time.time()
		response = session.post(url, json = message, verify=False)
		# 'elapsed' only measures time until response headers are parsed, per https://stackoverflow.com/a/30445723/7396017
		# responseTime = response.elapsed.total_seconds()
		responseTime = time.time() - then
		statusCode = response.status_code
		responseJson = response.json()
		status = responseJson['status']
		print(json.dumps(responseJson))
		print(f'status: {status}')
		print(f'response time: {responseTime}')
		print(f'status code: {statusCode}')
		return post_metadata(self.id, vehicleid, postTime, responseTime, statusCode, status)

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
	domain = LOCAL_SERVER_URL

	if argc != 2:
		print('* usage: python stress-test.py <path-to-config-json-file>')
		exit(1)

	jsonInput = sys.argv[1]

	if '.json' not in jsonInput:
		print('* error: input must be a JSON file')
		exit(1)

	f = open(jsonInput)
	data = json.load(f)
	f.close()

	domain = data.get('server-url', LOCAL_SERVER_URL)

	# Load attributes from json. If absent, use default values defined above
	agencyCount = data.get('agency-count', agencyCount)
	vehicleCount = data.get('vehicle-count', vehicleCount)
	intervalTime = data.get('interval-time', intervalTime)
	intervalVariation = data.get('interval-variation', intervalVariation)
	numRepeats = data.get('num-repeats', numRepeats)

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

	# 1.  Create agencies & vehicles
	for i in range(agencyCount):
		agencyID = "stress-test-" + str(startTime) + "-agency-" + str(i)
		agencyList.append(test_agency(agencyID, vehicleCount, client))

	# 2. Generate keys for each agency
	dirExists = os.path.exists(dirName)
	if not dirExists:
		os.mkdir(dirName)

	for agency in agencyList:
		agency.keyGen()

	# 3. Start service!!
	threads = list()
	for agency in agencyList:
		x = threading.Thread(target=agency.startService, args=(domain, intervalTime, intervalVariation, numRepeats))
		threads.append(x)

	#    Start each thread
	then = time.time()
	for x in threads:
		x.start()

	#    Wait for all of them to finish
	for x in threads:
		x.join()
	totalNetRuntime = time.time() - then

	# 4. Clean up file tree & remove agency data from gcloud
	keys = []
	session = requests.Session()

	for agency in agencyList:
		agency.post(session, domain, "x", 0)
		agency.clean()

		query = datastore_client.query(kind='current-position')
		query.add_filter('agency-id', '=', agency.id)
		positions = query.fetch()
		for pos in positions:
			keys.append(pos.key)

	datastore_client.delete_multi(keys)
	shutil.rmtree(dirName)

	# Reporting from local logging. The process of looping through logs and incrementing values is a little janky. Could be improved with a better data stracture.
	totalResponseTime = 0
	maxResponseTime = None
	minResponseTime = None
	updates = 0
	successes = 0
	verificationIssues = 0
	httpIssues = 0
	vehicleCounts = {}

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

		if data.vehicleid in vehicleCounts:
			vehicleCounts[data.vehicleid] += 1
		else:
			vehicleCounts[data.vehicleid] = 1

		# print("----------")
		# print(f'- agencyid: {data.agencyid}')
		# print(f'- vehicleid: {data.vehicleid}')
		# print(f'- startTime: {datetime.datetime.fromtimestamp(data.startTime).strftime("%Y-%m-%d %H:%M:%S")}')
		# print(f'- responseTime: {str(data.responseTime)}')
		# print(f'- statusCode: {str(data.statusCode)}')
		# print(f'- status: {str(data.status)}')

	print(vehicleCounts)

	print('Wrapping up...')
	gcloudUpdates = 0
	# Reporting from gcloud
	for agency in agencyList:
		query = datastore_client.query(kind='position')
		query.add_filter('agency-id', '=', agency.id)
		query.add_filter('timestamp', '>=', startTime)
		positions = query.fetch()
		gcloudUpdates += len(list(positions))

	print("------Summary------")
	print(f'- Initiated: {len(post_metadata_list)}')
	print(f'- Accepted: {successes}')
	print(f'- In gcloud: {gcloudUpdates}')
	print(f'- Verification issues: {verificationIssues}')
	print(f'- HTTP issues: {httpIssues}')
	print(f'- Avg response time: {totalResponseTime/updates:.3f} secs')
	print(f'- Min response time: {minResponseTime:.3f} secs')
	print(f'- Max response time: {maxResponseTime:.3f} secs')
	print(f'- Total net runtime (updates only, not including setup and teardown:\n{totalNetRuntime:.3f} secs')

if __name__ == '__main__':
   main(sys.argv[1:])