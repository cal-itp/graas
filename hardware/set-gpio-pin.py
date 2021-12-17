import RPi.GPIO as GPIO
import sys

def main(pin, value):
    print(f'main()')
    print(f'- pin: {pin}')
    print(f'- value: {value}')

    GPIO.setmode(GPIO.BCM)
    GPIO.setwarnings(False)

    GPIO.setup(pin, GPIO.OUT)
    GPIO.output(pin, GPIO.LOW if value == 0 else GPIO.HIGH)

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(f'usage: {sys.argv[0]} <GPIO pin> <value>')
        exit(1)

    main(int(sys.argv[1]), int(sys.argv[2]))

