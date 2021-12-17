import RPi.GPIO as GPIO
import time
import sys
import threading

lock = threading.Lock()
current_pattern = [0.1, 0]
next_pattern = None

def set_led_pattern(pattern):
    lock.acquire()
    global next_pattern
    next_pattern = pattern
    lock.release()

def drive_led():
    index = 0

    global current_pattern, next_pattern

    GPIO.setup(18, GPIO.OUT)

    while True:
        #print("LED on")
        GPIO.output(18, GPIO.HIGH)
        time.sleep(current_pattern[index])
        index += 1
        #print("LED off")
        GPIO.output(18, GPIO.LOW)
        time.sleep(current_pattern[index])
        index += 1
        if index >= len(current_pattern):
            index = 0

        lock.acquire()
        if not next_pattern is None:
            current_pattern = next_pattern
            index = 0
        next_pattern = None
        lock.release()

def start_led():
    led_thread = threading.Thread(target=drive_led, args=())
    led_thread.start()

if __name__ == '__main__':
    GPIO.setmode(GPIO.BCM)
    GPIO.setwarnings(False)
    start_led();
    set_led_pattern([0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.9]);

