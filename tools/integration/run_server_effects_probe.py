"""Run a real Forge regression in a NEW disposable directory, using local libraries.

The client mode copies a modpack's mods/configuration, never worlds or accounts.
The dedicated mode uses only AM2, AnimationAPI and CoFH from that same modpack.
"""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,zipfile

def coordinate(prism, name):
    group,artifact,version,*classifier=name.split(':')
    suffix='-'+classifier[0] if classifier else ''
    return prism/'libraries'/group.replace('.','/')/artifact/version/(artifact+'-'+version+suffix+'.jar')

def libraries(prism, client):
    found={}
    docs=[prism/'meta/net.minecraft/1.7.10.json',prism/'meta/org.lwjgl/2.9.4-nightly-20150209.json',prism/'meta/net.minecraftforge/10.13.4.1614.json']
    for path in docs:
        data=json.loads(path.read_text(encoding='utf-8'))
        for lib in data.get('libraries',[]):
            if lib.get('natives'):continue
            allowed=not lib.get('rules')
            for rule in lib.get('rules',[]):
                if not rule.get('os') or rule['os'].get('name')=='windows':allowed=rule['action']=='allow'
            if not allowed:continue
            jar=coordinate(prism,lib['name'])
            found[':'.join(lib['name'].split(':')[:2])]=jar
        if client and 'mainJar' in data:found['minecraft']=coordinate(prism,data['mainJar']['name'])
    for jar in found.values():
        if not jar.exists():raise FileNotFoundError(jar)
    return list(found.values())

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def copy_config(source,target):
    secret=re.compile(r'(?i)(?:^|[\s.:"\-])(?:password|passwd|access[_-]?token|auth[_-]?token|api[_-]?key|secret|authorization)\s*[=:]')
    omitted=[]
    for path in source.rglob('*'):
        if path.is_symlink() or not path.is_file():continue
        try:lines=path.read_text(encoding='utf-8').splitlines()
        except (UnicodeError,OSError):lines=[]
        if any(secret.search(line) for line in lines if not line.lstrip().startswith(('#','//'))):
            omitted.append(str(path.relative_to(source)));continue
        dest=target/path.relative_to(source);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
    return omitted

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prism','instance','output','jar','java','javac','server-jar']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--client',action='store_true');p.add_argument('--legacy',action='store_true')
    p.add_argument('--timeout',type=int,default=600)
    a=p.parse_args()
    game=a.output.resolve();assert not game.exists(),'Use a fresh disposable directory.'
    assert game!=a.instance.resolve() and a.instance.resolve() not in game.parents
    assert a.jar.is_file() and a.server_jar.is_file()
    game.mkdir(parents=True);mods=game/'mods';mods.mkdir()
    cp=libraries(a.prism,a.client)
    if a.client:
        for name in ['config','coremods','falsepattern','scripts','resourcepacks','fonts','models','animations','particle','customnpcs']:
            source=a.instance/name
            if source.exists():copy_config(source,game/name)
        chosen=[x for x in (a.instance/'mods').rglob('*.jar') if not x.name.startswith('AM2.5-') and not x.is_symlink()]
    else:
        chosen=[a.instance/'mods'/name for name in ['AnimationAPI-1.7.10-1.2.4.jar','CoFHCore-[1.7.10]3.1.4-329.jar','CoFHLib-[1.7.10]1.2.1-185.jar']]
        vanilla=game/'minecraft_server.1.7.10.jar';shutil.copy2(a.server_jar,vanilla);cp.append(vanilla)
        (game/'eula.txt').write_text('eula=true\n',encoding='ascii')
        (game/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=disposable\nlevel-type=FLAT\ngenerator-settings=2;7,2x3,2;1;\ngenerate-structures=false\nspawn-monsters=false\nspawn-animals=false\nallow-nether=false\nview-distance=3\nmax-players=1\nenable-query=false\nenable-rcon=false\n',encoding='ascii')
    manifest=[]
    for source in chosen:
        dest=mods/source.relative_to(a.instance/'mods');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
        manifest.append({'name':str(dest.relative_to(mods)),'sha256':sha(dest)})
    if a.client and (a.instance/'mods/Reika').exists():
        copy_config(a.instance/'mods/Reika',mods/'Reika')
    shutil.copy2(a.jar,mods/a.jar.name);manifest.append({'name':a.jar.name,'sha256':sha(a.jar)})
    classes=game/'probe-classes';classes.mkdir()
    source=Path(__file__).with_name('ServerEffectsProbe.java')
    compile_cp=os.pathsep.join(map(str,cp+[a.server_jar]))
    subprocess.run([str(a.javac),'--release','8','-encoding','UTF-8','-cp',compile_cp,'-d',str(classes),str(source)],check=True)
    with zipfile.ZipFile(mods/'disposable-server-effects-probe.jar','w') as jar:
        for path in classes.rglob('*.class'):jar.write(path,path.relative_to(classes))
    command=[str(a.java),'-Xms512M','-Xmx'+('8G' if a.client else '2G'),'-Dlog4j2.formatMsgNoLookups=true']
    if a.legacy:command.append('-Dam2.probe.legacy=true')
    if a.client:
        natives=game/'natives';natives.mkdir()
        for path in (a.prism/'libraries').rglob('*natives-windows*.jar'):
            with zipfile.ZipFile(path) as jar:
                for name in jar.namelist():
                    if name.lower().endswith('.dll'):
                        dest=natives/Path(name).name
                        if not dest.exists():dest.write_bytes(jar.read(name))
        (game/'options.txt').write_text('lang:pt_BR\nrenderDistance:2\nfancyGraphics:false\nparticles:2\nguiScale:2\npauseOnLostFocus:false\nenableVsync:false\nmaxFps:60\nsoundCategory_master:0.0\n',encoding='ascii')
        command+=['-Djava.library.path='+str(natives),'-cp',os.pathsep.join(map(str,cp)),
                  'net.minecraft.launchwrapper.Launch','--username','EffectsProbe','--version','1.7.10',
                  '--gameDir',str(game),'--assetsDir',str(a.prism/'assets'),'--assetIndex','1.7.10',
                  '--uuid','00000000000000000000000000000003','--accessToken','0','--userProperties','{}',
                  '--userType','legacy','--tweakClass','cpw.mods.fml.common.launcher.FMLTweaker','--width','854','--height','480']
    else:command+=['-cp',os.pathsep.join(map(str,cp)),'cpw.mods.fml.relauncher.ServerLaunchWrapper','nogui']
    meta={'am2':a.jar.name,'sha256':sha(a.jar),'physical_side':'CLIENT' if a.client else 'SERVER','legacy':a.legacy,
          'source_mods':manifest,'worlds_and_accounts_copied':False,'passed':False}
    (game/'manifest.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    with (game/'console.log').open('w',encoding='utf-8') as log:
        proc=subprocess.Popen(command,cwd=game,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,creationflags=0x08000000)
        print('STARTED',game.name,'PID',proc.pid,flush=True)
        try:code=proc.wait(timeout=a.timeout)
        except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=30);code=124
    report=game/'server-effects-result.txt'
    text=report.read_text(encoding='utf-8') if report.exists() else ''
    marker='SERVER_EFFECTS_BASELINE=PASS' if a.legacy else 'SERVER_EFFECTS_REGRESSION=PASS'
    meta.update(exit_code=code,passed=code==0 and marker in text)
    (game/'manifest.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    print('EXIT',code,'PASSED',meta['passed'])
    if text:print(text)
    else:print('No regression report; inspect the isolated console.log.')
    raise SystemExit(0 if meta['passed'] else 1)

if __name__=='__main__':main()
