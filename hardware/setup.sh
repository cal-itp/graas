#! /bin/sh

edit_file() {
    FILE=$1
    FROM=$2
    TO=$3

    echo -n "editing $FILE..."
    cat $FILE | sed "s/$FROM/$TO/" > /tmp/graas.save || exit 1
    sudo mv /tmp/graas.save $FILE || exit 1
    echo "done"
}

send_at() {
  echo "send $1" > cmd.script
  echo "expect OK" >> cmd.script
  echo '! killall minicom' >> cmd.script
  minicom -D /dev/ttyUSB2 -S cmd.script
}

# check args
if [ $# -ne 3 ]
then
    echo "usage: $0 <hostname> <password> <ngrok-auth>"
    exit 1
fi

if [ ! -e /dev/ttyUSB2 ]
then
  echo "could not find /dev/ttyUSB2. Cable unplugged?"
  exit 1
fi

HOSTNAME=$1
PASSWD=$2
NGROK_AUTH=$3

# prep
sudo rfkill unblock 0
sudo ifconfig wlan0 up
sudo apt -y update
sudo apt -y install vim

VIMRC=/home/pi/.vimrc
touch $VIMRC
echo "syntax enable" >> $VIMRC
echo "set tabstop=4" >> $VIMRC
echo "set expandtab" >> $VIMRC

sudo apt-get -y install xclip
edit_file /etc/lightdm/lightdm.conf "^autologin-user=pi" "#autologin-user=pi"
sudo rm /usr/bin/python
sudo ln -s /usr/bin/python3 /usr/bin/python

mkdir -p /home/pi/logs
mkdir -p /home/pi/venv
mkdir -p /home/pi/.ssh
mkdir -p /home/pi/projects
mkdir -p /home/pi/doc
mkdir -p /home/pi/bin

sudo sh -c "echo pi:$PASSWD | chpasswd"

if [ ! -f /home/pi/.ssh/config ]
then
	touch /home/pi/.ssh/config
	echo "Host github.com" >> /home/pi/.ssh/config
	echo "    StrictHostKeyChecking no" >> /home/pi/.ssh/config
fi

# ngrok
cd /home/pi/bin
if [ ! -f /home/pi/bin/ngrok ]
then
    wget https://bin.equinox.io/c/4VmDzA7iaHb/ngrok-stable-linux-arm.zip
    unzip ngrok-stable-linux-arm.zip
    rm ngrok-stable-linux-arm.zip
    ./ngrok authtoken $NGROK_AUTH
    sudo mkdir -p /root/.ngrok2
    sudo cp /home/pi/.ngrok2/ngrok.yml /root/.ngrok2
fi

#- github setup:
git config --global user.name "Kay Neuenhofen"
git config --global user.email "kay.neuenhofen@gmail.com"

# graas repo:
cd /home/pi/projects
if [ ! -d /home/pi/projects/grass-box ]
then
    git clone git@github.com:unreasonableman/grass-box.git
    cd /home/pi/projects/grass-box
else
    cd /home/pi/projects/grass-box
    git pull
fi

if [ ! -d /home/pi/venv/graas ]
then
    echo -n "creating graas venv..."
    python -m venv /home/pi/venv/graas
    . /home/pi/venv/graas/bin/activate
    pip install -r requirements.txt
    echo "done"
fi

# raspi-config
sudo raspi-config nonint do_hostname $HOSTNAME
sudo raspi-config nonint do_serial 2
sudo raspi-config nonint do_i2c 0
sudo raspi-config nonint do_ssh 0

# 7600
sudo apt -y install minicom
send_at "AT+CUSBPIDSWITCH=9011,1,1"
printf "waiting for usb network interface"
sleep 3

sudo dhclient -v usb0
sleep 5
echo "waiting for IP address..."
ping -c 5 -I usb0 8.8.8.8

# bluetooth:
sudo apt-get -y install bluetooth libbluetooth-dev

edit_file /etc/bluetooth/main.conf "^#DiscoverableTimeout = 0" "DiscoverableTimeout = 0"
edit_file /lib/systemd/system/bluetooth.service "^ExecStart=\/usr\/lib\/bluetooth\/bluetoothd$" "ExecStart=\/usr\/lib\/bluetooth\/bluetoothd -C"
sudo usermod -G bluetooth -a pi

# shell
RESULT=`grep psg\(\) /home/pi/.bashrc`
if [ -z "$RESULT" ]
then
    echo -n "pimping .bashrc..."
	BAK_FILE=/tmp/.bashrc
	cp /home/pi/.bashrc $BAK_FILE
	echo 'alias ll="ls -al"' >> $BAK_FILE
	echo 'alias h="history"' >> $BAK_FILE
	echo 'alias hg="history|grep"' >> $BAK_FILE
	echo 'alias sb="source ~/.bashrc"' >> $BAK_FILE
    echo 'alias tf="tail -f `ls /home/pi/logs/\$HOSTNAME-* | sort | tail -1`"' >> $BAK_FILE
	echo "function psg() {" >> $BAK_FILE
	echo "    ps -ef | grep \$1 | grep -v ' grep '" >> $BAK_FILE
	echo "}" >> $BAK_FILE
    echo "function mac() {" >> $BAK_FILE
    echo "    MAC=\`ifconfig eth0 | head -2 | tail -1 | awk '{print \$2}'\`" >> $BAK_FILE
    echo "    echo MAC: \$MAC" >> $BAK_FILE
    echo "    echo -n \$MAC | xclip -sel clip" >> $BAK_FILE
    echo "}" >> $BAK_FILE
    echo 'alias die="sudo shutdown 0"' >> $BAK_FILE
    echo 'alias bts="bluetoothctl show"' >> $BAK_FILE
    echo 'alias llu="ll /dev/ttyUSB*"' >> $BAK_FILE
    echo 'alias mini="minicom -D /dev/ttyUSB2"' >> $BAK_FILE
	echo "export HISTSIZE=10000" >> $BAK_FILE
	echo "export HISTFILESIZE=10000" >> $BAK_FILE
	mv $BAK_FILE /home/pi/.bashrc
	echo "done"
fi

# grass
RC_FILE=/etc/rc.local
RESULT=`grep /home/pi/projects $RC_FILE`
if [ -z "$RESULT" ]
then
    edit_file $RC_FILE "^exit 0$" "\/home\/pi\/projects\/grass-box\/start-util.sh \&\n\/home\/pi\/projects\/grass-box\/start-debuggee.sh \&\n\/home\/pi\/projects\/grass-box\/start-graas.sh \&\nexit 0"
    sudo chmod u+x $RC_FILE
    sudo chown root $RC_FILE
    sudo chgrp root $RC_FILE
fi

# misc
echo "0 3 * * * /usr/bin/sudo /usr/sbin/reboot" | crontab -
echo "copied a date/time string to the clipboard. please open clock settings and paste"
echo -n "%a, %b %d %l:%M %P" | xclip -sel clip
echo "hit enter when done (system will reboot)"
read LINE

# wrapping up
echo "rebooting..."
sleep 1
sudo reboot
