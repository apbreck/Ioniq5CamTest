#!/usr/bin/env python3
import pathlib,os,subprocess,zipfile,shutil,hashlib,json
root=pathlib.Path(__file__).resolve().parent
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',str(pathlib.Path.home()/'Library/Android/sdk')))
bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
build=pathlib.Path(os.environ.get('BROWSER_BUILD_DIR',str(root/'build')));keys=pathlib.Path(os.environ.get('BROWSER_SIGNING_DIR',str(root/'signing')));dist=root/'dist'
for p in [build,keys,dist]:p.mkdir(parents=True,exist_ok=True)
def run(a):subprocess.run([str(x) for x in a],check=True)
classes=build/'classes';tests=build/'tests';dex=build/'dex'
for p in [classes,tests,dex]:p.mkdir(exist_ok=True)
run(['javac','--release','8','-classpath',android,'-d',classes,*sorted((root/'src').rglob('*.java'))])
run(['javac','-d',tests,root/'src/dev/nemesiscatalog/Policy.java',root/'src/dev/nemesiscatalog/PackageCheck.java',root/'tests/ChecksTest.java'])
run(['java','-cp',tests,'ChecksTest'])
run([bt/'d8','--min-api','19','--lib',android,'--output',dex,*sorted(classes.rglob('*.class'))])
unsigned=build/'unsigned.apk';run([bt/'aapt','package','-f','-M',root/'AndroidManifest.xml','-I',android,'-F',unsigned])
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
aligned=build/'aligned.apk';run([bt/'zipalign','-f','4',unsigned,aligned])
key=keys/'module-browser.p12'
if not key.exists():run(['keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass','local-browser','-keypass','local-browser','-alias','browser','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Local Nemesis Module Browser'])
apk=dist/'NemesisModuleBrowser.apk'
run([bt/'apksigner','sign','--ks',key,'--ks-key-alias','browser','--ks-pass','pass:local-browser','--min-sdk-version','19','--v1-signing-enabled','true','--out',apk,aligned])
run([bt/'apksigner','verify','--min-sdk-version','19','--max-sdk-version','19',apk])
stage=build/'stage'
if stage.exists():shutil.rmtree(stage)
shutil.copytree(root/'package',stage);(stage/'app').mkdir();shutil.copy2(apk,stage/'app/NemesisModuleBrowser.apk')
with zipfile.ZipFile(stage/'install-gate.jar','w',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
for n in ['LICENSE','THIRD_PARTY_NOTICES.md']:shutil.copy2(root.parent/n,stage/n)
def pack(folder,out):
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
  for p in sorted(folder.rglob('*')):
   if p.is_file():z.write(p,str(p.relative_to(folder)))
module=dist/'module-browser-0.1.0.zip';pack(stage,module)
with zipfile.ZipFile(dist/'module-browser-0.1.0-usb.zip','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('NEMESIS.data','[nemesis]\nversion=1\ntype=install\npath=packages/module-browser.zip\n');z.write(module,'packages/module-browser.zip');z.write(root/'README.md','MODULE-BROWSER-README.md');z.write(root.parent/'LICENSE','LICENSE')
hello=dist/'catalog-hello-1.0.0.zip';pack(root/'hello',hello)
with zipfile.ZipFile(hello,'a',zipfile.ZIP_DEFLATED) as z:z.write(root.parent/'LICENSE','LICENSE')
camera=root.parent/'dist/camera-probe-0.1.2.zip'
def entry(path,**fields):return dict(fields,bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
entries=[entry(hello,id='catalog.hello',name='Catalog Hello Test',version='1.0.0',publisher='apbreck',description='Tests downloading and installing a module. Writes one installed.txt marker inside its own package directory. No vehicle settings or services are changed.',compatibility='Experimental package-manager test. Requires NEMESIS. Physical-car installation has not been verified.',firmwarePrefixes=[],downloadUrl='https://github.com/apbreck/Ioniq5CamTest/releases/download/browser-v0.1.0/catalog-hello-1.0.0.zip')]
if camera.exists():entries.append(entry(camera,id='camera.probe',name='Camera Inventory (preview disabled)',version='0.1.2',publisher='apbreck',description='Diagnostic-only camera inventory and USB reports. All vehicle preview attempts are disabled. This does not enable camera recording or 360 video.',compatibility='Experimental. Declared firmware target only; not a confirmation of head-unit compatibility. Earlier preview releases have a known driver issue.',firmwarePrefixes=['NEEV23.USA.S5W_M.V015.012.260507'],downloadUrl='https://github.com/apbreck/Ioniq5CamTest/releases/download/v0.1.2/camera-probe-0.1.2.zip'))
(root.parent/'module-catalog.json').write_text(json.dumps(dict(schemaVersion=1,repository='apbreck/Ioniq5CamTest',modules=entries),indent=2)+'\n')
with open(dist/'SHA256SUMS.txt','w') as f:
 for p in sorted(dist.glob('*.zip')):f.write(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
print('Built browser and test catalog:',dist)
