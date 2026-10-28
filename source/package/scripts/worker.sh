#!/system/bin/sh
# Idle unless the foreground UI requests an individual preview.
cd /data/nemesis/apps/camera.probe || exit 1
if [ -z "$BOOTCLASSPATH" ] && [ -r /init.environ.rc ]; then
    while read _cmd _name _value; do
        [ "$_cmd" = export ] && export "$_name=$_value"
    done < /init.environ.rc
fi
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 5; done
mkdir -p /data/nemesis/camera-probe
chown 1000:1000 /data/nemesis/camera-probe
chmod 777 /data/nemesis/camera-probe
sleep 2
export CLASSPATH=/data/nemesis/apps/camera.probe/probe-worker.jar
exec app_process /system/bin dev.cameraprobe.Worker >> /data/nemesis/camera-probe-worker.log 2>&1
