import time
import board
import busio
import adafruit_adxl34x
import sys
import threading

lock = threading.Lock()
buf = []
max_buf_len = 0
delay = .1

def acc_snapshot():
    lock.acquire()
    result = buf.copy()
    lock.release()
    return result

def drive_acc(trace=False):
    i2c = busio.I2C(board.SCL, board.SDA)
    accelerometer = adafruit_adxl34x.ADXL345(i2c)

    while True:
        v = accelerometer.acceleration
        if trace:
            print(f'{v[0]} {v[1]} {v[2]}')
        lock.acquire()
        buf.append(v)
        if len(buf) >= max_buf_len:
            buf.pop(0)
        lock.release()
        time.sleep(delay)

def start_acc(max_len, d, trace=False):
    global delay, max_buf_len
    delay = d
    max_buf_len = max_len
    acc_thread = threading.Thread(target=drive_acc, args=([trace]))
    acc_thread.start()

if __name__ == '__main__':
    start_acc(100, .1, True);

