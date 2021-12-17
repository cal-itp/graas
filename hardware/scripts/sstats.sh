#! /bin/sh

echo "system stats:"
echo "-------------"
echo "disk:"
df -H | egrep --color=never "Filesystem|/dev/root"
echo "load:"
uptime | sed 's/^ *//'
echo "mem:"
free -h

