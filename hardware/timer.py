import util

class Timer:
    def __init__(self, name):
        self.name = name
        self.start = util.now()

    def __str__(self):
        return f'timer \'{self.name}\': {util.now() - self.start} ms'
