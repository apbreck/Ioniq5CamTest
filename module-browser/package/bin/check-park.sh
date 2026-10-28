#!/system/bin/sh
# Fixed read-only gate; no downloaded commands or arguments are interpolated.
if [ -z "$BOOTCLASSPATH" ] && [ -r /init.environ.rc ]; then
    while read _cmd _name _value; do
        [ "$_cmd" = export ] && export "$_name=$_value"
    done < /init.environ.rc
fi
export CLASSPATH=/data/nemesis/apps/module.browser/install-gate.jar
exec app_process /system/bin dev.nemesiscatalog.InstallGate
