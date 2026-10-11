"""Real first install and restart of a historical Music fixture on modern Paper."""
import hashlib, json, pathlib, queue, shutil, subprocess, threading, time
from run_matrix import ROOT, freeport

def run(toolcache=None):
    source=ROOT/'test-servers/26.3';folder=ROOT/'test-servers/migration-26.3';folder.mkdir(exist_ok=True)
    for name in ('server.jar','download.json'):shutil.copy2(source/name,folder/name)
    for name in ('cache','versions','libraries'):
        if (source/name).exists():shutil.copytree(source/name,folder/name,dirs_exist_ok=True)
    plugins=folder/'plugins';plugins.mkdir(exist_ok=True)
    own=plugins/'MusicReforged'
    if own.exists():shutil.rmtree(own)
    own.mkdir();(own/'config.yml').write_text('admin:\n  enabled: false\n')
    legacy=plugins/'Music';(legacy/'Music').mkdir(parents=True,exist_ok=True)
    owner='19cd46b4-5227-3575-9cbf-64f1bc95421f'
    config=f"Loop:\n  '88':\n    s: [LegacySong]\n    p: {owner}\n    r: 2\n    rand: true\nJukeboxes:\n  original:\n    location:\n      ==: org.bukkit.Location\n      world: world-currently-unloaded\n      x: -32.0\n      y: 81.0\n      z: 42.0\n      pitch: 0.0\n      yaw: 0.0\n    owner: {owner}\n    station: 88\n    volume: -1\n    active: true\n"
    (legacy/'config.yml').write_text(config);(legacy/'Music/LegacySong.txt').write_text('Time:0.125\nSongname:legacyfx\n[&&END&&]\n')
    original={str(p.relative_to(legacy)):hashlib.sha256(p.read_bytes()).hexdigest() for p in legacy.rglob('*') if p.is_file()}
    shutil.copy2(ROOT/'target/Music-Reforged-1.0.0.jar',plugins/'Music-Reforged-1.0.0.jar')
    (folder/'eula.txt').write_text('eula=true\n');(folder/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={freeport()}\nonline-mode=false\nlevel-name=test-world\nlevel-type=flat\nview-distance=2\nsimulation-distance=2\nallow-nether=false\n')
    (folder/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
    cache=pathlib.Path(toolcache or ROOT/'.tools').resolve();cmd=[str(cache/'jdk25/bin/java.exe'),'-Xms128M','-Xmx768M','-XX:ActiveProcessorCount=2','-Dmusicreforged.test=true','-jar','server.jar','nogui']
    result={'firstInstall':False,'restart':False,'originalUnchanged':False,'unloadedWorldPreserved':False};log=[]
    for phase in ('firstInstall','restart'):
        p=subprocess.Popen(cmd,cwd=folder,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace');lines=queue.Queue()
        def pump():
            for line in p.stdout:log.append(line);lines.put(line)
        thread=threading.Thread(target=pump,daemon=True);thread.start();start=time.monotonic()
        try:
            while time.monotonic()-start<120:
                try:line=lines.get(timeout=.5)
                except queue.Empty:
                    if p.poll() is not None:break
                    continue
                if 'Done (' in line:p.stdin.write('music migrationcheck\n');p.stdin.flush()
                if 'MIGRATION PASS' in line:result[phase]=True;break
                if 'Initialization failed' in line or 'CHECK FAIL' in line:break
        finally:
            if p.poll() is None:
                p.stdin.write('stop\n');p.stdin.flush()
                try:p.wait(timeout=40)
                except subprocess.TimeoutExpired:p.kill();p.wait()
            thread.join(timeout=3)
    current={str(p.relative_to(legacy)):hashlib.sha256(p.read_bytes()).hexdigest() for p in legacy.rglob('*') if p.is_file()}
    result['originalUnchanged']=original==current
    result['unloadedWorldPreserved']='world-currently-unloaded' in (own/'state.yml').read_text()
    (ROOT/'docs/migration-results.json').write_text(json.dumps(result,indent=2))
    (folder/'migration-test.log').write_text(''.join(log),encoding='utf-8')
    print(json.dumps(result),flush=True)
    if not all(result.values()):raise SystemExit(1)

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--tool-cache');args=parser.parse_args();run(args.tool_cache)
