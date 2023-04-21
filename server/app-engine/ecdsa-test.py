import ecdsa

#sk = ecdsa.SigningKey.generate(curve=ecdsa.SECP256k1)
#print(f'- ecdsa: {dir(ecdsa)}')
print(f'- ecdsa.__version__: {ecdsa.__version__}')
sk = ecdsa.SigningKey.generate(curve=ecdsa.NIST256p)
skpem = sk.to_pem(format='pkcs8').decode('utf-8')
print(f'- skpem:\n{skpem}')
vk = sk.get_verifying_key()
vkpem = vk.to_pem().decode('utf-8')
print(f'- vkpem:\n{vkpem}')
