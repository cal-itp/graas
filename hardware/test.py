import os
import sys
import random
import time
import rcanvas
from rcanvas import RCanvas
import util

alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/='
setup_complete = False

def rnd(limit):
    return random.randrange(limit)

def heads():
    return rnd(100) < 50

def rstr(length):
    s = ''
    for i in range(length):
        s += alphabet[rnd(len(alphabet))]
    return s

def rcolor():
    argb = 0xff000000
    r = 128 + rnd(127)
    argb |= r << 16
    g = 128 + rnd(127)
    argb |= g << 8
    b = 128 + rnd(127)
    argb |= b

    return format(argb, 'x')

def test_frame(canvas, w, h):
    #util.debug(f'test_frame()')
    #util.debug(f'- w: {w}')
    #util.debug(f'- h: {h}')

    canvas.setColor('ff000020')
    canvas.drawRect(0, 0, w, h, True)

    canvas.setColor('ffff0000')

    for i in range(1000):
        canvas.drawCircle(rnd(w), rnd(h), 10, True if heads() else False)

    for i in range(20):
        canvas.setColor(rcolor())
        x = rnd(w)
        y = rnd(h)
        ww = int((w - x) * random.random())
        hh = int((h - y) * random.random())
        canvas.drawRect(x, y, ww, hh, True if heads() else False)

    for i in range(20):
        canvas.setColor(rcolor())
        x = rnd(w)
        y = rnd(h)
        text = rstr(1 + rnd(19))
        canvas.drawText(text, x, y)

    canvas.blit()

def handleCommand(args):
    util.debug(f'- args: {args}')
    global setup_complete
    setup_complete = True

def main(arg):
    canvas = RCanvas(rcanvas.COMM_TCP, handleCommand)
    w = int(arg[0])
    h = int(arg[1])

    while True:
        if setup_complete:
            test_frame(canvas, w, h)
        time.sleep(.1)

if __name__ == '__main__':
   main(sys.argv[1:])
