import RPi.GPIO as GPIO
import sys

def main(pin):
    print(f'main()')
    print(f'- pin: {pin}')

    GPIO.setmode(GPIO.BCM)
    GPIO.setwarnings(False)

    GPIO.setup(pin, GPIO.IN)
    value = GPIO.input(pin)
    print(f'- value: {value}')

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print(f'usage: {sys.argv[0]} <GPIO pin>')
        exit(1)

    main(int(sys.argv[1]))

