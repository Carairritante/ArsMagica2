"""Run the Astral Compass regression in a NEW disposable dedicated server.

Supply a server-compatible Forge 1.7.10 pack containing OreSpawn Remastered,
Electroblob's Wizardry and ChromatiCraft. Exclude client-only mods explicitly.
No user worlds, player data or launcher accounts are copied.
"""
from pathlib import Path
import argparse, fnmatch, json, os, shutil, subprocess, zipfile
from run_server_effects_probe import libraries, copy_config, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['prism', 'instance', 'output', 'jar', 'java', 'javac', 'server-jar']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--exclude', action='append', default=[], help='Repeatable JAR basename glob for client-only/server-incompatible mods.')
    parser.add_argument('--timeout', type=int, default=900)
    args = parser.parse_args()
    game = args.output.resolve()
    instance = args.instance.resolve()
    if game.exists() or game == instance or instance in game.parents:
        parser.error('Choose a new directory outside the source instance.')
    if not (instance / 'mods').is_dir() or not args.jar.is_file() or not args.server_jar.is_file():
        parser.error('The instance, mod JAR and vanilla server JAR must exist.')
    if args.timeout < 30:
        parser.error('Timeout must be at least 30 seconds.')
    cp = libraries(args.prism, False)
    game.mkdir(parents=True)
    mods = game / 'mods'
    mods.mkdir()
    manifest = {'am2': args.jar.name, 'sha256': sha(args.jar), 'mods': [],
                'excluded': [], 'worlds_and_accounts_copied': False, 'passed': False}
    exclusions = ['AM2.5*.jar', '*astral-compass-probe*.jar'] + args.exclude
    for source in sorted((instance / 'mods').rglob('*.jar')):
        if source.is_symlink():
            continue
        if any(fnmatch.fnmatch(source.name, pattern) for pattern in exclusions):
            manifest['excluded'].append(source.name)
            continue
        rel = source.relative_to(instance / 'mods')
        target = mods / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        manifest['mods'].append({'name': str(rel), 'sha256': sha(source)})
    for name in ['config', 'scripts', 'coremods', 'falsepattern']:
        if (instance / name).exists():
            copy_config(instance / name, game / name)
    if (instance / 'mods/Reika').exists():
        copy_config(instance / 'mods/Reika', mods / 'Reika')
    shutil.copy2(args.jar, mods / args.jar.name)
    vanilla = game / 'minecraft_server.1.7.10.jar'
    shutil.copy2(args.server_jar, vanilla)
    cp.append(vanilla)
    classes = game / 'probe-classes'
    classes.mkdir()
    source = Path(__file__).with_name('AstralCompassProbe.java')
    subprocess.run([str(args.javac), '--release', '8', '-encoding', 'UTF-8',
                    '-cp', os.pathsep.join(map(str, cp)), '-d', str(classes), str(source)], check=True)
    with zipfile.ZipFile(mods / 'astral-compass-probe.jar', 'w') as jar:
        for path in sorted(classes.rglob('*.class')):
            jar.write(path, path.relative_to(classes))
    (game / 'eula.txt').write_text('eula=true\n', encoding='ascii')
    (game / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=disposable\n'
        'level-type=FLAT\ngenerator-settings=2;7,2x3,2;1;village\ngenerate-structures=true\n'
        'spawn-monsters=false\nspawn-animals=false\nallow-nether=false\nview-distance=3\n'
        'max-players=1\nenable-query=false\nenable-rcon=false\n', encoding='ascii')
    command = [str(args.java), '-Xms512M', '-Xmx6G', '-Dlog4j2.formatMsgNoLookups=true',
               '-cp', os.pathsep.join(map(str, cp)), 'cpw.mods.fml.relauncher.ServerLaunchWrapper', 'nogui']
    (game / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    with (game / 'console.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT,
                                   stdin=subprocess.PIPE, creationflags=0x08000000 if os.name == 'nt' else 0)
        try:
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            try:
                process.stdin.write(b'stop\n')
                process.stdin.flush()
                code = process.wait(timeout=45)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                process.terminate()
                code = process.wait(timeout=30)
    result = game / 'astral-results.txt'
    text = result.read_text(encoding='utf-8') if result.exists() else ''
    manifest.update(exit_code=code, passed=code == 0 and 'ASTRAL_COMPASS=PASS' in text)
    (game / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(text or 'No test report; inspect the disposable console.log.')
    raise SystemExit(0 if manifest['passed'] else 1)


if __name__ == '__main__':
    main()
