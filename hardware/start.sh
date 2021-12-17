#! /bin/bash

echo "sleeping..."
sleep 10
echo "done"

cd /home/pi/projects/graas
source /home/pi/venvs/graas/bin/activate
/home/pi/tools/ngrok/ngrok tcp 22 &
python graas.py /home/pi/doc/test.pk

