"""Fetch official build/test tools with vendor checksums; no system installation."""
import argparse, concurrent.futures, hashlib, io, json, pathlib, urllib.request, zipfile
ROOT = pathlib.Path(__file__).resolve().parent.parent / '.tools'
HEADERS = {'User-Agent':'MusicReforged/1.0 (https://github.com/ZombieStriker/Music)'}
def get(url): return urllib.request.urlopen(urllib.request.Request(url,headers=HEADERS),timeout=180).read()
def jdk(v):
    target = ROOT / f'jdk{v}'
    if (target/'bin/java.exe').exists(): return
    packages=json.loads(get(f'https://api.adoptium.net/v3/assets/latest/{v}/hotspot?architecture=x64&image_type=jdk&os=windows'))
    p=packages[0]['binary']['package'];data=get(p['link'])
    if hashlib.sha256(data).hexdigest()!=p['checksum']:raise RuntimeError('JDK checksum mismatch')
    staging=ROOT/f'extract-jdk{v}';zipfile.ZipFile(io.BytesIO(data)).extractall(staging)
    next(staging.iterdir()).rename(target);staging.rmdir();print('JDK',v,'ready',flush=True)
def maven():
    if (ROOT/'apache-maven-3.9.11/bin/mvn.cmd').exists():return
    url='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip';data=get(url)
    if hashlib.sha512(data).hexdigest()!=get(url+'.sha512').decode().split()[0]:raise RuntimeError('Maven checksum mismatch')
    zipfile.ZipFile(io.BytesIO(data)).extractall(ROOT);print('Maven ready',flush=True)
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--build-only',action='store_true');args=a.parse_args();ROOT.mkdir(exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:
        jobs=[ex.submit(jdk,v) for v in ([25] if args.build_only else [8,16,17,21,25])]+[ex.submit(maven)]
        for job in jobs:job.result()
    protocol=ROOT/'protocol';protocol.mkdir(exist_ok=True)
    (protocol/'package.json').write_text(json.dumps({'private':True,'dependencies':{'minecraft-protocol':'1.68.0','minecraft-data':'3.117.0'}},indent=2))
    print('For protocol tests: npm install --prefix .tools/protocol',flush=True)
