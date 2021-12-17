import os
import json
from datetime import datetime
import sys
from urllib import request, parse
import requests
from requests import get
import time
import traceback
import uuid
import socket
import threading
import util

try:
    import bluetooth as bt
except ImportError:
    print('* bluetooth module not found')


COMM_BT = 'bt'
COMM_TCP = 'tcp'
TCP_PORT = 8942

client_sock = None
text_table = {}
widget_table = {}
handleCommandMethod = None
handleButtonMethod = None

def is_initialized():
    return len(text_table) > 0

def center_text_horizontally(w):
    if text_table is None or len(text_table) == 0:
        return 0
    for key in text_table:
        return int((w - text_table[key][0]) / 2)

def center_text_vertically(h):
    #return int((h + next(iter(text_table))[1]) / 2)
    if text_table is None or len(text_table) == 0:
        return 0
    for key in text_table:
        return int((h + text_table[key][1]) / 2)

def scan_bluetooth():
    while True:
        util.debug('BT scan')
        os.system('hciconfig hci0 piscan')
        time.sleep(30)

def start_bluetooth():
#    try:
        print(f'start_bluetooth()')
        uuid = "bdeafb56-fbe4-4bfb-8743-af0a84f3142b"
        server_sock = bt.BluetoothSocket(bt.RFCOMM)

        server_sock.bind(("", bt.PORT_ANY))
        server_sock.listen(1)

        port = server_sock.getsockname()[1]
        bt.advertise_service(server_sock, "BabyShark",
                           service_id=uuid,
                           service_classes=[uuid, bt.SERIAL_PORT_CLASS],
                           profiles=[bt.SERIAL_PORT_PROFILE])

        while True:
            print("Waiting for connection on RFCOMM channel %d" % port)

            try:
                global client_sock
                client_sock, client_info = server_sock.accept()
                print("Accepted connection from " +  str(client_info))

                cmd_list = []
                line = ''

                while True:
                    c = client_sock.recv(1).decode('utf-8')
                    #print(f'-- c: {c}')

                    if c == '\n' and len(line) > 0:
                        #print(f'-- line: {line}')
                        cmd_list.append(line)
                        if line == 'nop':
                            handleMessage(cmd_list)
                            cmd_list.clear()
                        line = ''
                    else:
                        line += c

            except KeyboardInterrupt:
                print("Server going down")

                if client_sock is not None:
                    client_sock.close()

                server_sock.close()
                break

            except:
                util.debug(f'* exception: {sys.exc_info()[0]}')
                traceback.print_exc()


def start_tcp():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(('127.0.0.1', TCP_PORT))
        s.listen()

        while True:
            print(f'waiting for connection on TCP port {TCP_PORT}')

            try:
                global client_sock
                client_sock, addr = s.accept()
                with client_sock:
                    print(f'connection from {addr}')

                    cmd_list = []
                    line = ''

                    while True:
                        c = client_sock.recv(1).decode('utf-8')
                        #print(f'-- c: {c}')

                        if c == '\n' and len(line) > 0:
                            line = line.strip()
                            ##print(f'-- line: "{line}"')
                            cmd_list.append(s)
                            if line == 'nop':
                                handleMessage(cmd_list)
                                cmd_list.clear()
                            line = ''
                        else:
                            line += c

            except KeyboardInterrupt:
                print("Server going down")

                if client_sock is not None:
                    client_sock.close()

                s.close()
                break

            except:
                util.debug(f'* exception: {sys.exc_info()[0]}')
                traceback.print_exc()

class RCanvas:
    def __init__(self, comm, handleCommand, handleButton=None):
        global handleCommandMethod
        handleCommandMethod = handleCommand
        global handleButtonMethod
        handleButtonMethod = handleButton
        self.draw_buf = ''

        if comm == COMM_BT:
            scan_thread = threading.Thread(target=scan_bluetooth, args=())
            scan_thread.start()

            bt_thread = threading.Thread(target=start_bluetooth, args=())
            bt_thread.start()
        elif comm == COMM_TCP:

            tcp_thread = threading.Thread(target=start_tcp, args=())
            tcp_thread.start()
        else:
            raise ValueError(f'unknown comm mode: {comm}')

    def drawText(self, text, x, y):
        self.draw_buf += f'draw-text {util.to_b64(text)} {x} {y}\n'

    def drawLabel(self, text, x, y):
        s = ''

        s += f'set-color {util.LIGHT}\n'
        s += f'draw-text {util.to_b64(text)} {x} {y}\n'

        self.draw_buf += s

    def drawTextField(self, text, x, y, w, h, color=util.LIGHT):
        s = ''

        s += f'set-color {util.LIGHT}\n'
        s += f'draw-rect {x} {y} {w} {h} false\n'
        s += f'set-color {color}\n'
        s += f'draw-text {util.to_b64(text)} {x + 25} {y + center_text_vertically(h)}\n'

        self.draw_buf += s

    def drawButton(self, text, color, x, y, w, h):
        widget_table[text] = (x, y, w, h, True)
        s = ''

        dims = text_table.get(text, None)
        #print(f'+++ dims: {dims}')

        if dims is None:
            util.debug(f'* no dims found for \'{util.to_b64(text)}\'')
            xx = x + 25
            yy = y + 25
        else:
            xx = int(x + (w - dims[0]) / 2)
            #yy = int(y + (h + dims[1]) / 2)
            yy = y + center_text_vertically(h)

        s += f'set-color {util.LIGHT}\n'
        s += f'draw-rect {x} {y} {w} {h} true\n'
        s += f'set-color {color}\n'
        s += f'draw-text {util.to_b64(text)} {xx} {yy}\n'

        self.draw_buf += s

    def getTextDimensions(self, text):
        self.draw_buf += f'get-text-dimensions {util.to_b64(text)}\n'

    def setColor(self, color):
        self.draw_buf += f'set-color {color}\n'

    def drawRect(self, x, y, w, h, fill):
        self.draw_buf += f'draw-rect {x} {y} {w} {h} {"true" if fill else "false"}\n'

    def drawCircle(self, x, y, r, fill):
        self.draw_buf += f'draw-circle {x} {y} {r} {"true" if fill else "false"}\n'

    def blit(self):
        self.draw_buf += f'blit\n'
        self.send(self.draw_buf)
        self.draw_buf = ''

    def send(self, message):
        #print(f'send()')
        #print(f'- message: {message}')
        try:
            client_sock.send(message.encode('utf-8'))
        except:
            util.debug(f'* exception: {sys.exc_info()[0]}')
            traceback.print_exc()

    def terminate(self):
        self.send(f'terminate\n')
        time.sleep(.1) # give send() some time to complete
        os.system('sudo shutdown 0')

def check_for_button_presses(x, y):
    print(f'check_for_button_presses() {x} {y}')
    for key in widget_table:
        w = widget_table[key]

        x1 = w[0]
        print(f'- x1: {x1}')
        x2 = x1 + w[2]
        print(f'- x2: {x2}')
        y1 = w[1]
        print(f'- y1: {y1}')
        y2 = y1 + w[3]
        print(f'- y2: {y2}')
        print(f'')

        if not handleButtonMethod is None and len(w) > 4 and x >= x1 and x < x2 and y >= y1 and y < y2:
            handleButtonMethod(key)

def handleMessage(cmd_list):
    #print(f'handleMessage()')
    #print(f'- cmd_list: {cmd_list}')

    for s in cmd_list:
        args = s.split(' ')
        #print(f'-- args: {args}')

        if args[0] == 'goodbye':
            util.debug(f'* received goodbye from remote screen')
            global client_sock
            if client_sock is not None:
                client_sock.close()
            continue

        if args[0] == 'touch':
            check_for_button_presses(int(args[1]), int(args[2]))
            continue

        if args[0] == 'text-dimensions':
            global text_table
            text_table[util.from_b64(args[1])] = (int(args[2]), int(args[3]))
            util.debug(f'- text_table: {text_table}')
            continue

        handleCommandMethod(args)
