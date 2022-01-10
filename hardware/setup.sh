#! /bin/bash

get_config_attr() {
    FILE=$1
    KEY=$2": "

    echo `grep "^$KEY" $FILE | awk '{print $2}'`
}

is_attr_set() {
    ATTR=$1

    if [ -z $ATTR ]
    then
      echo "0"
      return
    fi

    if [ "$ATTR" == "SET_ME" ]
    then
      echo "0"
      return
    fi

    echo "1"
}

check_required_arg() {
    NAME=$1
    VALUE=$2

    if [ -z "$VALUE" ]
    then
      echo "required arg $NAME unset, bailing..."
      exit 1
    fi

    if [ "$VALUE" == "SET_ME" ]
    then
      echo "required arg $NAME unset, bailing..."
      exit 1
    fi
}

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

esc_slashes() {
    echo $1 | sed 's|/|\\/|g'
}

# check args
if [ $# -ne 1 ]
then
    echo "usage: $0 <path-to-setup.cfg>"
    exit 1
fi

CONFIG_FILE=$1

AGENCY_ID=$( get_config_attr $CONFIG_FILE agency_id )
check_required_arg AGENCY_ID $AGENCY_ID
echo "AGENCY_ID: $AGENCY_ID"

VEHICLE_ID=$( get_config_attr $CONFIG_FILE vehicle_id )
check_required_arg VEHICLE_ID $VEHICLE_ID
echo "VEHICLE_ID: $VEHICLE_ID"

SIGNING_KEY=$( get_config_attr $CONFIG_FILE signing_key )
check_required_arg SIGNING_KEY $SIGNING_KEY
echo "SIGNING_KEY: $SIGNING_KEY"

STATIC_GTFS_URL=$( get_config_attr $CONFIG_FILE static_gtfs_url )
check_required_arg STATIC_GTFS_URL $STATIC_GTFS_URL
echo "STATIC_GTFS_URL: $STATIC_GTFS_URL"

NGROK_AUTH=$( get_config_attr $CONFIG_FILE ngrok_auth )
echo "NGROK_AUTH: $NGROK_AUTH"

NGROK_TUNNEL=$( get_config_attr $CONFIG_FILE ngrok_tunnel )
echo "NGROK_TUNNEL: $NGROK_TUNNEL"

GRASS_BRANCH=$( get_config_attr $CONFIG_FILE grass_branch )
check_required_arg GRASS_BRANCH $GRASS_BRANCH
echo "GRASS_BRANCH: $GRASS_BRANCH"

GRASS_REPO=$( get_config_attr $CONFIG_FILE grass_repo )
check_required_arg GRASS_REPO $GRASS_REPO
echo "GRASS_REPO: $GRASS_REPO"

HOSTNAME=$( get_config_attr $CONFIG_FILE hostname )
check_required_arg HOSTNAME $HOSTNAME
echo "HOSTNAME: $HOSTNAME"

PASSWD=$( get_config_attr $CONFIG_FILE passwd )
check_required_arg PASSWD $PASSWD
echo "PASSWD: $PASSWD"

GRASS_ROOT=`pwd -P`
echo "GRASS_ROOT: \"$GRASS_ROOT\""

# env
ENV_FILE=/etc/environment
sudo touch $ENV_FILE
RESULT=`grep GRASS $ENV_FILE`
if [ -z "$RESULT" ]
then
    echo "setting /etc/environment vars"
    TMP_FILE=/tmp/environment
    cat $ENV_FILE > $TMP_FILE
    echo "GRASS_ROOT=$GRASS_ROOT" >> $TMP_FILE
    echo "GRASS_BRANCH=$GRASS_BRANCH" >> $TMP_FILE
    echo "GRASS_REPO=$GRASS_REPO" >> $TMP_FILE

    if [ $(is_attr_set $NGROK_TUNNEL ) == "1" ]
    then
        echo "GRASS_TUNNEL=$NGROK_TUNNEL" >> $TMP_FILE
    fi
    sudo mv $TMP_FILE $ENV_FILE
fi

if [ ! -e /dev/ttyUSB2 ]
then
  echo "could not find /dev/ttyUSB2. Cable unplugged?"
  exit 1
fi

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
mkdir -p /home/pi/doc
mkdir -p /home/pi/bin

# conf
CONF_FILE=/home/pi/doc/graas.cfg
if [ ! -f $CONF_FILE ]
then
    echo "setting up graas.cfg"
    touch $CONF_FILE
    echo "agency_name: $AGENCY_ID" >> $CONF_FILE
    echo "vehicle_id: $VEHICLE_ID" >> $CONF_FILE
    echo "static_gtfs_url: $STATIC_GTFS_URL" >> $CONF_FILE
    echo "agency_key: $SIGNING_KEY" >> $CONF_FILE
fi

sudo sh -c "echo pi:$PASSWD | chpasswd"

if [ ! -f /home/pi/.ssh/config ]
then
	touch /home/pi/.ssh/config
	echo "Host github.com" >> /home/pi/.ssh/config
	echo "    StrictHostKeyChecking no" >> /home/pi/.ssh/config
fi

# ngrok
if [ ! -f /home/pi/bin/ngrok ]
then
    pushd /home/pi/bin
    wget https://bin.equinox.io/c/4VmDzA7iaHb/ngrok-stable-linux-arm.zip
    unzip ngrok-stable-linux-arm.zip
    rm ngrok-stable-linux-arm.zip
    popd
fi

if [ $(is_attr_set $NGROK_AUTH ) == "1" ]
then
    home/pi/bin/ngrok authtoken $NGROK_AUTH
    sudo mkdir -p /root/.ngrok2
    sudo cp /home/pi/.ngrok2/ngrok.yml /root/.ngrok2
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
RESULT=`grep graas $RC_FILE`
if [ -z "$RESULT" ]
then
    ESC_ROOT=$( esc_slashes $GRASS_ROOT )
    echo "ESC_ROOT: \"$ESC_ROOT\""
    edit_file $RC_FILE "^exit 0$" "$ESC_ROOT\/start-util.sh \&\n$ESC_ROOT\/start-debuggee.sh \&\n$ESC_ROOT\/start-graas.sh \&\nexit 0"
    sudo chmod u+x $RC_FILE
    sudo chown root $RC_FILE
    sudo chgrp root $RC_FILE
fi

# misc
echo "0 3 * * * $GRASS_ROOT/shutdown-graas.sh" | crontab -
echo "copied a date/time string to the clipboard. please open clock settings and paste"
echo -n "%a, %b %d %l:%M %P" | xclip -sel clip
echo "hit enter when done (system will reboot)"
read LINE

# wrapping up
echo "rebooting..."
sleep 1
sudo reboot
