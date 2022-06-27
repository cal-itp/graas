import db

# tmp-1 MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1qXoVCyOO+XSOHucIYQqXE4nbKXm2BUV2PHKXVJiY3dg2+HVtmafp0R2ufeAD/QtUfYRC0Ls8hz0ycAfnYGKwg==
# tmp-2 MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESLctx+i80C56DRpQNQbf8HICkbDAbdplbIatiHVNcaXy4IlagIHgRoh8i4Aa9jG+9rUj3AJt4jOV/VpN7PDsvw==
# tmp-3 MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAENT3kW2qhCsP96YEugqr8sADndj1uJM9XV7DgkcISYIgsgkhaRK1xxm1Ak0X0FCxUtQ902mdAZXyAoSoHBL8HVg==

"""
e1 = Entity(Key('agency'))
e1['agency-id'] = 'tmp-1'
e1['public-key'] = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1qXoVCyOO+XSOHucIYQqXE4nbKXm2BUV2PHKXVJiY3dg2+HVtmafp0R2ufeAD/QtUfYRC0Ls8hz0ycAfnYGKwg=='

print(f'e1: {e1}')

e2 = Entity(Key('agency'))
e2['agency-id'] = 'tmp-2'
e2['public-key'] = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESLctx+i80C56DRpQNQbf8HICkbDAbdplbIatiHVNcaXy4IlagIHgRoh8i4Aa9jG+9rUj3AJt4jOV/VpN7PDsvw=='

print(f'e2: {e2}')

e3 = Entity(Key('agency'))
e3['agency-id'] = 'tmp-3'
e3['public-key'] = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAENT3kW2qhCsP96YEugqr8sADndj1uJM9XV7DgkcISYIgsgkhaRK1xxm1Ak0X0FCxUtQ902mdAZXyAoSoHBL8HVg=='

print(f'e3: {e3}')
"""

client = db.Client()
query = client.query(kind="agency")
results = list(query.fetch())
print(f'- results:')

for i in results:
    print(f'-- {i}')


