from Crypto.PublicKey import RSA
from Crypto.Signature import PKCS1_v1_5
from Crypto.Hash import SHA256
from base64 import b64decode

def main():
    pub_key = 'MIIBigKCAYEA33oSE4a0XZ7/skH9R/oKfCWXRCA4W9YSZXIsY+zyAAOh3S3LuWa+fMI9HSo4WPjkEYxGZdCdslwS+SrJ/Jn7AiqytZqXOCkFC2paW9Up+PXS12XyEjk5F74z1FfW0KwR2VZdxhx90FoWEfaOke17wGYzMNFTLtePoe0+2Tter9uCIpGmL7Oz0bagRYANRRBLfixHj32GkvU2pfJ9mq2v9GRdUO2SkD4JwRG1fTV++IvXbfjgXs6ZSeNuIgRrXjKwGYqnKBFbCDlimmBeEqr3C3JHF+E+WYPhG3pfdJ/mOO7vnfS39sL1WDkam1b/Ms4owkv5gO2x4NMY8RRH25k05N9PCcIpqm4W/3JoenfuvB6SNs/VYXrznyuA29PYRcJDXitZu5FZwMq7GwgWZf0Lf99ptU2p89o4Sn+zY4j99n/RYfS1BqGydPENNfhIV6qzQVPmF/NxXEkGNMWzNr3VNP7JpIfe2mslWSM2hXiYfElYxp9N9261hmVHJfVBW8BHAgMBAAE='
    signature = 'iyRKcZP4fWKjtIcgi/FLZagz8GQbe/V5TgmgpX1B7Ug2N0XudFTS1yEVcJ1Ydkp62FHM+dkTQGTLUvm3ESIHqoXJVO0DC76FVkCNTm9DdZ3jlbUr/pqorZMcjqk0x8E1JyhwXYtJ8kHAaLsU5/jigC7dnslCcmgD9hMctawgGEzS4QNaKojYuxnpzhw3pvrXdTnPohYOEPUahXIouB4PVermx6KAnUIx/yejpeCQ0vGQZWBOJwSN8R4VGJYrIbXcFDsb4iwb/zO46mWxfqmPPyToXVlr+ImX/rB9RUyKIFWvk3MPPJ21HPmtB7gzYvOgujHAOayernjimv0EzspUUSCeHNuGDG7q58Lz4eIHAJ9AxAPAhI2piYOy0p6BEdyBsvjDYRFaLdj6lM/wdISBsQ7t7v3UpTLPgI8q0/JPRsQ/cYklTUDh7OCBX2ticzZKpRxfulqlkCUkAJIKEeYSRKh+QMn9mpY2xFYxFJBgNRUb5u3Sv+I2GaAMWHlirbL0'
    data = '{"uuid": "[object HTMLParagraphElement]", "agent": "(Macintosh; Intel Mac OS X 10_15_5)", "timestamp": "1597776489", "lat": "0", "long": "0", "speed": "0", "heading": "0", "trip-id": "6045495", "agency-id": "santa-barbara-clean-air-express", "vehicle-id": "187", "pos-timestamp": "0", "accuracy": "0"}'
    rsakey = RSA.importKey(b64decode(pub_key))
    signer = PKCS1_v1_5.new(rsakey)
    digest = SHA256.new()
    digest.update(data.encode('utf-8'))
    if signer.verify(digest, b64decode(signature)):
        print('verified')
    else:
        print('failed')

if __name__ == '__main__':
   main()
