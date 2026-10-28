#!/usr/bin/env python3
"""Offline Android SDK build. No Gradle downloads or vendor firmware bundled."""
import os, pathlib, subprocess, shutil, zipfile, hashlib, sys
root=pathlib.Path(__file__).resolve().parent
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',str(pathlib.Path.home()/'Library/Android/sdk')))
bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
build=pathlib.Path(os.environ.get('CAMERAPROBE_BUILD_DIR',str(root/'build')));dist=root.parent/'dist'
build.mkdir(exist_ok=True);dist.mkdir(exist_ok=True)
def run(args):subprocess.run([str(a) for a in args],check=True,cwd=root)
classes=build/'classes';classes.mkdir(exist_ok=True)
run(['javac','-source','8','-target','8','-Xlint:-options','-bootclasspath',android,'-d',classes,*sorted((root/'src').rglob('*.java'))])
tests=build/'tests';tests.mkdir(exist_ok=True)
run(['javac','-d',tests,root/'src/dev/cameraprobe/FrameStats.java',root/'tests/FrameStatsTest.java'])
run(['java','-cp',tests,'FrameStatsTest'])
dex=build/'dex';dex.mkdir(exist_ok=True)
run([bt/'d8','--min-api','19','--lib',android,'--output',dex,*sorted(classes.rglob('*.class'))])
unsigned=build/'unsigned.apk'
run([bt/'aapt','package','-f','-M',root/'AndroidManifest.xml','-I',android,'-F',unsigned])
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
aligned=build/'aligned.apk';run([bt/'zipalign','-f','4',unsigned,aligned])
# Local signing key is retained so future updates can use the same signer. Not bundled in releases.
keys=pathlib.Path(os.environ.get('CAMERAPROBE_SIGNING_DIR',str(root/'signing')));keys.mkdir(exist_ok=True);key=keys/'camera-probe.p12'
if not key.exists():run(['keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass','local-camera-probe','-keypass','local-camera-probe','-alias','probe','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Local Camera Feed Probe'])
apk=dist/'CameraFeedProbe.apk'
run([bt/'apksigner','sign','--ks',key,'--ks-key-alias','probe','--ks-pass','pass:local-camera-probe','--min-sdk-version','19','--v1-signing-enabled','true','--out',apk,aligned])
run([bt/'apksigner','verify','--min-sdk-version','19','--verbose',apk])
stage=build/'stage'
if stage.exists():shutil.rmtree(stage)
shutil.copytree(root/'package',stage);(stage/'app').mkdir();shutil.copy2(apk,stage/'app/CameraFeedProbe.apk')
for notice in ['LICENSE','THIRD_PARTY_NOTICES.md']:
    shutil.copy2(root.parent/notice,stage/notice)
with zipfile.ZipFile(stage/'probe-worker.jar','w',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
module=dist/'camera-probe-0.1.0.zip'
with zipfile.ZipFile(module,'w',zipfile.ZIP_DEFLATED) as z:
    for f in sorted(stage.rglob('*')):
        if f.is_file():z.write(f,str(f.relative_to(stage)))
with zipfile.ZipFile(dist/'camera-probe-0.1.0-usb.zip','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('NEMESIS.data','[nemesis]\nversion=1\ntype=install\npath=packages/camera-probe.zip\n');z.write(module,'packages/camera-probe.zip')
    z.write(root.parent/'README.md','CAMERA-PROBE-README.md')
    z.write(root.parent/'LICENSE','LICENSE')
    z.write(root.parent/'THIRD_PARTY_NOTICES.md','THIRD_PARTY_NOTICES.md')
shutil.copy2(stage/'probe-worker.jar',dist/'probe-worker.jar')
with open(dist/'SHA256SUMS.txt','w') as out:
    for f in sorted(dist.iterdir()):
        if f.suffix in ['.zip','.apk','.jar']:out.write(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n')
print('Built',module)
