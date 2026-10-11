"""Real isolated Paper boots, protocol clients, HTTP packs and restart checks."""
import argparse, concurrent.futures, hashlib, json, os, pathlib, queue, re, shutil, socket, subprocess, sys, threading, time, urllib.request, zipfile
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAR = ROOT / 'target/Music-Reforged-1.0.0.jar'

def runtime(v):
    if v == '1.17': return 16
    if v.startswith('26.'): return 25
    parts = tuple(map(int,v.split('.')))
    return 8 if parts < (1,17) else 17 if parts < (1,20,5) else 21

def freeport():
    with socket.socket() as s: s.bind(('127.0.0.1',0)); return s.getsockname()[1]

def test(version, toolcache):
    folder = ROOT / 'test-servers' / version
    port, httpport = freeport(), freeport()
    plugins = folder / 'plugins'; plugins.mkdir(exist_ok=True)
    shutil.copy2(JAR, plugins / JAR.name)
    for name in ('test-world','test-world_nether','test-world_the_end','plugins/MusicReforged'):
        target=(folder/name).resolve()
        assert target.is_relative_to((ROOT/'test-servers').resolve())
        if target.exists(): shutil.rmtree(target)
    data = plugins / 'MusicReforged'; data.mkdir()
    with zipfile.ZipFile(JAR) as z: config=z.read('config.yml').decode()
    config=config.replace('serve: false','serve: true').replace('port: 8124',f'port: {httpport}')
    (data/'config.yml').write_text(config,encoding='utf-8')
    (folder/'eula.txt').write_text('eula=true\n')
    (folder/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\nallow-flight=true\nlevel-name=test-world\nlevel-type=flat\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-players=4\nallow-nether=false\nspawn-npcs=false\nspawn-monsters=false\nspawn-animals=false\ngenerate-structures=false\nenable-query=false\nenable-rcon=false\nnetwork-compression-threshold=-1\n')
    (folder/'bukkit.yml').write_text('settings:\n  allow-end: false\n  connection-throttle: 0\nspawn-limits:\n  monsters: 0\n  animals: 0\n  water-animals: 0\n  ambient: 0\n')
    (folder/'paper.yml').write_text('world-settings:\n  default:\n    keep-spawn-loaded: false\n    keep-spawn-loaded-range: 0\n')
    (folder/'config').mkdir(exist_ok=True)
    (folder/'config/paper-world-defaults.yml').write_text('spawn:\n  keep-spawn-loaded: false\n  keep-spawn-loaded-range: 0\n')
    java=pathlib.Path(toolcache)/f'jdk{runtime(version)}/bin/java.exe'
    cmd=[str(java),'-Xms128M','-Xmx768M','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-Dmusicreforged.test=true','-jar','server.jar','nogui']
    all_log=[]; result={'version':version,'status':'timeout','java':runtime(version),'checks':0,'playerChecks':0,'restartChecks':0,'http':False}
    env=dict(os.environ, MUSIC_TOOL_CACHE=str(pathlib.Path(toolcache).resolve()))
    def launch():
        process=subprocess.Popen(cmd,cwd=folder,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1)
        lines=queue.Queue()
        def read():
            for line in process.stdout: all_log.append(line); lines.put(line)
        thread=threading.Thread(target=read,daemon=True);thread.start()
        return process,lines,thread
    def send(process,text): process.stdin.write(text+'\n');process.stdin.flush()
    def stop(process,thread):
        if process.poll() is None:
            try: send(process,'stop'); process.wait(timeout=45)
            except (OSError,subprocess.TimeoutExpired): process.kill();process.wait()
        thread.join(timeout=5)
    p,lines,thread=launch();bot=None;botlog=None;started=time.monotonic();phase='boot'
    try:
        while time.monotonic()-started < 220:
            try: line=lines.get(timeout=.5)
            except queue.Empty:
                if p.poll() is not None: result['status']='boot-failed';break
                if bot is not None and bot.poll() is not None: result['status']='client-failed';break
                continue
            if 'Done (' in line and phase=='boot': send(p,'music selftest'); phase='selftest'
            if 'SELFTEST PASS' in line:
                result['checks']=int(re.search(r'checks=(\d+)',line)[1])
                # Check actual hosted bytes, SHA-1, and a path traversal rejection.
                data=urllib.request.urlopen(f'http://127.0.0.1:{httpport}/pack.zip',timeout=10).read()
                assert data==(plugins/'MusicReforged/pack.zip').read_bytes()
                try: urllib.request.urlopen(f'http://127.0.0.1:{httpport}/pack.zip/../config.yml',timeout=10); raise AssertionError('Path leak')
                except urllib.error.HTTPError as e: assert e.code==404
                result['http']=True
                botlog=open(folder/'client-test.log','w',encoding='utf-8')
                if version in ('26.2','26.3'): send(p,'music protocolinfo');phase='protocol'
                else:
                    bot=subprocess.Popen(['node',str(ROOT/'tools/test_client.cjs'),str(port),version],stdout=botlog,stderr=subprocess.STDOUT,env=env);phase='player'
            if 'PROTOCOL INFO READY' in line:
                bot=subprocess.Popen([sys.executable,str(ROOT/'tools/raw_client.py'),str(port),version,str(plugins/'MusicReforged/protocol.json')],stdout=botlog,stderr=subprocess.STDOUT,env=env);phase='player'
            if phase=='player' and 'Hash014Test' in line and 'logged in with entity id' in line: send(p,'music playercheck Hash014Test');phase='checking-player'
            if 'PLAYERTEST PASS' in line:
                result['playerChecks']=int(re.search(r'checks=(\d+)',line)[1]); result['status']='first-boot-passed';break
            if 'CHECK FAIL' in line or 'Ownership bypass' in line or 'Initialisation impossible' in line or 'Commande Music echouee' in line or 'Could not pass event' in line:
                result['status']='test-failed';break
    except Exception as e: result['status']='runner-failed';result['error']=repr(e)
    finally:
        stop(p,thread)
        if bot is not None and bot.poll() is None:bot.terminate();bot.wait(timeout=5)
        if botlog is not None:botlog.close()
    if result['status']=='first-boot-passed':
        client=(folder/'client-test.log').read_text(encoding='utf-8')
        result['soundPackets']=client.count('CLIENT SOUND');result['stopPackets']=client.count('CLIENT STOP');result['packPackets']=client.count('CLIENT PACK')
        if not result['soundPackets'] or not result['packPackets']: result['status']='packet-check-failed'
        else:
            p,lines,thread=launch();started=time.monotonic()
            try:
                while time.monotonic()-started<120:
                    try:line=lines.get(timeout=.5)
                    except queue.Empty:
                        if p.poll() is not None:result['status']='restart-failed';break
                        continue
                    if 'Done (' in line:send(p,'music persistcheck')
                    if 'PERSISTENCE PASS' in line:result['restartChecks']=int(re.search(r'checks=(\d+)',line)[1]);result['status']='passed';break
                    if 'CHECK FAIL' in line or 'Initialisation impossible' in line:result['status']='restart-test-failed';break
                else:result['status']='restart-timeout'
            finally:stop(p,thread)
    (folder/'reforged-test.log').write_text(''.join(all_log),encoding='utf-8')
    result['seconds']=round(time.monotonic()-started,1)
    result['failures']=[line.strip() for line in all_log if any(s in line for s in ['CHECK FAIL','Initialisation impossible','Commande Music echouee','Could not pass event','IncompatibleClassChangeError','NoSuchMethodError'])]
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('versions',nargs='*');parser.add_argument('--all',action='store_true');parser.add_argument('--workers',type=int,default=3);parser.add_argument('--tool-cache',default=str(ROOT/'.tools'));a=parser.parse_args()
    versions=a.versions or [p.name for p in (ROOT/'test-servers').iterdir() if p.is_dir() and re.fullmatch(r'(?:1\.\d+(?:\.\d+)?|26\.\d+(?:\.\d+)?)',p.name)]
    report={'date':datetime.now(timezone.utc).isoformat(),'jarSha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'results':[]}
    with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as ex:
        fs={ex.submit(test,v,a.tool_cache):v for v in versions}
        for f in concurrent.futures.as_completed(fs):
            try:r=f.result()
            except Exception as e:r={'version':fs[f],'status':'runner-failed','error':repr(e)}
            report['results'].append(r);print(json.dumps(r),flush=True)
            (ROOT/'docs').mkdir(exist_ok=True);(ROOT/'docs/matrix-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    if any(r['status']!='passed' for r in report['results']):sys.exit(1)
