import qrcode
from qrcode.image.pure import PyPNGImage
from PIL import ImageDraw
import base64
from io import BytesIO

### DO NOT COMMIT!
data = '''
plumas-transit-systems
-----BEGIN TOKEN-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgNFFLI4qOIP+UbKeG
pEv+Dn5XRQIkbNDSBtwfeYasb4WhRANCAAQFsFP7nrb8Dcff8eAdVo8Ia8dvyGm9
LHPXb6v+EeEpo8ynaDmkkxLrPzTZJbqxkgLtkBHaFgOBcpj+u+kGRL3u
-----END TOKEN-----
'''

'''
'''

print(f'- data: {data}')

# xxx img = qrcode.make(data)

qr = qrcode.QRCode(
    version=1,
    error_correction=qrcode.constants.ERROR_CORRECT_L,
    box_size=5,
    border=7,
)
qr.add_data(data)
qr.make(fit=True)

img = qr.make_image(fill_color=(0, 105, 0), back_color=(255, 255, 255))
print(f'- type(img): {type(img)}')

ImageDraw.Draw(
    img
).text(
    (135, 7),  # Coordinates
    'Plumas 7/17/23',
    (0, 105, 0)  # Color
)

buf = BytesIO()
img.save(buf, format="PNG")
b64 = base64.b64encode(buf.getvalue()).decode()

#print(f'- b64: {b64}')

html = f'''
<html>
    <img src="data:image/png;base64,{b64}">
</html>
'''

#print(f'- html: {html}')

with open('index.html', mode='w') as f:
    f.write(html)

img.save('qr.png')
