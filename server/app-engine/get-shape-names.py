import csv
import sys

def main(argv):
    file_name = argv[0]
    print('- file_name: ' + str(file_name))

    names = {}

    with open(file_name) as f:
        reader = csv.reader(f)
        next(reader) # skip column names
        for row in reader:
            if row[0] in names:
                n = names[row[0]]
            else:
                n = 0
            n += 1
            names[row[0]] = n

    for k in names.keys():
        print(str(k) + ': ' + str(names[k]))

if __name__ == '__main__':
    main(sys.argv[1:])

