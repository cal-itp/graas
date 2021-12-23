def parse(names, line):
    result = {}
    ci = 0
    s = ''
    i = 0

    while i < len(line):
        c = line[i]

        if c == ',':
            result[names[ci]] = s
            ci += 1
            s = ''
            i += 1
            continue

        if c == '"':
            i += 1
            while i < len(line):
                c = line[i]
                if c == '"':
                    if i + 1 < len(line) and line[i + 1] == '"':
                        s += '"'
                        i += 2
                        continue
                    else:
                        i += 1
                        break
                s += c
                i += 1
            continue

        s += c
        i += 1

    result[names[ci]] = s
    return result

class DictReader:
    def __init__(self, f):
        s = f.readline().strip()

        self.names = s.split(',')
        self.rows = []
        self.rowIndex = 0

        while True:
            line = f.readline()
            if not line:
                break

            line = line.strip()
            if len(line) == 0:
                continue

            self.rows.append(parse(self.names, line.strip()))

    def __iter__(self):
        return self

    def __next__(self):
        if self.rowIndex >= len(self.rows):
            raise StopIteration

        row = self.rows[self.rowIndex]
        self.rowIndex += 1
        return row

class CSVLine:
    def __init__(self, s):
        self.names = s.split(',')

    def parse(self, line):
        return parse(self.names, line)

