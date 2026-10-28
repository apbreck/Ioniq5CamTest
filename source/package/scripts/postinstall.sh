#!/system/bin/sh
mkdir -p /data/nemesis/camera-probe
chown 1000:1000 /data/nemesis/camera-probe
chmod 777 /data/nemesis/camera-probe
# Old requests never survive install/update as an active session.
rm -f /data/nemesis/camera-probe/request.json /data/nemesis/camera-probe/preview.jpg

rm -f /data/nemesis/camera-probe/export-request.json /data/nemesis/camera-probe/export-result.json
