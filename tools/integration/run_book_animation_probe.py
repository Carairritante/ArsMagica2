"""Visual regression in a fresh disposable modpack copy. Never copies user saves/accounts.

Supply a Prism directory, source Minecraft instance, Java 8 runtime and JDK 17
compiler. The output directory must not exist. All input JARs remain untouched.
"""
from pathlib import Path
import argparse,importlib.util,json,os,shutil,subprocess,zipfile

repo=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('fixture',repo/'tools/integration/run_server_effects_probe.py')
fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture)

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--output',type=Path,required=True);p.add_argument('--jar',type=Path,required=True)
    p.add_argument('--prism',type=Path,required=True)
    p.add_argument('--instance',type=Path,required=True)
    p.add_argument('--java',type=Path,required=True);p.add_argument('--javac',type=Path,required=True)
    p.add_argument('--baseline',action='store_true');p.add_argument('--timeout',type=int,default=650)
    a=p.parse_args();prism=a.prism.resolve()
    assert a.jar.is_file()
    game=a.output.resolve();assert not game.exists()
    assert game != a.instance.resolve() and a.instance.resolve() not in game.parents
    game.mkdir(parents=True);mods=game/'mods';mods.mkdir()
    for name in ['config','coremods','falsepattern','scripts','resourcepacks','fonts','models','animations','particle','customnpcs']:
        if (a.instance/name).exists():fixture.copy_config(a.instance/name,game/name)
    manifest=[]
    for source in (a.instance/'mods').rglob('*.jar'):
        if source.is_symlink() or source.name.startswith('AM2.5-'):continue
        dest=mods/source.relative_to(a.instance/'mods');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
        manifest.append({'name':str(dest.relative_to(mods)),'sha256':fixture.sha(dest)})
    if (a.instance/'mods/Reika').exists():fixture.copy_config(a.instance/'mods/Reika',mods/'Reika')
    shutil.copy2(a.jar,mods/a.jar.name)
    cp=fixture.libraries(prism,True)
    classes=game/'probe-classes';classes.mkdir()
    subprocess.run([str(a.javac),'--release','8','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),str(repo/'tools/integration/BookAnimationProbe.java')],check=True)
    with zipfile.ZipFile(mods/'disposable-book-animation-probe.jar','w') as jar:
        for path in classes.rglob('*.class'):jar.write(path,path.relative_to(classes))
    natives=game/'natives';natives.mkdir()
    for path in (prism/'libraries').rglob('*natives-windows*.jar'):
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                if name.lower().endswith('.dll'):
                    dest=natives/Path(name).name
                    if not dest.exists():dest.write_bytes(jar.read(name))
    (game/'options.txt').write_text('lang:pt_BR\nrenderDistance:2\nfancyGraphics:false\nparticles:1\nguiScale:2\npauseOnLostFocus:false\nenableVsync:false\nmaxFps:60\nsoundCategory_master:0.0\nchatVisibility:2\n',encoding='ascii')
    command=[str(a.java),'-Xms512M','-Xmx6G','-Dlog4j2.formatMsgNoLookups=true']
    if a.baseline:command+=['-Dbookprobe.baseline=true']
    command+=['-Djava.library.path='+str(natives),'-cp',os.pathsep.join(map(str,cp)),
              'net.minecraft.launchwrapper.Launch','--username','BookProbe','--version','1.7.10','--gameDir',str(game),
              '--assetsDir',str(prism/'assets'),'--assetIndex','1.7.10','--uuid','00000000000000000000000000000004',
              '--accessToken','0','--userProperties','{}','--userType','legacy','--tweakClass','cpw.mods.fml.common.launcher.FMLTweaker',
              '--width','1024','--height','768']
    data={'jar':a.jar.name,'sha256':fixture.sha(a.jar),'baseline':a.baseline,'mods':manifest,'source_instance':str(a.instance),'passed':False}
    (game/'manifest.json').write_text(json.dumps(data,indent=2),encoding='utf-8')
    with (game/'console.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen(command,cwd=game,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,creationflags=0x08000000)
        print('BOOK TEST',game.name,'PID',process.pid,flush=True)
        try:code=process.wait(timeout=a.timeout)
        except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=30);code=124
    report=game/'book-animation-result.txt';text=report.read_text(encoding='utf-8') if report.exists() else ''
    marker='BOOK_BASELINE_REPRODUCED=PASS' if a.baseline else 'BOOK_ANIMATION_REGRESSION=PASS'
    data.update(exit_code=code,passed=code==0 and marker in text)
    (game/'manifest.json').write_text(json.dumps(data,indent=2),encoding='utf-8')
    print('EXIT',code,'PASS',data['passed']);print(text or 'No complete test report; inspect the temporary console.log.')
    raise SystemExit(0 if data['passed'] else 1)

if __name__=='__main__':main()
