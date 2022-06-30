import db
import json

e = db.Entity(db.Key('foo'))
e['bar'] = 'florb'
e['my_key'] = db.Key('test')
print(f'- e: {e}')
print(f'- e.key: {e.key}')
print(f'- e["my_key"]: {e["my_key"]}')
data = json.dumps(e, default=db.serialize)
print(f'- data: {data}')
e2 = db.Entity(line=data)
print(f'- e2: {e2}')
print(f'- e2.key: {e2.key}')
print(f'- e2["my_key"]: {e2["my_key"]}')
