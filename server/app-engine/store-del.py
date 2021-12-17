from google.cloud import datastore
import warnings
import sys, getopt, time

warnings.filterwarnings("ignore", "Your application has authenticated using end user credentials")

BATCH_SIZE = 500

def main(argv):
    datastore_client = datastore.Client()
    query = datastore_client.query(kind='position')
    positions = query.fetch()
    key_list = []
    count = 0
    for pos in positions:
        #print('- pos: ' + str(pos))
        #print('- pos.id: ' + str(pos.id))

        if 'timestamp' in pos and isinstance(pos['timestamp'], str):
            key_list.append(pos.key)
            if len(key_list) == BATCH_SIZE:
                datastore_client.delete_multi(key_list)
                key_list = []
                count += BATCH_SIZE
                print(str(count))

    if len(key_list) > 0:
        datastore_client.delete_multi(key_list)


if __name__ == '__main__':
   main(sys.argv[1:])
